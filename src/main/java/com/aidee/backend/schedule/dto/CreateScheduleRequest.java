package com.aidee.backend.schedule.dto;

import java.time.LocalDateTime;

public record CreateScheduleRequest(
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean allDay,
        String sourceType,
        String sourceMeetingId
) {
}
