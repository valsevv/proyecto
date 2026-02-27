package com.example.proyect.game;

import java.util.List;

import com.example.proyect.game.units.drone.Drone;

public class PlayerState {

    private String sessionId; 
    private final int playerIndex;
    private final List<Drone> drones;
    private String side; // "Naval" or "Aereo"

    public PlayerState(String sessionId, int playerIndex, List<Drone> drones) {
        this.sessionId = sessionId;
        this.playerIndex = playerIndex;
        this.drones = drones;
    }

    public String getSessionId() { 
        return sessionId; 
    }

    public void setSessionId(String sessionId) { 
        this.sessionId = sessionId; 
    }


    public int getPlayerIndex() { 
        return playerIndex; 
    }

    public List<Drone> getDrones() { 
        return drones; 
    }

    public String getSide() { 
        return side; 
    }

    public void setSide(String side) { 
        this.side = side; 
    }
}