package com.aidee.backend.project;

import com.aidee.backend.auth.User;
import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.meeting.MeetingRepository;
import com.aidee.backend.meeting.dto.MeetingSummaryResponse;
import com.aidee.backend.meeting.dto.MemoItemResponse;
import com.aidee.backend.project.dto.ProjectCreateResponse;
import com.aidee.backend.project.dto.ProjectDetailResponse;
import com.aidee.backend.project.dto.ProjectSummaryResponse;
import com.aidee.backend.project.dto.ShareProjectResponse;
import com.aidee.backend.schedule.ScheduleRepository;
import com.aidee.backend.schedule.dto.ScheduleResponse;
import com.aidee.backend.script.ScriptRepository;
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
    private final ScriptRepository scriptRepository;

    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> getProjects(User user) {
        return projectRepository.findByUserId(user.getId()).stream()
                .map(project -> ProjectSummaryResponse.of(
                        project,
                        meetingRepository.countByProjectId(project.getId()),
                        scheduleRepository.countByProjectId(project.getId())
                ))
                .toList();
    }

    @Transactional
    public ProjectCreateResponse createProject(User user) {
        Project project = Project.create(user);
        projectRepository.save(project);
        return new ProjectCreateResponse(project.getId());
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getProject(String projectId, User user) {
        Project project = projectRepository.findByIdAndUserId(projectId, user.getId())
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

        return ProjectDetailResponse.of(project, meetings, schedules, List.of());
    }

    @Transactional
    public void updateTitle(String projectId, String name, User user) {
        Project project = projectRepository.findByIdAndUserId(projectId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId));
        project.updateTitle(name);
    }

    @Transactional
    public void deleteProject(String projectId, User user) {
        Project project = projectRepository.findByIdAndUserId(projectId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId));
        scheduleRepository.deleteByProjectId(projectId);
        scriptRepository.deleteByMeetingProjectId(projectId);
        meetingRepository.deleteByProjectId(projectId);
        projectRepository.delete(project);
    }

    @Transactional
    public ShareProjectResponse enableSharing(String projectId, User user) {
        Project project = projectRepository.findByIdAndUserId(projectId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId));
        String token = project.enableSharing();
        return new ShareProjectResponse(token);
    }

    @Transactional
    public void disableSharing(String projectId, User user) {
        Project project = projectRepository.findByIdAndUserId(projectId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("프로젝트를 찾을 수 없습니다: " + projectId));
        project.disableSharing();
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse getSharedProject(String shareToken) {
        Project project = projectRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new ResourceNotFoundException("유효하지 않은 공유 링크입니다."));

        List<MeetingSummaryResponse> meetings = meetingRepository
                .findTop5ByProjectIdOrderByMeetingAtDesc(project.getId())
                .stream().map(MeetingSummaryResponse::from).toList();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        List<ScheduleResponse> schedules = scheduleRepository
                .findByProjectIdAndPeriodOverlaps(project.getId(), startOfMonth, endOfMonth)
                .stream().map(ScheduleResponse::from).toList();

        List<MemoItemResponse> memos = meetingRepository
                .findByProjectIdAndMemoIsNotNullOrderByMeetingAtDesc(project.getId()).stream()
                .filter(m -> m.getMemo() != null && !m.getMemo().isBlank())
                .map(MemoItemResponse::from).toList();

        return ProjectDetailResponse.of(project, meetings, schedules, memos);
    }
}
