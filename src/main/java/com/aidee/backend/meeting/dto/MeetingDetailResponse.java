package com.aidee.backend.meeting.dto;

import com.aidee.backend.meeting.Meeting;
import com.aidee.backend.schedule.dto.ScheduleResponse;

import java.time.LocalDateTime;
import java.util.List;

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
        LocalDateTime createdAt
) {
    public static MeetingDetailResponse of(Meeting meeting,
                                           List<ScriptSegmentResponse> scripts,
                                           List<ScheduleResponse> schedules,
                                           String audioUrl) {
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
                meeting.getCreatedAt()
        );
    }
}
