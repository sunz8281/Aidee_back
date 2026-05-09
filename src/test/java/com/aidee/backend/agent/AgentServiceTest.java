package com.aidee.backend.agent;

import com.aidee.backend.agent.dto.AgentRequest;
import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.meeting.MeetingRepository;
import com.aidee.backend.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private MeetingRepository meetingRepository;

    @InjectMocks
    private AgentService agentService;

    @Test
    void 프로젝트_기반_채팅_성공() {
        when(projectRepository.existsById("project-1")).thenReturn(true);

        SseEmitter emitter = agentService.chat("project-1", null,
                new AgentRequest("안녕하세요", List.of()));

        assertThat(emitter).isNotNull();
    }

    @Test
    void 회의_기반_채팅_성공() {
        when(projectRepository.existsById("project-1")).thenReturn(true);
        when(meetingRepository.existsById("meeting-1")).thenReturn(true);

        SseEmitter emitter = agentService.chat("project-1", "meeting-1",
                new AgentRequest("회의 요약해줘", List.of()));

        assertThat(emitter).isNotNull();
    }

    @Test
    void 존재하지_않는_프로젝트로_채팅시_404_반환() {
        when(projectRepository.existsById("not-exist")).thenReturn(false);

        assertThatThrownBy(() -> agentService.chat("not-exist", null,
                new AgentRequest("안녕", List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 존재하지_않는_회의로_채팅시_404_반환() {
        when(projectRepository.existsById("project-1")).thenReturn(true);
        when(meetingRepository.existsById("not-exist")).thenReturn(false);

        assertThatThrownBy(() -> agentService.chat("project-1", "not-exist",
                new AgentRequest("안녕", List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
