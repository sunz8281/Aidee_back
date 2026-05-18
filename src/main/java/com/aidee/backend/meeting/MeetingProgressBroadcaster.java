package com.aidee.backend.meeting;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class MeetingProgressBroadcaster {

    private record StoredEvent(String event, String data) {}

    private final ConcurrentHashMap<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<StoredEvent>> eventLog = new ConcurrentHashMap<>();

    public void register(String meetingId, SseEmitter emitter) {
        // 과거 이벤트 먼저 replay
        List<StoredEvent> history = eventLog.get(meetingId);
        if (history != null) {
            for (StoredEvent e : history) {
                try {
                    emitter.send(SseEmitter.event().name(e.event()).data(e.data()));
                } catch (Exception ignored) {
                    return; // 클라이언트 이미 끊김
                }
            }
        }
        emitters.computeIfAbsent(meetingId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> unregister(meetingId, emitter));
        emitter.onError(e -> unregister(meetingId, emitter));
        emitter.onTimeout(() -> unregister(meetingId, emitter));
    }

    private void unregister(String meetingId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(meetingId);
        if (list != null) list.remove(emitter);
    }

    public void send(String meetingId, String event, String data) {
        // 이벤트 로그에 저장
        eventLog.computeIfAbsent(meetingId, k -> new CopyOnWriteArrayList<>())
                .add(new StoredEvent(event, data));
        // 현재 구독자에게 전송
        List<SseEmitter> list = emitters.get(meetingId);
        if (list == null || list.isEmpty()) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception ignored) {}
        }
    }

    public void complete(String meetingId) {
        eventLog.remove(meetingId);
        List<SseEmitter> list = emitters.remove(meetingId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    public void completeWithError(String meetingId, String message) {
        eventLog.remove(meetingId);
        List<SseEmitter> list = emitters.remove(meetingId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"message\":\"" + message + "\"}"));
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }
}
