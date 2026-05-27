package com.aidee.backend.meeting;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "speaker_names",
        uniqueConstraints = @UniqueConstraint(columnNames = {"meeting_id", "label"}))
@Getter
public class SpeakerName {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String name;

    protected SpeakerName() {}

    public static SpeakerName create(Meeting meeting, String label, String name) {
        SpeakerName s = new SpeakerName();
        s.meeting = meeting;
        s.label = label;
        s.name = name;
        return s;
    }

    public void updateName(String name) {
        this.name = name;
    }
}
