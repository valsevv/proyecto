package com.example.proyect.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Lobby {

    private final String lobbyId;
    private final List<Long> playerIds = new ArrayList<>(2);
    private LobbyStatus status = LobbyStatus.WAITING; //comienza pendiente

    public Lobby(String lobbyId) {
        this.lobbyId = lobbyId;
    }

    public String getLobbyId() { return lobbyId; }
    public LobbyStatus getStatus() { return status; }
    public List<Long> getPlayerIds() { return Collections.unmodifiableList(playerIds); }

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
