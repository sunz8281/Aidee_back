package com.aidee.backend.meeting;

import java.time.LocalDateTime;
import java.util.List;

public record LlmAnalysisResult(
        String summary,
        String memo,
        List<ScheduleData> schedules
) {
    public record ScheduleData(
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean allDay
    ) {}
}
