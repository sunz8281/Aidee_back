package com.aidee.backend.project;

import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.config.SecurityConfig;
import com.aidee.backend.project.dto.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProjectController.class)
@Import(SecurityConfig.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProjectService projectService;

    @Test
    void 프로젝트_목록_조회_성공() throws Exception {
        when(projectService.getProjects()).thenReturn(
                List.of(new ProjectSummaryResponse("id-1", "새 프로젝트", LocalDateTime.now()))
        );

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].id").value("id-1"))
                .andExpect(jsonPath("$.items[0].name").value("새 프로젝트"));
    }

    @Test
    void 프로젝트_생성_성공() throws Exception {
        when(projectService.createProject()).thenReturn(new ProjectCreateResponse("new-id"));

        mockMvc.perform(post("/projects"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new-id"));
    }

    @Test
    void 프로젝트_단건_조회_성공() throws Exception {
        ProjectDetailResponse response = new ProjectDetailResponse(
                "id-1", "새 프로젝트", List.of(), List.of(), LocalDateTime.now()
        );
        when(projectService.getProject("id-1")).thenReturn(response);

        mockMvc.perform(get("/projects/id-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("id-1"))
                .andExpect(jsonPath("$.name").value("새 프로젝트"));
    }

    @Test
    void 존재하지_않는_프로젝트_조회시_404_반환() throws Exception {
        when(projectService.getProject("not-exist"))
                .thenThrow(new ResourceNotFoundException("프로젝트를 찾을 수 없습니다"));

        mockMvc.perform(get("/projects/not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 프로젝트_타이틀_수정_성공() throws Exception {
        doNothing().when(projectService).updateTitle(eq("id-1"), eq("수정된 이름"));

        mockMvc.perform(patch("/projects/id-1/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProjectTitleRequest("수정된 이름"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void 프로젝트_삭제_성공() throws Exception {
        doNothing().when(projectService).deleteProject("id-1");

        mockMvc.perform(delete("/projects/id-1"))
                .andExpect(status().isNoContent());
    }
}
