package com.example.mainapp.model;

import java.time.LocalDateTime;

public class User {

    public enum Role {
        STANDARD, ADMIN
    }

    private final long id;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final LocalDateTime createdAt;

    public User(long id, String username, String passwordHash, Role role, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

}
