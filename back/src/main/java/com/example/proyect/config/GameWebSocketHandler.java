package com.example.proyect.config;

import com.example.proyect.game.GameRoom;
import com.example.proyect.game.PlayerState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all WebSocket messages for the game.
 *
 * Protocol:
 *   Client → Server:
 *     { "type": "join" }
 *     { "type": "move", "droneIndex": 0, "x": 123.4, "y": 567.8 }
 *
 *   Server → Client:
 *     { "type": "welcome",   "playerId": "...", "playerIndex": 0 }
 *     { "type": "gameStart", "state": { "players": [...] } }
 *     { "type": "moveDrone", "playerIndex": 0, "droneIndex": 0, "x": ..., "y": ... }
 *     { "type": "playerLeft","playerIndex": 0 }
 *     { "type": "error",     "message": "..." }
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final GameRoom room = new GameRoom();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("Client connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        PlayerState removed = room.removePlayer(session.getId());
        if (removed != null) {
            log.info("Player {} (index {}) left", session.getId(), removed.getPlayerIndex());
            // Notify remaining players, then reset the room
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("type", "playerLeft");
            msg.put("playerIndex", removed.getPlayerIndex());
            broadcastSafe(msg);
            room.reset();
        }
    }


    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);
        String type = (String) msg.get("type");

        switch (type) {
            case "join" -> handleJoin(session);
            case "move" -> handleMove(session, msg);
            default     -> sendError(session, "Unknown message type: " + type);
        }
    }


    private void handleJoin(WebSocketSession session) throws IOException {
        PlayerState player = room.addPlayer(session.getId());
        if (player == null) {
            sendError(session, "Game is full");
            return;
        }

        // Confirm to the joining player
        Map<String, Object> welcome = new LinkedHashMap<>();
        welcome.put("type", "welcome");
        welcome.put("playerId", session.getId());
        welcome.put("playerIndex", player.getPlayerIndex());
        sendTo(session, welcome);
        log.info("Player {} joined as index {}", session.getId(), player.getPlayerIndex());

        // When room is full → broadcast gameStart with full state
        if (room.isFull()) {
            Map<String, Object> gameStart = new LinkedHashMap<>();
            gameStart.put("type", "gameStart");
            gameStart.put("state", room.toStateMap());
            broadcast(gameStart);
            log.info("Game started!");
        }
    }

    private void handleMove(WebSocketSession session, Map<String, Object> msg) throws IOException {
        int droneIndex  = ((Number) msg.get("droneIndex")).intValue();
        double x        = ((Number) msg.get("x")).doubleValue();
        double y        = ((Number) msg.get("y")).doubleValue();

        PlayerState player = room.getPlayerBySession(session.getId());
        if (player == null) {
            sendError(session, "You are not in the game");
            return;
        }

        if (!room.moveDrone(session.getId(), droneIndex, x, y)) {
            sendError(session, "Invalid move");
            return;
        }

        // Broadcast confirmed move to every player
        Map<String, Object> moveMsg = new LinkedHashMap<>();
        moveMsg.put("type", "moveDrone");
        moveMsg.put("playerIndex", player.getPlayerIndex());
        moveMsg.put("droneIndex", droneIndex);
        moveMsg.put("x", x);
        moveMsg.put("y", y);
        broadcast(moveMsg);
    }


    private void sendTo(WebSocketSession session, Map<String, Object> msg) throws IOException {
        session.sendMessage(new TextMessage(mapper.writeValueAsString(msg)));
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        sendTo(session, Map.of("type", "error", "message", message));
    }

    private void broadcast(Map<String, Object> msg) throws IOException {
        String json = mapper.writeValueAsString(msg);
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(json));
            }
        }
    }

    /** Same as broadcast but without IOExceptions (used in disconnect handler). */
    private void broadcastSafe(Map<String, Object> msg) {
        try { broadcast(msg); } catch (IOException e) {
            log.error("Error broadcasting message", e);
        }
    }
}
