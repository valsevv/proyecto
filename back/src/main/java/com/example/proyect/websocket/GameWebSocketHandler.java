package com.example.proyect.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.proyect.VOs.GameResult;
import com.example.proyect.controller.GameController;
import com.example.proyect.websocket.packet.Packet;
import com.example.proyect.websocket.packet.PacketSerializer;

/**
 * WebSocket handler.
 *
 * Protocol:
 *   Client → Server:
 *     { "type": "join" }
 *     { "type": "move", "droneIndex": 0, "x": 123.4, "y": 567.8 }
 *     { "type": "attack", "attackerIndex": 0, "targetPlayer": 1, "targetDrone": 0 }
 *     { "type": "endTurn" }
 *
 *   Server → Client:
 *     { "type": "welcome",      "playerId": "...", "playerIndex": 0 }
 *     { "type": "gameStart",    "state": { ... } }
 *     { "type": "turnStart",    "activePlayer": 0, "actionsRemaining": 2 }
 *     { "type": "moveDrone",    "playerIndex": 0, "droneIndex": 0, "x": ..., "y": ... }
 *     { "type": "attackResult", "attackerPlayer": 0, "attackerDrone": 0, "targetPlayer": 1, "targetDrone": 0, "damage": 25, "remainingHealth": 75 }
 *     { "type": "playerLeft",   "playerIndex": 0 }
 *     { "type": "error",        "message": "..." }
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final GameController gameController;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public GameWebSocketHandler(GameController gameController) {
        this.gameController = gameController;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("Client connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        int removedIndex = gameController.removePlayer(session.getId());
        if (removedIndex >= 0) {
            // Broadcast player left to remaining players
            broadcastSafe(Packet.playerLeft(removedIndex));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("[WS] Received from {}: {}", session.getId(), payload);
        
        Packet packet = PacketSerializer.deserialize(payload);
        
        if (packet == null || packet.getType() == null) {
            log.warn("[WS] Failed to parse packet: {}", payload);
            sendError(session, "Invalid message format");
            return;
        }

        log.info("[WS] Parsed packet type: {}", packet.getType());

        switch (packet.getType()) {
            case JOIN        -> handleJoin(session);
            case SELECT_SIDE -> handleSelectSide(session, packet);
            case MOVE        -> handleMove(session, packet);
            case ATTACK      -> handleAttack(session, packet);
            case END_TURN    -> handleEndTurn(session);
            default          -> sendError(session, "Unknown message type");
        }
    }

    private void handleJoin(WebSocketSession session) throws IOException {
        GameResult result = gameController.joinGame(session.getId());
        
        if (!result.isSuccess()) {
            send(session, result.getPacket());
            return;
        }

        // Send welcome to joining player
        send(session, result.getPacket());
        
        // Note: Game will not auto-start anymore
        // Players must select sides first
    }
    
    private void handleSelectSide(WebSocketSession session, Packet packet) throws IOException {
        String side = packet.getString("side");
        
        if (side == null) {
            sendError(session, "Side not specified");
            return;
        }
        
        GameResult result = gameController.selectSide(session.getId(), side);
        
        if (!result.isSuccess()) {
            send(session, result.getPacket());
            return;
        }
        
        // Broadcast side selection to both players in room
        broadcastToRoom(session.getId(), result.getPacket());
        
        // If game is ready (both sides selected), broadcast game start
        if (result.isGameReady()) {
            Packet bothReady = Packet.bothReady();
            broadcastToRoom(session.getId(), bothReady);
            
            Packet gameStart = Packet.gameStart(gameController.getGameState(session.getId()));
            broadcastToRoom(session.getId(), gameStart);
            
            Packet turnStart = Packet.turnStart(
                gameController.getCurrentTurn(session.getId()), 
                gameController.getActionsRemaining(session.getId())
            );
            broadcastToRoom(session.getId(), turnStart);
        }
    }

    private void handleMove(WebSocketSession session, Packet packet) throws IOException {
        int droneIndex = packet.getInt("droneIndex");
        double x = packet.getDouble("x");
        double y = packet.getDouble("y");

        log.info("[WS] handleMove: droneIndex={}, x={}, y={}", droneIndex, x, y);

        GameResult result = gameController.processMove(session.getId(), droneIndex, x, y);
        
        log.info("[WS] Move result: success={}, error={}", result.isSuccess(), result.getErrorMessage());
        
        if (!result.isSuccess()) {
            send(session, result.getPacket());
            return;
        }

        // Broadcast the move to room
        broadcastToRoom(session.getId(), result.getPacket());

        // If turn ended, broadcast new turn to room
        if (result.isTurnEnded()) {
            Packet turnStart = Packet.turnStart(result.getNextPlayer(), result.getActionsRemaining());
            broadcastToRoom(session.getId(), turnStart);
        }
    }

    private void handleAttack(WebSocketSession session, Packet packet) throws IOException {
        int attackerIndex = packet.getInt("attackerIndex");
        int targetPlayer = packet.getInt("targetPlayer");
        int targetDrone = packet.getInt("targetDrone");

        GameResult result = gameController.processAttack(
            session.getId(), attackerIndex, targetPlayer, targetDrone
        );
        
        if (!result.isSuccess()) {
            send(session, result.getPacket());
            return;
        }

        // Broadcast the attack result to room
        broadcastToRoom(session.getId(), result.getPacket());

        // If turn ended, broadcast new turn to room
        if (result.isTurnEnded()) {
            Packet turnStart = Packet.turnStart(result.getNextPlayer(), result.getActionsRemaining());
            broadcastToRoom(session.getId(), turnStart);
        }
    }

    private void handleEndTurn(WebSocketSession session) throws IOException {
        GameResult result = gameController.endTurn(session.getId());
        
        if (!result.isSuccess()) {
            send(session, result.getPacket());
            return;
        }

        // Broadcast turn change to room
        broadcastToRoom(session.getId(), result.getPacket());
    }

    private void send(WebSocketSession session, Packet packet) throws IOException {
        session.sendMessage(new TextMessage(PacketSerializer.serialize(packet)));
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        send(session, Packet.error(message));
    }

    private void broadcast(Packet packet) throws IOException {
        String json = PacketSerializer.serialize(packet);
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(json));
            }
        }
    }

    /**
     * Broadcast a packet only to players in the same room as the given session.
     */
    private void broadcastToRoom(String sessionId, Packet packet) throws IOException {
        java.util.List<String> roomSessions = gameController.getSessionsInSameRoom(sessionId);
        if (roomSessions.isEmpty()) {
            log.warn("No room found for session {}", sessionId);
            return;
        }
        
        String json = PacketSerializer.serialize(packet);
        log.debug("Broadcasting to room ({} sessions): {}", roomSessions.size(), packet.getType());
        
        for (String sid : roomSessions) {
            WebSocketSession s = sessions.get(sid);
            if (s != null && s.isOpen()) {
                s.sendMessage(new TextMessage(json));
            }
        }
    }

    private void broadcastSafe(Packet packet) {
        try {
            broadcast(packet);
        } catch (IOException e) {
            log.error("Error broadcasting message", e);
        }
    }
}
