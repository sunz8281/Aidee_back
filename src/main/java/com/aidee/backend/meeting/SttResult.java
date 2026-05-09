package com.aidee.backend.meeting;

import java.util.List;

public record SttResult(
        String fullText,
        List<Segment> segments
) {
    public record Segment(int startTime, String text) {}
}
