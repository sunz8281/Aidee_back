package com.aidee.backend.agent;

import com.aidee.backend.agent.dto.AgentRequest;
import com.aidee.backend.agent.dto.MessageDto;
import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.config.SecurityConfig;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AgentController.class)
@Import(SecurityConfig.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AgentService agentService;

    @Test
    void 채팅_SSE_성공() throws Exception {
        when(agentService.chat(eq("project-1"), isNull(), any()))
                .thenReturn(new SseEmitter());

        mockMvc.perform(post("/projects/project-1/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AgentRequest("안녕하세요", List.of()))))
                .andExpect(status().isOk());
    }

    @Test
    void 회의ID_포함_채팅_SSE_성공() throws Exception {
        when(agentService.chat(eq("project-1"), eq("meeting-1"), any()))
                .thenReturn(new SseEmitter());

        mockMvc.perform(post("/projects/project-1/agent")
                        .param("meetingId", "meeting-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AgentRequest("회의 요약해줘", List.of(
                                        new MessageDto("user", "안녕"),
                                        new MessageDto("assistant", "안녕하세요!")
                                )))))
                .andExpect(status().isOk());
    }

    @Test
    void 존재하지_않는_프로젝트로_채팅시_404_반환() throws Exception {
        when(agentService.chat(eq("not-exist"), isNull(), any()))
                .thenThrow(new ResourceNotFoundException("프로젝트를 찾을 수 없습니다"));

        mockMvc.perform(post("/projects/not-exist/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AgentRequest("안녕", List.of()))))
                .andExpect(status().isNotFound());
    }
}
