package com.aidee.backend.meeting.dto;

import com.aidee.backend.meeting.Meeting;

import java.time.LocalDateTime;

public record MeetingSummaryResponse(
        String id,
        String title,
        String status,
        LocalDateTime meetingAt,
        LocalDateTime createdAt
) {
    public static MeetingSummaryResponse from(Meeting meeting) {
        return new MeetingSummaryResponse(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getStatus().name().toLowerCase(),
                meeting.getMeetingAt(),
                meeting.getCreatedAt()
        );
    }
}
