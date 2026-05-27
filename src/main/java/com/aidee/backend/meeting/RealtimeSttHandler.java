package com.aidee.backend.meeting;

import com.google.protobuf.ByteString;
import com.nbp.cdncp.nest.grpc.proto.v1.*;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
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
public class RealtimeSttHandler extends AbstractWebSocketHandler {

    @Value("${clova.speech.realtime.grpc-url}")
    private String grpcUrl;

    @Value("${clova.speech.realtime.secret}")
    private String secret;

    private static final String CONFIG_JSON =
            "{\"transcription\":{\"language\":\"ko\"}," +
            "\"semanticEpd\":{\"skipEmptyText\":true,\"useWordEpd\":true,\"usePeriodEpd\":true}}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 프론트 세션 ID → gRPC 요청 스트림
    private final Map<String, StreamObserver<NestRequest>> grpcStreams = new ConcurrentHashMap<>();
    // 프론트 세션 ID → gRPC 채널 (종료 시 shutdown)
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
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

        StreamObserver<NestRequest> requestStream = stub.recognize(new StreamObserver<>() {
            @Override
            public void onNext(NestResponse response) {
                try {
                    if (!session.isOpen()) return;
                    session.sendMessage(new TextMessage(parseContents(response.getContents())));
                } catch (Exception e) {
                    log.warn("[RealtimeSTT] 클라이언트 전송 실패: {}", e.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("[RealtimeSTT] gRPC 오류: {}", t.getMessage());
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage("{\"error\":\"STT 오류가 발생했습니다\"}"));
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
        log.info("[RealtimeSTT] 연결 sessionId={}", session.getId());
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
    private String parseContents(String contents) {
        try {
            JsonNode node = objectMapper.readTree(contents);
            String type = node.path("type").asText("partial");
            String text = node.path("transcription").asText("");
            int startTimeSec = node.path("startTime").asInt(0) / 1000;
            return String.format(
                    "{\"type\":\"%s\",\"text\":\"%s\",\"startTime\":%d}",
                    type,
                    text.replace("\\", "\\\\").replace("\"", "\\\""),
                    startTimeSec
            );
        } catch (Exception e) {
            log.warn("[RealtimeSTT] 응답 파싱 실패, 원본 전송: {}", e.getMessage());
            return contents;
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
        log.info("[RealtimeSTT] 연결 종료 sessionId={} status={}", session.getId(), status);
    }
}
