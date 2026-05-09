package com.aidee.backend.schedule;

import com.aidee.backend.meeting.Meeting;
import com.aidee.backend.project.Project;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedules")
@Getter
public class Schedule {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private boolean allDay;

    private String sourceType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    protected Schedule() {}

    public static Schedule create(Project project, Meeting meeting,
                                   String title, LocalDateTime startTime,
                                   LocalDateTime endTime, boolean allDay, String sourceType) {
        Schedule s = new Schedule();
        s.id = UUID.randomUUID().toString();
        s.project = project;
        s.meeting = meeting;
        s.title = title;
        s.startTime = startTime;
        s.endTime = endTime;
        s.allDay = allDay;
        s.sourceType = sourceType;
        s.createdAt = LocalDateTime.now();
        return s;
    }

    public void update(String title, LocalDateTime startTime, LocalDateTime endTime, Boolean allDay, Meeting meeting) {
        if (title != null) this.title = title;
        if (startTime != null) this.startTime = startTime;
        if (endTime != null) this.endTime = endTime;
        if (allDay != null) this.allDay = allDay;
        if (meeting != null) this.meeting = meeting;
    }
}
