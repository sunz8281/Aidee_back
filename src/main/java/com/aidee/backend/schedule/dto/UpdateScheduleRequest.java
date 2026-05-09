package com.aidee.backend.schedule.dto;

import java.time.LocalDateTime;

public record UpdateScheduleRequest(
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Boolean allDay,
        String sourceMeetingId
) {
}
