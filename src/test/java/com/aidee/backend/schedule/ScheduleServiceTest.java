package com.aidee.backend.schedule;

import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.meeting.MeetingRepository;
import com.aidee.backend.project.Project;
import com.aidee.backend.project.ProjectRepository;
import com.aidee.backend.schedule.dto.*;
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
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private MeetingRepository meetingRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    @Test
    void 일정_목록_조회_성공() {
        Project project = Project.create();
        Schedule schedule = Schedule.create(project, null, "팀 미팅",
                LocalDateTime.of(2025, 5, 10, 10, 0),
                LocalDateTime.of(2025, 5, 10, 11, 0),
                false, "user");
        when(projectRepository.existsById(project.getId())).thenReturn(true);
        when(scheduleRepository.findByProjectIdAndPeriodOverlaps(any(), any(), any()))
                .thenReturn(List.of(schedule));

        List<ScheduleResponse> result = scheduleService.getSchedules(project.getId(), 2025, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("팀 미팅");
    }

    @Test
    void 존재하지_않는_프로젝트_일정_조회시_404_반환() {
        when(projectRepository.existsById("not-exist")).thenReturn(false);

        assertThatThrownBy(() -> scheduleService.getSchedules("not-exist", 2025, 5))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 일정_생성_성공() {
        Project project = Project.create();
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateScheduleRequest request = new CreateScheduleRequest(
                "새 일정",
                LocalDateTime.of(2025, 5, 10, 10, 0),
                LocalDateTime.of(2025, 5, 10, 11, 0),
                false, "user", null
        );
        CreateScheduleResponse response = scheduleService.createSchedule(project.getId(), request);

        assertThat(response.title()).isEqualTo("새 일정");
        assertThat(response.id()).isNotNull();
    }

    @Test
    void 일정_수정_성공() {
        Project project = Project.create();
        Schedule schedule = Schedule.create(project, null, "원래 일정",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1), false, "user");
        when(scheduleRepository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest("수정된 일정", null, null, null);
        UpdateScheduleResponse response = scheduleService.updateSchedule(schedule.getId(), request);

        assertThat(response.title()).isEqualTo("수정된 일정");
    }

    @Test
    void 존재하지_않는_일정_수정시_404_반환() {
        when(scheduleRepository.findById("not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.updateSchedule("not-exist",
                new UpdateScheduleRequest("제목", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 일정_삭제_성공() {
        when(scheduleRepository.existsById("schedule-id")).thenReturn(true);

        scheduleService.deleteSchedule("schedule-id");

        verify(scheduleRepository).deleteById("schedule-id");
    }

    @Test
    void 존재하지_않는_일정_삭제시_404_반환() {
        when(scheduleRepository.existsById("not-exist")).thenReturn(false);

        assertThatThrownBy(() -> scheduleService.deleteSchedule("not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
