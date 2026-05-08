package com.aidee.backend.meeting;

import java.time.LocalDateTime;
import java.util.List;

public record LlmAnalysisResult(
        String summary,
        List<ScheduleData> schedules,
        List<ScriptData> scripts
) {
    public record ScheduleData(
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean allDay
    ) {}

    public record ScriptData(
            int startTime,
            String contents
    ) {}
}
