package com.aidee.backend.project;

import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.meeting.MeetingRepository;
import com.aidee.backend.meeting.dto.MeetingSummaryResponse;
import com.aidee.backend.project.dto.ProjectCreateResponse;
import com.aidee.backend.project.dto.ProjectDetailResponse;
import com.aidee.backend.project.dto.ProjectSummaryResponse;
import com.aidee.backend.schedule.ScheduleRepository;
import com.aidee.backend.schedule.dto.ScheduleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final MeetingRepository meetingRepository;
    private final ScheduleRepository scheduleRepository;

    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> getProjects() {
        return projectRepository.findAll().stream()
                .map(project -> ProjectSummaryResponse.of(
                        project,
                        meetingRepository.countByProjectId(project.getId()),
                        scheduleRepository.countByProjectId(project.getId())
                ))
                .toList();
    }

    @Transactional
    public ProjectCreateResponse createProject() {
        Project project = Project.create();
        projectRepository.save(project);
        return new ProjectCreateResponse(project.getId());
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProject(String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId));

        List<MeetingSummaryResponse> meetings = meetingRepository
                .findTop5ByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(MeetingSummaryResponse::from).toList();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        List<ScheduleResponse> schedules = scheduleRepository
                .findByProjectIdAndPeriodOverlaps(projectId, startOfMonth, endOfMonth)
                .stream().map(ScheduleResponse::from).toList();

        return ProjectDetailResponse.of(project, meetings, schedules);
    }

    @Transactional
    public void updateTitle(String projectId, String name) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId));
        project.updateTitle(name);
    }

    @Transactional
    public void deleteProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId);
        }
        projectRepository.deleteById(projectId);
    }
}
