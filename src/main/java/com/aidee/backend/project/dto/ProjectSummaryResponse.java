package com.aidee.backend.project.dto;

import com.aidee.backend.project.Project;

import java.time.LocalDateTime;

public record ProjectSummaryResponse(
        String id,
        String name,
        LocalDateTime createdAt
) {
    public static ProjectSummaryResponse from(Project project) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getTitle(),
                project.getCreatedAt()
        );
    }
}
