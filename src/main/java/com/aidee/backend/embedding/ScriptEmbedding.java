package com.aidee.backend.embedding;

import com.aidee.backend.common.VectorConverter;
import jakarta.persistence.*;
import lombok.Getter;

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

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    @Convert(converter = VectorConverter.class)
    @Column(columnDefinition = "vector(768)", nullable = false)
    private float[] embedding;

    protected ScriptEmbedding() {}

    public static ScriptEmbedding create(String scriptId, String meetingId, String projectId,
                                          String text, float[] embedding) {
        ScriptEmbedding e = new ScriptEmbedding();
        e.id = UUID.randomUUID().toString();
        e.scriptId = scriptId;
        e.meetingId = meetingId;
        e.projectId = projectId;
        e.text = text;
        e.embedding = embedding;
        return e;
    }
}
