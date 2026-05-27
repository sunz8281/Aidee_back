package com.aidee.backend.project;

import com.aidee.backend.auth.User;
import com.aidee.backend.project.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/projects")
    public ResponseEntity<Map<String, List<ProjectSummaryResponse>>> getProjects(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of("items", projectService.getProjects(user)));
    }

    @PostMapping("/projects")
    public ResponseEntity<ProjectCreateResponse> createProject(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject(user));
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ProjectDetailResponse> getProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectService.getProject(projectId, user));
    }

    @PatchMapping("/projects/{projectId}/title")
    public ResponseEntity<Void> updateTitle(
            @PathVariable String projectId,
            @RequestBody UpdateProjectTitleRequest request,
            @AuthenticationPrincipal User user) {
        projectService.updateTitle(projectId, request.name(), user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        projectService.deleteProject(projectId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/share")
    public ResponseEntity<ShareProjectResponse> enableSharing(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(projectService.enableSharing(projectId, user));
    }

    @DeleteMapping("/projects/{projectId}/share")
    public ResponseEntity<Void> disableSharing(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        projectService.disableSharing(projectId, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/share/{shareToken}")
    public ResponseEntity<ProjectDetailResponse> getSharedProject(
            @PathVariable String shareToken) {
        return ResponseEntity.ok(projectService.getSharedProject(shareToken));
    }
}
