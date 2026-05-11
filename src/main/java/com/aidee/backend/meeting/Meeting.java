package com.aidee.backend.meeting;

import com.aidee.backend.project.Project;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meetings")
@Getter
public class Meeting {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MeetingStatus status;

    @Column(nullable = false)
    private LocalDateTime meetingAt;

    private String recordingFile;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    protected Meeting() {}

    public static Meeting create(Project project, String title, LocalDateTime meetingAt) {
        Meeting m = new Meeting();
        m.id = UUID.randomUUID().toString();
        m.project = project;
        m.title = title;
        m.status = MeetingStatus.PENDING;
        m.meetingAt = meetingAt;
        m.createdAt = LocalDateTime.now();
        return m;
    }

    public void updateTitle(String title) {
        if (title != null) this.title = title;
    }

    public void updateMeetingAt(LocalDateTime meetingAt) {
        if (meetingAt != null) this.meetingAt = meetingAt;
    }

    public void updateMemo(String memo) {
        this.memo = memo;
    }

    public void startProcessing(String recordingFile) {
        this.recordingFile = recordingFile;
        this.status = MeetingStatus.PROCESSING;
    }

    public void completeProcessing(String summary, String memo) {
        this.summary = summary;
        if (memo != null && !memo.isBlank()) this.memo = memo;
        this.status = MeetingStatus.DONE;
    }

    public void failProcessing() {
        this.status = MeetingStatus.FAILED;
    }
}
