package com.example.proyect.lobby;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Lobby {

    private final String lobbyId;
    private final String creatorUsername;
    private final LocalDateTime createdAt;
    private final List<Long> playerIds = new ArrayList<>(2);
    private LobbyStatus status = LobbyStatus.WAITING; //comienza pendiente
    private Long gameId; // null = partida nueva, no null = cargar partida
    private Long expectedOpponentId; // For load game lobbies: only this user can join

    public Lobby(String lobbyId, String creatorUsername) {
        this.lobbyId = lobbyId;
        this.creatorUsername = creatorUsername;
        this.createdAt = LocalDateTime.now();
    }

    public String getLobbyId() { return lobbyId; }
    public String getCreatorUsername() { return creatorUsername; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LobbyStatus getStatus() { return status; }
    public List<Long> getPlayerIds() { return Collections.unmodifiableList(playerIds); }

    public Long getGameId() { return gameId; }

    public void setGameId(Long gameId) { 
        this.gameId = gameId; 
    }

    public Long getExpectedOpponentId() {
        return expectedOpponentId;
    }

    public void setExpectedOpponentId(Long expectedOpponentId) {
        this.expectedOpponentId = expectedOpponentId;
    }

    public boolean isLoadGameLobby() {
        return gameId != null;
    }

    public boolean isFull() {
        return playerIds.size() >= 2;
    }

    public void addPlayer(long playerId) {
        if (playerIds.contains(playerId)) return;
        if (playerIds.size() >= 2) throw new IllegalStateException("Lobby lleno");
        playerIds.add(playerId);
        status = (playerIds.size() == 2) ? LobbyStatus.READY : LobbyStatus.WAITING;
    }

    public boolean isReady() {
        return status == LobbyStatus.READY;
    }

    public void markStarted() {
        this.status = LobbyStatus.STARTED;
    }
}
