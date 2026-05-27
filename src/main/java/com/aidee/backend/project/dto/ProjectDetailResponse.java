package com.aidee.backend.project.dto;

import com.aidee.backend.meeting.dto.MeetingSummaryResponse;
import com.aidee.backend.meeting.dto.MemoItemResponse;
import com.aidee.backend.project.Project;
import com.aidee.backend.schedule.dto.ScheduleResponse;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectDetailResponse(
        String id,
        String name,
        List<MeetingSummaryResponse> meetings,
        List<ScheduleResponse> schedules,
        List<MemoItemResponse> memos,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProjectDetailResponse of(Project project,
                                           List<MeetingSummaryResponse> meetings,
                                           List<ScheduleResponse> schedules,
                                           List<MemoItemResponse> memos) {
        return new ProjectDetailResponse(
                project.getId(),
                project.getTitle(),
                meetings,
                schedules,
                memos,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
