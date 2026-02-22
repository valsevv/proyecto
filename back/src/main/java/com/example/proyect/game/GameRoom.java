package com.example.proyect.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.proyect.game.units.Unit.HexCoord;
import com.example.proyect.game.units.drone.AerialDrone;
import com.example.proyect.game.units.drone.Drone;
import com.example.proyect.game.units.drone.NavalDrone;

/**
me parece que este GameRoom sustituye nuestro Game o como es la movida
 */
public class GameRoom {

    private final String roomId;

    public GameRoom(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomId() {
        return roomId;
    }

    public static final int MAX_PLAYERS = 2;
    public static final int AERIAL_DRONES_PER_PLAYER = 12;
    public static final int NAVAL_DRONES_PER_PLAYER = 6;
    public static final int ACTIONS_PER_TURN = 99; // Unlimited - frontend tracks per-drone limits

    // Starting positions per player (left side vs right side of the map)
    private static final double[][] STARTS_P0_AERIAL = { {300, 600}, {300, 900}, {300, 1200} };
    private static final double[][] STARTS_P1_AERIAL = { {2100, 600}, {2100, 900}, {2100, 1200} };
    private static final double[][] STARTS_P0_NAVAL = {
            {300, 450}, {300, 630}, {300, 810}, {300, 990}, {300, 1170}, {300, 1350}
    };
    private static final double[][] STARTS_P1_NAVAL = {
            {2100, 450}, {2100, 630}, {2100, 810}, {2100, 990}, {2100, 1170}, {2100, 1350}
    };

    private final List<PlayerState> players = new ArrayList<>();
    
    // Side selection state (Naval or Aereo)
    private final Map<Integer, String> playerSides = new HashMap<>();

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
        double[][] starts = (index == 0) ? STARTS_P0_AERIAL : STARTS_P1_AERIAL;

        List<Drone> drones = new ArrayList<>();
        // Note: Drones will be created as placeholders initially
        // They will be recreated with proper type after side selection
        for (int i = 0; i < starts.length; i++) {
            double[] pos = starts[i];
            // Default to AerialDrone, will be replaced after side selection
            AerialDrone drone = new AerialDrone();
            drone.setId(UUID.randomUUID().toString());
            drone.setOwnerPlayerId(index);
            drone.setPosition(new HexCoord(pos[0], pos[1]));
            drones.add(drone);
        }

        PlayerState player = new PlayerState(sessionId, index, drones);
        players.add(player);
        return player;
    }
    
    /**
     * Recreate player's drones based on selected side.
     * Called after side selection.
     */
    public synchronized void createDronesForSide(int playerIndex, String side) {
        PlayerState player = getPlayerByIndex(playerIndex);
        if (player == null) return;

        boolean isNaval = "Naval".equals(side);
        double[][] starts = isNaval
                ? ((playerIndex == 0) ? STARTS_P0_NAVAL : STARTS_P1_NAVAL)
                : ((playerIndex == 0) ? STARTS_P0_AERIAL : STARTS_P1_AERIAL);
        List<Drone> drones = new ArrayList<>();
        
        for (int i = 0; i < starts.length; i++) {
            double[] pos = starts[i];
            Drone drone = isNaval ? new NavalDrone() : new AerialDrone();
            drone.setId(UUID.randomUUID().toString());
            drone.setOwnerPlayerId(playerIndex);
            drone.setPosition(new HexCoord(pos[0], pos[1]));
            drones.add(drone);
        }
        
        // Replace drones in player state
        player.getDrones().clear();
        player.getDrones().addAll(drones);
        player.setSide(side);
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

        Drone drone = player.getDrones().get(droneIndex);
        drone.setPosition(new HexCoord(x, y));
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
     * Get the side chosen by a player.
     */
    public synchronized String getPlayerSide(int playerIndex) {
        return playerSides.get(playerIndex);
    }
    
    /**
     * Set the side for a player.
     */
    public synchronized void setPlayerSide(int playerIndex, String side) {
        playerSides.put(playerIndex, side);
    }
    
    /**
     * Check if both players have selected their sides.
     */
    public synchronized boolean bothSidesSelected() {
        return playerSides.size() == MAX_PLAYERS;
    }

    /**
     * Reset the room so new players can join.
     */
    public synchronized void reset() {
        players.clear();
        playerSides.clear();
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

    public synchronized Drone getDrone(int playerIndex, int droneIndex) {
        PlayerState player = getPlayerByIndex(playerIndex);
        if (player == null) return null;
        List<Drone> drones = player.getDrones();
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
            pm.put("side", p.getSide());

            List<Map<String, Object>> droneMaps = new ArrayList<>();
            for (Drone d : p.getDrones()) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("x", d.getPosition().getX());
                dm.put("y", d.getPosition().getY());
                dm.put("health", d.getCurrentHp());
                dm.put("maxHealth", d.getMaxHp());
                dm.put("attackDamage", d.getWeapon().getDamage());
                dm.put("attackRange", d.getWeapon().getRange());
                dm.put("alive", d.isAlive());
                // Add drone type for frontend rendering
                dm.put("droneType", d instanceof NavalDrone ? "Naval" : "Aereo");
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
