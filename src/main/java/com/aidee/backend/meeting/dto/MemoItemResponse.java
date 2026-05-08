package com.aidee.backend.meeting.dto;

import com.aidee.backend.meeting.Meeting;

public record MemoItemResponse(
        String meetingId,
        String meetingTitle,
        String memo
) {
    public static MemoItemResponse from(Meeting meeting) {
        return new MemoItemResponse(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getMemo()
        );
    }
}
