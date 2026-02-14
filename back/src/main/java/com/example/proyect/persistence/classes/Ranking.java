package com.example.proyect.persistence.classes;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "ranking",
    indexes = {
        @Index(name = "idx_ranking_user_id", columnList = "user_id")
    }
)
public class Ranking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) 
    @Column(name = "rank_id")
    private Long id;

    // Un registro por usuario (UNIQUE)
    @OneToOne(optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_ranking_user")
    )
    private User user;

    @Column(name = "points", nullable = false)
    private Integer points = 0;

    // DEFAULT now() en DB; fallback en @PrePersist
    @Column(name = "reached_at", nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT now()")
    private OffsetDateTime reachedAt;

    public Ranking() {}

    @PrePersist
    public void prePersist() {
        if (this.points == null) {
            this.points = 0;
        }
        if (this.reachedAt == null) {
            this.reachedAt = OffsetDateTime.now();
        }
    }

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Integer getPoints() { return this.points; }
    public void setPoints(Integer points) { this.points = points; }

    public OffsetDateTime getReachedAt() { return this.reachedAt; }
    public void setReachedAt(OffsetDateTime reachedAt) { this.reachedAt = reachedAt; }
}
