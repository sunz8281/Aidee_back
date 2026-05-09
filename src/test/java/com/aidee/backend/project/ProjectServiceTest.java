package com.aidee.backend.project;

import com.aidee.backend.common.ResourceNotFoundException;
import com.aidee.backend.meeting.MeetingRepository;
import com.aidee.backend.project.dto.ProjectCreateResponse;
import com.aidee.backend.project.dto.ProjectDetailResponse;
import com.aidee.backend.project.dto.ProjectSummaryResponse;
import com.aidee.backend.schedule.ScheduleRepository;
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
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private MeetingRepository meetingRepository;
    @Mock
    private ScheduleRepository scheduleRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void 프로젝트_목록_조회_성공() {
        Project project = Project.create();
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(meetingRepository.countByProjectId(project.getId())).thenReturn(2L);
        when(scheduleRepository.countByProjectId(project.getId())).thenReturn(3L);

        List<ProjectSummaryResponse> result = projectService.getProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("새 프로젝트");
        assertThat(result.get(0).meetingsCount()).isEqualTo(2L);
        assertThat(result.get(0).schedulesCount()).isEqualTo(3L);
    }

    @Test
    void 프로젝트_생성_성공() {
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectCreateResponse response = projectService.createProject();

        assertThat(response.id()).isNotNull();
        verify(projectRepository, times(1)).save(any(Project.class));
    }

    @Test
    void 프로젝트_단건_조회_성공() {
        Project project = Project.create();
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(meetingRepository.findByProjectId(project.getId())).thenReturn(List.of());
        when(scheduleRepository.findByProjectId(project.getId())).thenReturn(List.of());

        ProjectDetailResponse result = projectService.getProject(project.getId());

        assertThat(result.id()).isEqualTo(project.getId());
        assertThat(result.name()).isEqualTo("새 프로젝트");
    }

    @Test
    void 존재하지_않는_프로젝트_조회시_404_반환() {
        when(projectRepository.findById("not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject("not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 프로젝트_타이틀_수정_성공() {
        Project project = Project.create();
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));

        projectService.updateTitle(project.getId(), "수정된 프로젝트");

        assertThat(project.getTitle()).isEqualTo("수정된 프로젝트");
    }

    @Test
    void 존재하지_않는_프로젝트_수정시_404_반환() {
        when(projectRepository.findById("not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.updateTitle("not-exist", "이름"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 프로젝트_삭제_성공() {
        when(projectRepository.existsById("project-id")).thenReturn(true);

        projectService.deleteProject("project-id");

        verify(projectRepository).deleteById("project-id");
    }

    @Test
    void 존재하지_않는_프로젝트_삭제시_404_반환() {
        when(projectRepository.existsById("not-exist")).thenReturn(false);

        assertThatThrownBy(() -> projectService.deleteProject("not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
