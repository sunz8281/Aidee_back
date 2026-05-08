package com.aidee.backend.schedule.dto;

import com.aidee.backend.schedule.Schedule;

import java.time.LocalDateTime;

public record CreateScheduleResponse(
        String id,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt
) {
    public static CreateScheduleResponse from(Schedule schedule) {
        return new CreateScheduleResponse(
                schedule.getId(),
                schedule.getTitle(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getCreatedAt()
        );
    }
}
