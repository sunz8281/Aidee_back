package com.aidee.backend.schedule;

import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.config.SecurityConfig;
import com.aidee.backend.schedule.dto.*;
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

@WebMvcTest(controllers = ScheduleController.class)
@Import(SecurityConfig.class)
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ScheduleService scheduleService;

    @Test
    void 일정_목록_조회_성공() throws Exception {
        ScheduleResponse response = new ScheduleResponse(
                "s-1", "팀 미팅",
                LocalDateTime.of(2025, 5, 10, 10, 0),
                LocalDateTime.of(2025, 5, 10, 11, 0),
                false, "user", null, LocalDateTime.now()
        );
        when(scheduleService.getSchedules("project-id", 2025, 5)).thenReturn(List.of(response));

        mockMvc.perform(get("/projects/project-id/schedules")
                        .param("year", "2025")
                        .param("month", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("s-1"))
                .andExpect(jsonPath("$.items[0].title").value("팀 미팅"));
    }

    @Test
    void 일정_생성_성공() throws Exception {
        CreateScheduleResponse response = new CreateScheduleResponse(
                "s-1", "새 일정",
                LocalDateTime.of(2025, 5, 10, 10, 0),
                LocalDateTime.of(2025, 5, 10, 11, 0),
                LocalDateTime.now()
        );
        when(scheduleService.createSchedule(eq("project-id"), any())).thenReturn(response);

        CreateScheduleRequest request = new CreateScheduleRequest(
                "새 일정",
                LocalDateTime.of(2025, 5, 10, 10, 0),
                LocalDateTime.of(2025, 5, 10, 11, 0),
                false, "user", null
        );

        mockMvc.perform(post("/projects/project-id/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("s-1"));
    }

    @Test
    void 일정_수정_성공() throws Exception {
        UpdateScheduleResponse response = new UpdateScheduleResponse("s-1", "수정된 일정");
        when(scheduleService.updateSchedule(eq("s-1"), any())).thenReturn(response);

        UpdateScheduleRequest request = new UpdateScheduleRequest("수정된 일정", null, null, null);

        mockMvc.perform(put("/schedules/s-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s-1"))
                .andExpect(jsonPath("$.title").value("수정된 일정"));
    }

    @Test
    void 일정_삭제_성공() throws Exception {
        doNothing().when(scheduleService).deleteSchedule("s-1");

        mockMvc.perform(delete("/schedules/s-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 존재하지_않는_일정_삭제시_404_반환() throws Exception {
        doThrow(new ResourceNotFoundException("일정을 찾을 수 없습니다"))
                .when(scheduleService).deleteSchedule("not-exist");

        mockMvc.perform(delete("/schedules/not-exist"))
                .andExpect(status().isNotFound());
    }
}
