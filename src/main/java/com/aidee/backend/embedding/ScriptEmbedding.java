package com.aidee.backend.embedding;

import com.aidee.backend.common.VectorConverter;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "script_embeddings")
@Getter
public class ScriptEmbedding {

    @Id
    private String id;

    @Column(name = "script_id", nullable = false)
    private String scriptId;

    @Column(name = "meeting_id", nullable = false)
    private String meetingId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "meeting_title", nullable = false)
    private String meetingTitle;

    @Column(name = "meeting_at", nullable = false)
    private LocalDateTime meetingAt;

    @Column(name = "start_time", nullable = false)
    private int startTime;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    @Column(name = "speaker")
    private String speaker;

    @Convert(converter = VectorConverter.class)
    @Column(columnDefinition = "vector(768)", nullable = false)
    @ColumnTransformer(write = "?::vector")
    private float[] embedding;

    protected ScriptEmbedding() {}

    public static ScriptEmbedding create(String scriptId, String meetingId, String projectId,
                                          String meetingTitle, LocalDateTime meetingAt,
                                          int startTime, String text, String speaker, float[] embedding) {
        ScriptEmbedding e = new ScriptEmbedding();
        e.id = UUID.randomUUID().toString();
        e.scriptId = scriptId;
        e.meetingId = meetingId;
        e.projectId = projectId;
        e.meetingTitle = meetingTitle;
        e.meetingAt = meetingAt;
        e.startTime = startTime;
        e.text = text;
        e.speaker = speaker;
        e.embedding = embedding;
        return e;
    }
}
