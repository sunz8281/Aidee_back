package com.aidee.backend.meeting.dto;

import com.aidee.backend.script.ScriptSegment;

public record ScriptSegmentResponse(
        int startTime,
        String contents
) {
    public static ScriptSegmentResponse from(ScriptSegment segment) {
        return new ScriptSegmentResponse(segment.getStartTime(), segment.getContents());
    }
}
