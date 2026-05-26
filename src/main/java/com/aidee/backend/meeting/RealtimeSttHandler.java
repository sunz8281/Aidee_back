package com.aidee.backend.meeting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프론트엔드 ↔ 백엔드 ↔ CLOVA Speech 간 실시간 STT 프록시.
 *
 * 연결 URL: ws://host/meetings/{meetingId}/stt/stream
 * - 프론트: 마이크 오디오 바이너리 청크 전송
 * - 백엔드: CLOVA WebSocket에 포워딩
 * - 백엔드: CLOVA 전사 결과를 JSON으로 프론트에 전달
 *   {"type":"partial","text":"..."}  — 중간 결과
 *   {"type":"final","text":"..."}    — 확정 결과
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeSttHandler extends AbstractWebSocketHandler {

    @Value("${clova.speech.invoke-url}")
    private String invokeUrl;

    @Value("${clova.speech.secret}")
    private String secret;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // 프론트 세션 ID → CLOVA WebSocket
    private final Map<String, WebSocket> clovaConnections = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String wsUrl = invokeUrl.strip().replaceAll("/+$", "")
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                + "/recognizer/streaming";

        log.info("[RealtimeSTT] 연결 시작 sessionId={}, clovaUrl={}", session.getId(), wsUrl);

        WebSocket clovaWs = httpClient.newWebSocketBuilder()
                .header("X-CLOVASPEECH-API-KEY", secret.strip())
                .buildAsync(URI.create(wsUrl), new ClovaListener(session))
                .join();

        // 연결 직후 설정 메시지 전송 (CLOVA 프로토콜)
        String config = "{\"config\":{\"encoding\":\"LINEAR16\",\"sampleRate\":16000,\"channels\":1}}";
        clovaWs.sendText(config, true);

        clovaConnections.put(session.getId(), clovaWs);
        log.info("[RealtimeSTT] CLOVA WebSocket 연결 완료 sessionId={}", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        WebSocket clovaWs = clovaConnections.get(session.getId());
        if (clovaWs == null) return;
        ByteBuffer payload = message.getPayload();
        clovaWs.sendBinary(payload, true)
                .exceptionally(e -> {
                    log.warn("[RealtimeSTT] CLOVA 전송 실패: {}", e.getMessage());
                    return null;
                });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WebSocket clovaWs = clovaConnections.remove(session.getId());
        if (clovaWs != null) {
            clovaWs.sendClose(WebSocket.NORMAL_CLOSURE, "client disconnected");
        }
        log.info("[RealtimeSTT] 연결 종료 sessionId={} status={}", session.getId(), status);
    }

    private static class ClovaListener implements WebSocket.Listener {
        private final WebSocketSession clientSession;
        private final StringBuilder buf = new StringBuilder();

        ClovaListener(WebSocketSession clientSession) {
            this.clientSession = clientSession;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String msg = buf.toString();
                buf.setLength(0);
                try {
                    // CLOVA 응답을 그대로 클라이언트에 전달
                    if (clientSession.isOpen()) {
                        clientSession.sendMessage(new TextMessage(msg));
                    }
                } catch (Exception e) {
                    log.warn("[RealtimeSTT] 클라이언트 전송 실패: {}", e.getMessage());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.error("[RealtimeSTT] CLOVA 오류: {}", error.getMessage());
            try {
                if (clientSession.isOpen()) {
                    clientSession.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"STT 오류가 발생했습니다\"}"));
                }
            } catch (Exception ignored) {}
        }
    }
}
