package com.aidee.backend.schedule;

import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.meeting.Meeting;
import com.aidee.backend.meeting.MeetingRepository;
import com.aidee.backend.project.Project;
import com.aidee.backend.project.ProjectRepository;
import com.aidee.backend.schedule.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ProjectRepository projectRepository;
    private final MeetingRepository meetingRepository;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedules(String projectId, int year, int month) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId);
        }
        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end = start.plusMonths(1).minusSeconds(1);
        return scheduleRepository.findByProjectIdAndStartTimeBetween(projectId, start, end)
                .stream().map(ScheduleResponse::from).toList();
    }

    @Transactional
    public CreateScheduleResponse createSchedule(String projectId, CreateScheduleRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId));

        Meeting meeting = null;
        if (request.sourceMeetingId() != null) {
            meeting = meetingRepository.findById(request.sourceMeetingId())
                    .orElseThrow(() -> new ResourceNotFoundException("회의를 찾을 수 없습니다: " + request.sourceMeetingId()));
        }

        Schedule schedule = Schedule.create(
                project, meeting,
                request.title(), request.startTime(), request.endTime(),
                request.allDay(), request.sourceType()
        );
        scheduleRepository.save(schedule);
        return CreateScheduleResponse.from(schedule);
    }

    @Transactional
    public UpdateScheduleResponse updateSchedule(String scheduleId, UpdateScheduleRequest request) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("일정을 찾을 수 없습니다: " + scheduleId));
        schedule.update(request.title(), request.startTime(), request.endTime(), request.allDay());
        return UpdateScheduleResponse.from(schedule);
    }

    @Transactional
    public void deleteSchedule(String scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new ResourceNotFoundException("일정을 찾을 수 없습니다: " + scheduleId);
        }
        scheduleRepository.deleteById(scheduleId);
    }
}
