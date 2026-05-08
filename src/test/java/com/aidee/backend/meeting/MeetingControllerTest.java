package com.aidee.backend.meeting;

import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.config.SecurityConfig;
import com.aidee.backend.meeting.dto.*;
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

@WebMvcTest(controllers = MeetingController.class)
@Import(SecurityConfig.class)
class MeetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MeetingService meetingService;

    @Test
    void 회의_목록_조회_성공() throws Exception {
        when(meetingService.getMeetings("project-id")).thenReturn(
                List.of(new MeetingSummaryResponse("m-1", "테스트 회의", "pending",
                        LocalDateTime.now(), LocalDateTime.now()))
        );

        mockMvc.perform(get("/projects/project-id/meetings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("m-1"))
                .andExpect(jsonPath("$.items[0].title").value("테스트 회의"));
    }

    @Test
    void 회의_생성_성공() throws Exception {
        MeetingCreateResponse response = new MeetingCreateResponse(
                "m-1", "새 회의", LocalDateTime.now(), LocalDateTime.now()
        );
        when(meetingService.createMeeting(eq("project-id"), any())).thenReturn(response);

        mockMvc.perform(post("/projects/project-id/meetings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMeetingRequest("새 회의", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("m-1"));
    }

    @Test
    void 회의_상세_조회_성공() throws Exception {
        MeetingDetailResponse response = new MeetingDetailResponse(
                "m-1", "테스트 회의", LocalDateTime.now(), "pending",
                null, null, List.of(), List.of(), null, LocalDateTime.now()
        );
        when(meetingService.getMeeting("m-1")).thenReturn(response);

        mockMvc.perform(get("/meetings/m-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("m-1"))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void 존재하지_않는_회의_조회시_404_반환() throws Exception {
        when(meetingService.getMeeting("not-exist"))
                .thenThrow(new ResourceNotFoundException("회의를 찾을 수 없습니다"));

        mockMvc.perform(get("/meetings/not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 회의_수정_성공() throws Exception {
        doNothing().when(meetingService).updateMeeting(eq("m-1"), any(UpdateMeetingRequest.class));

        mockMvc.perform(patch("/meetings/m-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMeetingRequest("수정된 제목", null))))
                .andExpect(status().isNoContent());
    }

    @Test
    void 회의_삭제_성공() throws Exception {
        doNothing().when(meetingService).deleteMeeting("m-1");

        mockMvc.perform(delete("/meetings/m-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 메모_조회_성공() throws Exception {
        when(meetingService.getMemos("project-id")).thenReturn(
                List.of(new MemoItemResponse("m-1", "테스트 회의", "메모 내용"))
        );

        mockMvc.perform(get("/projects/project-id/memos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].meetingId").value("m-1"))
                .andExpect(jsonPath("$.items[0].memo").value("메모 내용"));
    }

    @Test
    void 메모_수정_성공() throws Exception {
        doNothing().when(meetingService).updateMemo(eq("m-1"), eq("새 메모"));

        mockMvc.perform(patch("/meetings/m-1/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMemoRequest("새 메모"))))
                .andExpect(status().isNoContent());
    }
}
