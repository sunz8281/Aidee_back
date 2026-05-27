package com.aidee.backend.script;

import com.aidee.backend.meeting.Meeting;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "script_segments")
@Getter
public class ScriptSegment {

    @Id
    private String id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contents;

    @Column(nullable = false)
    private int startTime;

    private String speaker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    protected ScriptSegment() {}

    public static ScriptSegment create(Meeting meeting, int startTime, String contents, String speaker) {
        ScriptSegment s = new ScriptSegment();
        s.id = UUID.randomUUID().toString();
        s.meeting = meeting;
        s.startTime = startTime;
        s.contents = contents;
        s.speaker = speaker;
        return s;
    }
}
