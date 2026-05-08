package com.aidee.backend.meeting;

import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.meeting.dto.*;
import com.aidee.backend.project.Project;
import com.aidee.backend.project.ProjectRepository;
import com.aidee.backend.schedule.ScheduleRepository;
import com.aidee.backend.script.ScriptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private MeetingRepository meetingRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ScriptRepository scriptRepository;
    @Mock
    private SttService sttService;
    @Mock
    private LlmService llmService;

    @InjectMocks
    private MeetingService meetingService;

    private Project createProject() {
        return Project.create();
    }

    @Test
    void 회의_목록_조회_성공() {
        Project project = createProject();
        Meeting meeting = Meeting.create(project, "테스트 회의", LocalDateTime.now());
        when(projectRepository.existsById(project.getId())).thenReturn(true);
        when(meetingRepository.findByProjectId(project.getId())).thenReturn(List.of(meeting));

        List<MeetingSummaryResponse> result = meetingService.getMeetings(project.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("테스트 회의");
    }

    @Test
    void 존재하지_않는_프로젝트의_회의_목록_조회시_404_반환() {
        when(projectRepository.existsById("not-exist")).thenReturn(false);

        assertThatThrownBy(() -> meetingService.getMeetings("not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 회의_생성_성공() {
        Project project = createProject();
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMeetingRequest request = new CreateMeetingRequest("회의 제목", LocalDateTime.now());
        MeetingCreateResponse response = meetingService.createMeeting(project.getId(), request);

        assertThat(response.title()).isEqualTo("회의 제목");
        assertThat(response.id()).isNotNull();
    }

    @Test
    void 회의_생성시_제목_미입력이면_날짜_기반_자동_생성() {
        Project project = createProject();
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));

        MeetingCreateResponse response = meetingService.createMeeting(project.getId(), null);

        assertThat(response.title()).contains("회의");
    }

    @Test
    void 회의_상세_조회_성공() {
        Project project = createProject();
        Meeting meeting = Meeting.create(project, "테스트 회의", LocalDateTime.now());
        when(meetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(scriptRepository.findByMeetingIdOrderByStartTimeAsc(meeting.getId())).thenReturn(List.of());
        when(scheduleRepository.findByMeetingId(meeting.getId())).thenReturn(List.of());

        MeetingDetailResponse result = meetingService.getMeeting(meeting.getId());

        assertThat(result.id()).isEqualTo(meeting.getId());
        assertThat(result.title()).isEqualTo("테스트 회의");
        assertThat(result.status()).isEqualTo("pending");
    }

    @Test
    void 존재하지_않는_회의_조회시_404_반환() {
        when(meetingRepository.findById("not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.getMeeting("not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 회의_수정_성공() {
        Project project = createProject();
        Meeting meeting = Meeting.create(project, "원래 제목", LocalDateTime.now());
        when(meetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));

        UpdateMeetingRequest request = new UpdateMeetingRequest("수정된 제목", null);
        meetingService.updateMeeting(meeting.getId(), request);

        assertThat(meeting.getTitle()).isEqualTo("수정된 제목");
    }

    @Test
    void 회의_삭제_성공() {
        when(meetingRepository.existsById("meeting-id")).thenReturn(true);

        meetingService.deleteMeeting("meeting-id");

        verify(meetingRepository).deleteById("meeting-id");
    }

    @Test
    void 존재하지_않는_회의_삭제시_404_반환() {
        when(meetingRepository.existsById("not-exist")).thenReturn(false);

        assertThatThrownBy(() -> meetingService.deleteMeeting("not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 메모_수정_성공() {
        Project project = createProject();
        Meeting meeting = Meeting.create(project, "테스트 회의", LocalDateTime.now());
        when(meetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));

        meetingService.updateMemo(meeting.getId(), "새 메모");

        assertThat(meeting.getMemo()).isEqualTo("새 메모");
    }
}
