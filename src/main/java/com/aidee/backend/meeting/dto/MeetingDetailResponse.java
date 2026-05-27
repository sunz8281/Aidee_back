package com.aidee.backend.meeting.dto;

import com.aidee.backend.meeting.Meeting;
import com.aidee.backend.schedule.dto.ScheduleResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MeetingDetailResponse(
        String id,
        String title,
        LocalDateTime meetingAt,
        String status,
        String summary,
        String memo,
        List<ScriptSegmentResponse> scripts,
        List<ScheduleResponse> schedules,
        String audioUrl,
        LocalDateTime createdAt,
        Map<String, String> speakerNames
) {
    public static MeetingDetailResponse of(Meeting meeting,
                                           List<ScriptSegmentResponse> scripts,
                                           List<ScheduleResponse> schedules,
                                           String audioUrl,
                                           Map<String, String> speakerNames) {
        return new MeetingDetailResponse(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getMeetingAt(),
                meeting.getStatus().name().toLowerCase(),
                meeting.getSummary(),
                meeting.getMemo(),
                scripts,
                schedules,
                audioUrl,
                meeting.getCreatedAt(),
                speakerNames
        );
    }
}
