package com.aidee.backend.project;

import com.aidee.backend.auth.User;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
public class Project {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Project() {}

    public static Project create(User user) {
        Project p = new Project();
        p.id = UUID.randomUUID().toString();
        p.title = "새 프로젝트";
        p.user = user;
        p.createdAt = LocalDateTime.now();
        p.updatedAt = LocalDateTime.now();
        return p;
    }

    public void updateTitle(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
