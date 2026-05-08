package com.aidee.backend.meeting.dto;

import java.time.LocalDateTime;

public record UpdateMeetingRequest(
        String title,
        LocalDateTime meetingAt
) {
}
