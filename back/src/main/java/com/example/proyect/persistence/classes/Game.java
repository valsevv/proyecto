package com.example.proyect.persistence.classes;

import java.time.OffsetDateTime;

import com.example.proyect.game.GameStateJsonConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "game",
    indexes = {
        @Index(name = "idx_game_player1_id", columnList = "player1_id"),
        @Index(name = "idx_game_player2_id", columnList = "player2_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_players", columnNames = {"player1_id", "player2_id"})
    }
)
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_id")
    private Long id;

    @Column(name = "state", nullable = false, columnDefinition = "jsonb")
    @Convert(converter = GameStateJsonConverter.class)
    private GameState state = defaultState();

    @Column(name = "player1_id", nullable = false)
    private Long player1Id;

    @Column(name = "player2_id", nullable = false)
    private Long player2Id;

    // DEFAULT now() en DB; fallback en @PrePersist
    @Column(name = "started_at", nullable = false, columnDefinition = "TIMESTAMPTZ DEFAULT now()")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    public Game() {}

    private static GameState defaultState() {
        GameState state = new GameState();
        state.setStatus("IN_PROGRESS");
        state.setTurn(1);
        return state;
    }

    @PrePersist
    public void prePersist() {
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
        validatePlayersDistinct();
    }

    @PreUpdate
    public void preUpdate() {
        validatePlayersDistinct();
    }

    private void validatePlayersDistinct() {
        if (player1Id != null && player2Id != null) {
            if (player1Id.equals(player2Id)) {
                throw new IllegalStateException("player1 y player2 no pueden ser el mismo usuario");
            }
        }
    }

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public GameState getState() { return state; }
    public void setState(GameState state) { this.state = state; }

    public Long getPlayer1Id() { return player1Id; }
    public void setPlayer1Id(Long player1Id) { this.player1Id = player1Id; }

    public Long getPlayer2Id() { return player2Id; }
    public void setPlayer2Id(Long player2Id) { this.player2Id = player2Id; } // <-- corregido

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
}