package com.example.proyect.game;

import java.util.List;

/**
 * Server-side state for a player in the game room.
 */
public class PlayerState {

    private final String sessionId;
    private final int playerIndex;
    private final List<DroneState> drones;

    public PlayerState(String sessionId, int playerIndex, List<DroneState> drones) {
        this.sessionId = sessionId;
        this.playerIndex = playerIndex;
        this.drones = drones;
    }

    public String getSessionId() { return sessionId; }
    public int getPlayerIndex() { return playerIndex; }
    public List<DroneState> getDrones() { return drones; }
}
