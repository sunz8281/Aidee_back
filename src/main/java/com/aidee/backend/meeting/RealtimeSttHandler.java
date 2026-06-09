package com.aidee.backend.meeting;

import com.google.protobuf.ByteString;
import com.nbp.cdncp.nest.grpc.proto.v1.*;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프론트엔드(WebSocket) ↔ 백엔드 ↔ CLOVA Speech(gRPC) 실시간 STT 프록시.
 *
 * 연결: ws://host/meetings/{meetingId}/stt/stream
 * 프론트 → 백엔드: PCM 16bit 16kHz 1ch 바이너리 청크
 * 백엔드 → 프론트: {"text":"...", "position":0} JSON
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeSttHandler extends AbstractWebSocketHandler {

    @Value("${clova.speech.realtime.grpc-url}")
    private String grpcUrl;

    @Value("${clova.speech.realtime.secret}")
    private String secret;

    private static final String CONFIG_JSON =
            "{\"transcription\":{\"language\":\"ko\"}," +
            "\"semanticEpd\":{\"skipEmptyText\":true,\"useWordEpd\":true,\"usePeriodEpd\":true}}";

    private final LiveTranscriptStore liveTranscriptStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 프론트 세션 ID → gRPC 요청 스트림
    private final Map<String, StreamObserver<NestRequest>> grpcStreams = new ConcurrentHashMap<>();
    // 프론트 세션 ID → gRPC 채널 (종료 시 shutdown)
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    // 프론트 세션 ID → meetingId (연결 해제 시 LiveTranscriptStore 정리용)
    private final Map<String, String> sessionMeetingIds = new ConcurrentHashMap<>();
    // meetingId → 현재 발화 그룹의 startTime(초) — 프론트에 내려주는 값
    private final Map<String, Integer> groupStartTimes = new ConcurrentHashMap<>();
    // meetingId → 직전 청크의 raw startTime(초) — 텀 계산용
    private final Map<String, Integer> lastRawStartTimes = new ConcurrentHashMap<>();

    private String extractMeetingId(WebSocketSession session) {
        // URI: /meetings/{meetingId}/stt/stream
        String path = session.getUri().getPath();
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if ("meetings".equals(segments[i])) return segments[i + 1];
        }
        return null;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String meetingId = extractMeetingId(session);
        if (meetingId != null) {
            sessionMeetingIds.put(session.getId(), meetingId);
            liveTranscriptStore.startSession(meetingId, session.getId());
            log.info("[RealtimeSTT] 라이브 세션 시작 meetingId={}", meetingId);
        }

        try {
            String host;
            int port;
            String[] parts = grpcUrl.strip().split(":");
            host = parts[0];
            port = parts.length > 1 ? Integer.parseInt(parts[1]) : 50051;

            ManagedChannel channel = NettyChannelBuilder.forAddress(host, port)
                    .useTransportSecurity()
                    .build();

            Metadata metadata = new Metadata();
            metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                    "Bearer " + secret.strip());

            NestServiceGrpc.NestServiceStub stub = NestServiceGrpc.newStub(channel)
                    .withInterceptors(io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(metadata));

            String meetingIdForCallback = sessionMeetingIds.get(session.getId());

            StreamObserver<NestRequest> requestStream = stub.recognize(new StreamObserver<>() {
                @Override
                public void onNext(NestResponse response) {
                    try {
                        if (!session.isOpen()) return;
                        String msg = parseContents(response.getContents(), meetingIdForCallback);
                        if (msg != null) session.sendMessage(new TextMessage(msg));
                    } catch (Exception e) {
                        log.warn("[RealtimeSTT] 클라이언트 전송 실패: {}", e.getMessage());
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.error("[RealtimeSTT] gRPC 오류: {}", t.getMessage(), t);
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage("{\"error\":\"STT 오류가 발생했습니다: " + t.getMessage() + "\"}"));
                            session.close(CloseStatus.SERVER_ERROR);
                        }
                    } catch (Exception ignored) {}
                }

                @Override
                public void onCompleted() {
                    log.info("[RealtimeSTT] gRPC 스트림 완료 sessionId={}", session.getId());
                }
            });

            // Config 전송
            requestStream.onNext(NestRequest.newBuilder()
                    .setType(RequestType.CONFIG)
                    .setConfig(NestConfig.newBuilder().setConfig(CONFIG_JSON).build())
                    .build());

            grpcStreams.put(session.getId(), requestStream);
            channels.put(session.getId(), channel);
            log.info("[RealtimeSTT] 연결 sessionId={} meetingId={} grpcUrl={}", session.getId(), meetingId, grpcUrl);
        } catch (Exception e) {
            log.error("[RealtimeSTT] 연결 초기화 실패 sessionId={}: {}", session.getId(), e.getMessage(), e);
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage("{\"error\":\"STT 초기화 실패: " + e.getMessage() + "\"}"));
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        StreamObserver<NestRequest> stream = grpcStreams.get(session.getId());
        if (stream == null) return;
        stream.onNext(NestRequest.newBuilder()
                .setType(RequestType.DATA)
                .setData(NestData.newBuilder()
                        .setChunk(ByteString.copyFrom(message.getPayload()))
                        .build())
                .build());
    }

    /**
     * CLOVA 실시간 응답을 프론트 포맷으로 정규화.
     * CLOVA: {"type":"partial","transcription":"텍스트","startTime":1000,"endTime":2000} (ms)
     * 프론트: {"type":"partial","text":"텍스트","startTime":1} (초)
     */
    private String parseContents(String contents, String meetingId) {
        try {
            JsonNode node = objectMapper.readTree(contents);
            JsonNode t = node.path("transcription");
            String text = t.path("text").asText("").strip();
            if (text.isBlank()) return null;
            int startTimeSec = t.path("startTimestamp").asInt(0) / 1000;
            boolean epFlag = t.path("epFlag").asBoolean(false);
            String type = epFlag ? "final" : "partial";

            // 직전 청크와의 텀이 1초 이상이면 새 그룹 시작, 미만이면 현재 그룹 startTime 유지
            int groupedStartTime;
            if (meetingId != null) {
                Integer lastRaw = lastRawStartTimes.get(meetingId);
                lastRawStartTimes.put(meetingId, startTimeSec);
                if (lastRaw == null || (startTimeSec - lastRaw) >= 2) {
                    groupStartTimes.put(meetingId, startTimeSec);
                    groupedStartTime = startTimeSec;
                } else {
                    groupedStartTime = groupStartTimes.getOrDefault(meetingId, startTimeSec);
                }
            } else {
                groupedStartTime = startTimeSec;
            }

            // final 결과만 누적 버퍼에 저장
            if (epFlag && meetingId != null) {
                liveTranscriptStore.addFinalChunk(meetingId, text);
            }

            return String.format(
                    "{\"type\":\"%s\",\"text\":\"%s\",\"startTime\":%d}",
                    type,
                    text.replace("\\", "\\\\").replace("\"", "\\\""),
                    groupedStartTime
            );
        } catch (Exception e) {
            log.warn("[RealtimeSTT] 응답 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        StreamObserver<NestRequest> stream = grpcStreams.remove(session.getId());
        if (stream != null) {
            try { stream.onCompleted(); } catch (Exception ignored) {}
        }
        ManagedChannel channel = channels.remove(session.getId());
        if (channel != null) {
            channel.shutdown();
        }
        String meetingId = sessionMeetingIds.remove(session.getId());
        if (meetingId != null) {
            liveTranscriptStore.endSession(meetingId);
            groupStartTimes.remove(meetingId);
            lastRawStartTimes.remove(meetingId);
            log.info("[RealtimeSTT] 라이브 세션 종료 meetingId={}", meetingId);
        }
        log.info("[RealtimeSTT] 연결 종료 sessionId={} status={}", session.getId(), status);
    }
}
