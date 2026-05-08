package com.aidee.backend.schedule.dto;

import com.aidee.backend.schedule.Schedule;

public record UpdateScheduleResponse(
        String id,
        String title
) {
    public static UpdateScheduleResponse from(Schedule schedule) {
        return new UpdateScheduleResponse(schedule.getId(), schedule.getTitle());
    }
}
