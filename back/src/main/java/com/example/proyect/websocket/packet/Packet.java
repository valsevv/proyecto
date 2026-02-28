package com.example.proyect.websocket.packet;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple packet wrapper for WebSocket messages.
 */
public class Packet {
    private final PacketType type;
    private final Map<String, Object> payload;

    public Packet(PacketType type, Map<String, Object> payload) {
        this.type = type;
        this.payload = payload != null ? payload : new HashMap<>();
    }

    public PacketType getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) payload.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        Object value = payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }

    public static Packet gameSaved(Long gameId, int savedByPlayerIndex) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", gameId);
        payload.put("savedByPlayerIndex", savedByPlayerIndex);
        return new Packet(PacketType.GAME_SAVED, payload);
    }

    public int getInt(String key) {
        Object value = payload.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    public double getDouble(String key) {
        Object value = payload.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    public String getString(String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Convert packet to a Map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>(payload);
        map.put("type", type.getValue());
        return map;
    }

    public static Packet gameLoaded(Object state) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("state", state);
        return new Packet(PacketType.GAME_LOADED, payload);
    }

    public static Packet gameLoadError(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        payload.put("operation", "loadGame");
        return new Packet(PacketType.ERROR, payload);
    }


    // Factory methods for common packets

    public static Packet of(PacketType type) {
        return new Packet(type, new HashMap<>());
    }

    public static Packet of(PacketType type, String key, Object value) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(key, value);
        return new Packet(type, payload);
    }

    public static Packet of(PacketType type, Map<String, Object> payload) {
        return new Packet(type, payload);
    }

    public static Packet welcome(String playerId, int playerIndex) {
        return welcome(playerId, playerIndex, false, null);
    }

    public static Packet welcome(String playerId, int playerIndex, boolean isLoadGame, Long gameId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", playerId);
        payload.put("playerIndex", playerIndex);
        payload.put("isLoadGame", isLoadGame);
        if (gameId != null) {
            payload.put("gameId", gameId);
        }
        return new Packet(PacketType.WELCOME, payload);
    }

    public static Packet gameStart(Object state) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("state", state);
        return new Packet(PacketType.GAME_START, payload);
    }

    public static Packet turnStart(int activePlayer, int actionsRemaining) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("activePlayer", activePlayer);
        payload.put("actionsRemaining", actionsRemaining);
        return new Packet(PacketType.TURN_START, payload);
    }

    public static Packet moveDrone(int playerIndex, int droneIndex, double x, double y, int remainingFuel, boolean destroyedByFuel) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerIndex", playerIndex);
        payload.put("droneIndex", droneIndex);
        payload.put("x", x);
        payload.put("y", y);
        payload.put("remainingFuel", remainingFuel);
        payload.put("destroyedByFuel", destroyedByFuel);
        return new Packet(PacketType.MOVE_DRONE, payload);
    }

    public static Packet fuelUpdate(int playerIndex, int droneIndex, int remainingFuel, boolean destroyedByFuel) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerIndex", playerIndex);
        payload.put("droneIndex", droneIndex);
        payload.put("remainingFuel", remainingFuel);
        payload.put("destroyedByFuel", destroyedByFuel);
        return new Packet(PacketType.MOVE_DRONE, payload);
    }

    public static Packet attackResult(int attackerPlayer, int attackerDrone, 
                                       int targetPlayer, int targetDrone, 
                                       int damage, int remainingHealth, boolean hit) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("attackerPlayer", attackerPlayer);
        payload.put("attackerDrone", attackerDrone);
        payload.put("targetPlayer", targetPlayer);
        payload.put("targetDrone", targetDrone);
        payload.put("damage", damage);
        payload.put("remainingHealth", remainingHealth);
        payload.put("hit", hit);
        return new Packet(PacketType.ATTACK_RESULT, payload);
    }

    public static Packet playerLeft(int playerIndex) {
        return Packet.of(PacketType.PLAYER_LEFT, "playerIndex", playerIndex);
    }

    public static Packet error(String message) {
        return Packet.of(PacketType.ERROR, "message", message);
    }

    public static Packet sideChosen(int playerIndex, String side) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerIndex", playerIndex);
        payload.put("side", side);
        return new Packet(PacketType.SIDE_CHOSEN, payload);
    }

    public static Packet bothReady() {
        return new Packet(PacketType.BOTH_READY, new HashMap<>());
    }

    public static Packet lobbyCreated(String lobbyId, Long gameId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("lobbyId", lobbyId);
        payload.put("gameId", gameId);
        return new Packet(PacketType.LOBBY_CREATED, payload);
    }
}
