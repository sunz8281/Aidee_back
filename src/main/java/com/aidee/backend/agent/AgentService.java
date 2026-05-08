package com.aidee.backend.agent;

import com.aidee.backend.agent.dto.AgentRequest;
import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.meeting.MeetingRepository;
import com.aidee.backend.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final ProjectRepository projectRepository;
    private final MeetingRepository meetingRepository;
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
                // 스텁: 모의 응답 스트리밍
                String response = buildMockResponse(request.message());
                String[] words = response.split(" ");
                for (String word : words) {
                    emitter.send(SseEmitter.event()
                            .name("delta")
                            .data(Map.of("text", word + " ")));
                    Thread.sleep(80);
                }
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(Map.of()));
                emitter.complete();
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String buildMockResponse(String message) {
        return "안녕하세요! 질문을 잘 받았습니다. "
                + "\"" + message + "\"에 대해 도움을 드리겠습니다. "
                + "현재 AI 에이전트 기능은 준비 중입니다. "
                + "곧 더 다양한 기능을 제공할 예정입니다.";
    }
}
