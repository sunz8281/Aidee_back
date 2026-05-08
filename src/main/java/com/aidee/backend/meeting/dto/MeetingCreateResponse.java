package com.aidee.backend.meeting.dto;

import com.aidee.backend.meeting.Meeting;

import java.time.LocalDateTime;

public record MeetingCreateResponse(
        String id,
        String title,
        LocalDateTime meetingAt,
        LocalDateTime createdAt
) {
    public static MeetingCreateResponse from(Meeting meeting) {
        return new MeetingCreateResponse(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getMeetingAt(),
                meeting.getCreatedAt()
        );
    }
}
