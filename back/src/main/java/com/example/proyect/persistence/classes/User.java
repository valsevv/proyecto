package com.example.proyect.persistence.classes;


import java.time.OffsetDateTime;//no se usa

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_player_username", columnNames = "username")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    // Nunca guardar password plano
    @Column(name = "password_hash", nullable = false, length = 255, columnDefinition = "TEXT")
    private String passwordHash;

    @Column(nullable = false)
    private int wins = 0;

    @Column(nullable = false)
    private int losses = 0;

    @Column(nullable = false)
    private int score = 0;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT now()")
    private OffsetDateTime createdAt;

    @Column(name = "last_login_at", columnDefinition = "TIMESTAMPTZ DEFAULT now()")
    private OffsetDateTime lastLoginAt;

    protected User() {
        // Constructor vacío requerido por JPA
    }

    public User(String username, String passwordHash) {
        setUsername(username);
        setPasswordHash(passwordHash);
        this.createdAt = OffsetDateTime.now();
    }

    // =========================
    // Getters
    // =========================

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getScore() { return score; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }

    // =========================
    // Setters controlados
    // =========================

    public void setUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username requerido");
        }
        if (username.length() > 50) {
            throw new IllegalArgumentException("Username demasiado largo");
        }
        this.username = username.trim();
    }

    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash requerido");
        }
        this.passwordHash = passwordHash;
    }

    public void markLoginNow() {
        this.lastLoginAt = OffsetDateTime.now();
    }

    // =========================
    // Dominio (ranking)
    // =========================

    public void registerWin(int points) {
        this.wins++;
        this.score += Math.max(0, points);
    }

    public void registerLoss(int penalty) {
        this.losses++;
        this.score = Math.max(0, this.score - Math.max(0, penalty));
    }
}
