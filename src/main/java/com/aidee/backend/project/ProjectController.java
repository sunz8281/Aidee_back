package com.aidee.backend.project;

import com.aidee.backend.project.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/projects")
    public ResponseEntity<Map<String, List<ProjectSummaryResponse>>> getProjects() {
        return ResponseEntity.ok(Map.of("items", projectService.getProjects()));
    }

    @PostMapping("/projects")
    public ResponseEntity<ProjectCreateResponse> createProject() {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject());
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ProjectDetailResponse> getProject(@PathVariable String projectId) {
        return ResponseEntity.ok(projectService.getProject(projectId));
    }

    @PatchMapping("/projects/{projectId}/title")
    public ResponseEntity<Void> updateTitle(@PathVariable String projectId,
                                             @RequestBody UpdateProjectTitleRequest request) {
        projectService.updateTitle(projectId, request.name());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable String projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }
}
