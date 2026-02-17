package com.example.proyect.persistence.classes;

//Con jakarte persistence podemos definir entidades que luego JPA sabe manejar en el repositorio
import java.time.OffsetDateTime;

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
                @UniqueConstraint(name = "uk_player_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_player_email", columnNames = "email")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userid;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String email;

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

    @Column(name = "last_connection", columnDefinition = "TIMESTAMPTZ DEFAULT now()")
    private OffsetDateTime lastConnection;

    protected User() {}

    public User(String username, String email, String passwordHash) {
        setUsername(username);
        setEmail(email);
        setPasswordHash(passwordHash);
        this.createdAt = OffsetDateTime.now();
    }

    // Getters

    public Long getUserId() { return userid; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getScore() { return score; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastConnection() { return lastConnection; }

    // Setters controlados

    public void setUsername(String username) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username requerido");

        if (username.length() > 50)
            throw new IllegalArgumentException("Username demasiado largo");

        this.username = username.trim();
    }

    public void setEmail(String email) {
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email requerido");

        if (!email.contains("@"))
            throw new IllegalArgumentException("Email inválido");

        this.email = email.trim().toLowerCase();
    }

    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank())
            throw new IllegalArgumentException("Password hash requerido");

        this.passwordHash = passwordHash;
    }

    public void markLoginNow() {
        this.lastConnection = OffsetDateTime.now();
    }

    public void registerWin(int points) {
        this.wins++;
        this.score += Math.max(0, points);
    }

    public void registerLoss(int penalty) {
        this.losses++;
        this.score = Math.max(0, this.score - Math.max(0, penalty));
    }
}
