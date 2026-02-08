package com.example.proyect.game;

import java.util.*;

/**
 * A single game room for 2 players, each with 3 drones.
 * All public methods are synchronized for thread safety.
 */
public class GameRoom {

    private static final int MAX_PLAYERS = 2;
    private static final int DRONES_PER_PLAYER = 3;

    // Starting positions per player (left side vs right side of the map)
    private static final double[][] STARTS_P0 = { {300, 600}, {300, 900}, {300, 1200} };
    private static final double[][] STARTS_P1 = { {2100, 600}, {2100, 900}, {2100, 1200} };

    private final List<PlayerState> players = new ArrayList<>();

    /**
     * Add a player to the room. Returns the new PlayerState, or null if full.
     */
    public synchronized PlayerState addPlayer(String sessionId) {
        if (players.size() >= MAX_PLAYERS) return null;

        int index = players.size();
        double[][] starts = (index == 0) ? STARTS_P0 : STARTS_P1;

        List<DroneState> drones = new ArrayList<>();
        for (double[] pos : starts) {
            drones.add(new DroneState(pos[0], pos[1]));
        }

        PlayerState player = new PlayerState(sessionId, index, drones);
        players.add(player);
        return player;
    }

    /**
     * Remove a player by session ID. Returns the removed player, or null.
     */
    public synchronized PlayerState removePlayer(String sessionId) {
        Iterator<PlayerState> it = players.iterator();
        while (it.hasNext()) {
            PlayerState p = it.next();
            if (p.getSessionId().equals(sessionId)) {
                it.remove();
                return p;
            }
        }
        return null;
    }

    /**
     * Move a drone. Returns true if the move was valid.
     */
    public synchronized boolean moveDrone(String sessionId, int droneIndex, double x, double y) {
        PlayerState player = getPlayerBySession(sessionId);
        if (player == null) return false;
        if (droneIndex < 0 || droneIndex >= player.getDrones().size()) return false;

        DroneState drone = player.getDrones().get(droneIndex);
        drone.setX(x);
        drone.setY(y);
        return true;
    }

    public synchronized PlayerState getPlayerBySession(String sessionId) {
        for (PlayerState p : players) {
            if (p.getSessionId().equals(sessionId)) return p;
        }
        return null;
    }

    public synchronized boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    /**
     * Reset the room so new players can join.
     */
    public synchronized void reset() {
        players.clear();
    }

    /**
     * Serialize the full game state to a Map (for JSON).
     */
    public synchronized Map<String, Object> toStateMap() {
        List<Map<String, Object>> playerMaps = new ArrayList<>();
        for (PlayerState p : players) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("playerIndex", p.getPlayerIndex());

            List<Map<String, Object>> droneMaps = new ArrayList<>();
            for (DroneState d : p.getDrones()) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("x", d.getX());
                dm.put("y", d.getY());
                droneMaps.add(dm);
            }
            pm.put("drones", droneMaps);
            playerMaps.add(pm);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("players", playerMaps);
        return state;
    }
}
