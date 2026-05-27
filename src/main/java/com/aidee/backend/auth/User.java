package com.aidee.backend.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    private String pictureUrl;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String providerId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static User create(String email, String name, String pictureUrl, String provider, String providerId) {
        User user = new User();
        user.id = UUID.randomUUID().toString();
        user.email = email;
        user.name = name;
        user.pictureUrl = pictureUrl;
        user.provider = provider;
        user.providerId = providerId;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        return user;
    }

    public void update(String name, String pictureUrl) {
        this.name = name;
        this.pictureUrl = pictureUrl;
        this.updatedAt = LocalDateTime.now();
    }
}
