package com.aidee.backend.meeting.dto;

import java.time.LocalDateTime;

public record CreateMeetingRequest(
        String title,
        LocalDateTime meetingAt
) {
}
