package com.example.proyect.game;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * A single game room for 2 players, each with 3 drones.
 * Manages turn-based gameplay with actions per turn.
 * All public methods are synchronized for thread safety.
 */
@Component
public class GameRoom {

    public static final int MAX_PLAYERS = 2;
    public static final int DRONES_PER_PLAYER = 3;
    public static final int ACTIONS_PER_TURN = 99; // Unlimited - frontend tracks per-drone limits

    // Starting positions per player (left side vs right side of the map)
    private static final double[][] STARTS_P0 = { {300, 600}, {300, 900}, {300, 1200} };
    private static final double[][] STARTS_P1 = { {2100, 600}, {2100, 900}, {2100, 1200} };

    private final List<PlayerState> players = new ArrayList<>();

    // Turn state
    private boolean gameStarted = false;
    private int currentTurn = 0; // Player index whose turn it is
    private int actionsRemaining = ACTIONS_PER_TURN;

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
        gameStarted = false;
        currentTurn = 0;
        actionsRemaining = ACTIONS_PER_TURN;
    }

    // ========== Turn Management ==========

    public synchronized void startGame() {
        gameStarted = true;
        currentTurn = 0;
        actionsRemaining = ACTIONS_PER_TURN;
    }

    public synchronized boolean isGameStarted() {
        return gameStarted;
    }

    public synchronized int getCurrentTurn() {
        return currentTurn;
    }

    public synchronized int getActionsRemaining() {
        return actionsRemaining;
    }

    public synchronized boolean isPlayerTurn(int playerIndex) {
        return gameStarted && currentTurn == playerIndex;
    }

    public synchronized boolean isPlayerTurn(String sessionId) {
        PlayerState player = getPlayerBySession(sessionId);
        return player != null && isPlayerTurn(player.getPlayerIndex());
    }

    /**
     * Consume an action. Returns true if action was available.
     */
    public synchronized boolean useAction() {
        if (actionsRemaining > 0) {
            actionsRemaining--;
            return true;
        }
        return false;
    }

    /**
     * End the current turn and switch to the other player.
     */
    public synchronized void endTurn() {
        currentTurn = (currentTurn + 1) % MAX_PLAYERS;
        actionsRemaining = ACTIONS_PER_TURN;
    }

    // ========== Player/Drone Accessors ==========

    public synchronized PlayerState getPlayerByIndex(int index) {
        if (index >= 0 && index < players.size()) {
            return players.get(index);
        }
        return null;
    }

    public synchronized DroneState getDrone(int playerIndex, int droneIndex) {
        PlayerState player = getPlayerByIndex(playerIndex);
        if (player == null) return null;
        List<DroneState> drones = player.getDrones();
        if (droneIndex < 0 || droneIndex >= drones.size()) return null;
        return drones.get(droneIndex);
    }

    public synchronized List<PlayerState> getPlayers() {
        return new ArrayList<>(players);
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
                dm.put("health", d.getHealth());
                dm.put("maxHealth", d.getMaxHealth());
                dm.put("attackDamage", d.getAttackDamage());
                dm.put("attackRange", d.getAttackRange());
                dm.put("alive", d.isAlive());
                droneMaps.add(dm);
            }
            pm.put("drones", droneMaps);
            playerMaps.add(pm);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("players", playerMaps);
        state.put("currentTurn", currentTurn);
        state.put("actionsRemaining", actionsRemaining);
        state.put("gameStarted", gameStarted);
        return state;
    }
}
