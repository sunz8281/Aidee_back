package com.aidee.backend.meeting;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 실시간 STT 세션 중 수신된 final 전사 결과를 meetingId 기준으로 메모리에 보관한다.
 * 세션 종료(WebSocket 연결 끊김) 시 해당 meetingId 버퍼를 제거한다.
 */
@Component
public class LiveTranscriptStore {

    // meetingId → 누적 final transcript 청크 목록
    private final Map<String, List<String>> buffer = new ConcurrentHashMap<>();
    // meetingId → 현재 열린 WebSocket 세션 ID (연결 추적용)
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    public void startSession(String meetingId, String sessionId) {
        buffer.put(meetingId, Collections.synchronizedList(new ArrayList<>()));
        activeSessions.put(meetingId, sessionId);
    }

    public void addFinalChunk(String meetingId, String text) {
        List<String> chunks = buffer.get(meetingId);
        if (chunks != null) {
            chunks.add(text);
        }
    }

    public void endSession(String meetingId) {
        buffer.remove(meetingId);
        activeSessions.remove(meetingId);
    }

    /**
     * 현재까지 누적된 transcript를 하나의 문자열로 반환한다.
     * 세션이 없으면 null을 반환한다.
     */
    public String getTranscript(String meetingId) {
        List<String> chunks = buffer.get(meetingId);
        if (chunks == null) return null;
        synchronized (chunks) {
            return String.join(" ", chunks);
        }
    }

    public boolean isLive(String meetingId) {
        return buffer.containsKey(meetingId);
    }
}
