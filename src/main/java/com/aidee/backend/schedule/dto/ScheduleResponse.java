package com.aidee.backend.schedule.dto;

import com.aidee.backend.schedule.Schedule;

import java.time.LocalDateTime;

public record ScheduleResponse(
        String id,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean allDay,
        String sourceType,
        String meetingId,
        LocalDateTime createdAt
) {
    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getTitle(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.isAllDay(),
                schedule.getSourceType(),
                schedule.getMeeting() != null ? schedule.getMeeting().getId() : null,
                schedule.getCreatedAt()
        );
    }
}
