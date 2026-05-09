package com.aidee.backend.project.dto;

import com.aidee.backend.project.Project;

import java.time.LocalDateTime;

public record ProjectSummaryResponse(
        String id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long meetingsCount,
        long schedulesCount,
        long memosCount
) {
    public static ProjectSummaryResponse of(Project project,
                                            long meetingsCount,
                                            long schedulesCount,
                                            long memosCount) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getTitle(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                meetingsCount,
                schedulesCount,
                memosCount
        );
    }
}
