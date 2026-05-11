package com.aidee.backend.agent;

import com.aidee.backend.agent.dto.AgentRequest;
import com.aidee.backend.agent.dto.MessageDto;
import com.aidee.backend.ai.GeminiClient;
import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.common.VectorConverter;
import com.aidee.backend.embedding.EmbeddingService;
import com.aidee.backend.embedding.ScriptEmbedding;
import com.aidee.backend.embedding.ScriptEmbeddingRepository;
import com.aidee.backend.meeting.MeetingRepository;
import com.aidee.backend.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private static final int TOP_K = 5;

    private final ProjectRepository projectRepository;
    private final MeetingRepository meetingRepository;
    private final ScriptEmbeddingRepository scriptEmbeddingRepository;
    private final EmbeddingService embeddingService;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final VectorConverter vectorConverter;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SseEmitter chat(String projectId, String meetingId, AgentRequest request) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId);
        }
        if (meetingId != null && !meetingRepository.existsById(meetingId)) {
            throw new ResourceNotFoundException("회의를 찾을 수 없습니다: " + meetingId);
        }

        SseEmitter emitter = new SseEmitter(3 * 60 * 1000L);

        executor.submit(() -> {
            try {
                // 질문 임베딩 → 유사 스크립트 검색
                float[] queryEmbedding = embeddingService.embed(request.message());
                String queryVector = vectorConverter.convertToDatabaseColumn(queryEmbedding);

                List<ScriptEmbedding> relevant = meetingId != null
                        ? scriptEmbeddingRepository.findSimilarByMeeting(meetingId, queryVector, TOP_K)
                        : scriptEmbeddingRepository.findSimilarByProject(projectId, queryVector, TOP_K);

                String context = relevant.stream()
                        .map(e -> String.format("[회의: %s | 날짜: %s | %d초] %s",
                                e.getMeetingTitle(),
                                e.getMeetingAt().toLocalDate(),
                                e.getStartTime(),
                                e.getText()))
                        .collect(Collectors.joining("\n"));

                String requestBody = buildRequestBody(request, context);

                geminiClient.streamContent(GeminiClient.TEXT_MODEL, requestBody, text -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("delta")
                                .data("{\"text\":\"" + text.replace("\\", "\\\\")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n") + "\"}"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                emitter.send(SseEmitter.event().name("done").data("{}"));
                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String buildRequestBody(AgentRequest request, String context) throws Exception {
        List<Map<String, Object>> contents = new ArrayList<>();

        if (request.history() != null) {
            for (MessageDto msg : request.history()) {
                contents.add(Map.of(
                        "role", msg.role().equals("assistant") ? "model" : "user",
                        "parts", List.of(Map.of("text", msg.content()))
                ));
            }
        }

        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", request.message()))
        ));

        String systemPrompt = "당신은 Aidee 프로젝트 관리 AI 어시스턴트입니다. " +
                "회의 내용 요약, 일정 관리, 메모 작성을 도와줍니다. " +
                "한국어로 친절하게 답변해주세요.";

        if (!context.isBlank()) {
            systemPrompt += "\n\n[관련 회의 내용]\n" + context;
        }

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", contents
        );

        return objectMapper.writeValueAsString(body);
    }
}
