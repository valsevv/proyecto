package com.example.proyect.controller;

import com.example.proyect.VOs.GameResult;
import com.example.proyect.game.DroneState;
import com.example.proyect.game.GameRoom;
import com.example.proyect.game.PlayerState;
import com.example.proyect.websocket.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Game service layer that handles all game logic orchestration.
 * Separates business logic from WebSocket handling.
 */
@Service
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private final GameRoom room;

    public GameController(GameRoom room) {
        this.room = room;
    }

    /**
     * Join a player to the game room.
     */
    public GameResult joinGame(String sessionId) {
        PlayerState player = room.addPlayer(sessionId);
        
        if (player == null) {
            log.warn("Player {} tried to join but game is full", sessionId);
            return GameResult.error("Game is full");
        }

        log.info("Player {} joined as index {}", sessionId, player.getPlayerIndex());
        
        Packet welcome = Packet.welcome(sessionId, player.getPlayerIndex());
        
        // Check if game should start
        if (room.isFull()) {
            room.startGame();
            log.info("Game started! Player 0's turn");
            return GameResult.gameReady(welcome);
        }
        
        return GameResult.ok(welcome);
    }

    /**
     * Remove a player from the game.
     */
    public int removePlayer(String sessionId) {
        PlayerState removed = room.removePlayer(sessionId);
        if (removed != null) {
            log.info("Player {} (index {}) left", sessionId, removed.getPlayerIndex());
            room.reset();
            return removed.getPlayerIndex();
        }
        return -1;
    }


    /**
     * Process a move action.
     */
    public GameResult processMove(String sessionId, int droneIndex, double x, double y) {
        PlayerState player = room.getPlayerBySession(sessionId);
        
        if (player == null) {
            return GameResult.error("You are not in the game");
        }

        if (!room.isPlayerTurn(sessionId)) {
            return GameResult.error("Not your turn");
        }

        DroneState drone = room.getDrone(player.getPlayerIndex(), droneIndex);
        if (drone == null) {
            return GameResult.error("Invalid drone index");
        }

        if (!drone.isAlive()) {
            return GameResult.error("Cannot move a destroyed drone");
        }

        // Apply move
        if (!room.moveDrone(sessionId, droneIndex, x, y)) {
            return GameResult.error("Invalid move");
        }

        // Consume action
        room.useAction();
        
        log.debug("Player {} moved drone {} to ({}, {})", 
            player.getPlayerIndex(), droneIndex, x, y);

        Packet movePacket = Packet.moveDrone(player.getPlayerIndex(), droneIndex, x, y);
        
        // Check if turn should auto-end
        if (room.getActionsRemaining() <= 0) {
            room.endTurn();
            return GameResult.turnEnded(movePacket, room.getCurrentTurn(), room.getActionsRemaining());
        }
        
        return GameResult.withActionsRemaining(movePacket, room.getActionsRemaining());
    }

    /**
     * Process an attack action.
     */
    public GameResult processAttack(String sessionId, int attackerIndex, 
                                     int targetPlayerIndex, int targetDroneIndex) {
        PlayerState attacker = room.getPlayerBySession(sessionId);
        
        if (attacker == null) {
            return GameResult.error("You are not in the game");
        }

        if (!room.isPlayerTurn(sessionId)) {
            return GameResult.error("Not your turn");
        }

        // Can't attack yourself
        if (attacker.getPlayerIndex() == targetPlayerIndex) {
            return GameResult.error("Cannot attack your own drones");
        }

        DroneState attackerDrone = room.getDrone(attacker.getPlayerIndex(), attackerIndex);
        DroneState targetDrone = room.getDrone(targetPlayerIndex, targetDroneIndex);

        if (attackerDrone == null) {
            return GameResult.error("Invalid attacker drone index");
        }
        if (targetDrone == null) {
            return GameResult.error("Invalid target drone index");
        }

        if (!attackerDrone.isAlive()) {
            return GameResult.error("Cannot attack with a destroyed drone");
        }
        if (!targetDrone.isAlive()) {
            return GameResult.error("Target drone is already destroyed");
        }

        // TODO: Add range validation here using hex distance
        // For now, all attacks are valid if drones exist

        // Apply damage
        int damage = attackerDrone.getAttackDamage();
        targetDrone.takeDamage(damage);

        // Consume action
        room.useAction();

        log.info("Player {} drone {} attacked player {} drone {} for {} damage (remaining HP: {})",
            attacker.getPlayerIndex(), attackerIndex, 
            targetPlayerIndex, targetDroneIndex,
            damage, targetDrone.getHealth());

        Packet attackPacket = Packet.attackResult(
            attacker.getPlayerIndex(), attackerIndex,
            targetPlayerIndex, targetDroneIndex,
            damage, targetDrone.getHealth()
        );

        // Check for game over
        if (isPlayerDefeated(targetPlayerIndex)) {
            log.info("Player {} has been defeated!", targetPlayerIndex);
            // Could return a game over result here
        }

        // Check if turn should auto-end
        if (room.getActionsRemaining() <= 0) {
            room.endTurn();
            return GameResult.turnEnded(attackPacket, room.getCurrentTurn(), room.getActionsRemaining());
        }

        return GameResult.withActionsRemaining(attackPacket, room.getActionsRemaining());
    }

    /**
     * End the current player's turn early.
     * @return Result with turnStart info or error
     */
    public GameResult endTurn(String sessionId) {
        if (!room.isPlayerTurn(sessionId)) {
            return GameResult.error("Not your turn");
        }

        room.endTurn();
        
        log.info("Turn ended. Now player {}'s turn with {} actions",
            room.getCurrentTurn(), room.getActionsRemaining());

        return GameResult.turnStarted(room.getCurrentTurn(), room.getActionsRemaining());
    }

    public Map<String, Object> getGameState() {
        return room.toStateMap();
    }

    public int getCurrentTurn() {
        return room.getCurrentTurn();
    }

    public int getActionsRemaining() {
        return room.getActionsRemaining();
    }

    public boolean isGameStarted() {
        return room.isGameStarted();
    }

    private boolean isPlayerDefeated(int playerIndex) {
        PlayerState player = room.getPlayerByIndex(playerIndex);
        if (player == null) return false;
        
        for (DroneState drone : player.getDrones()) {
            if (drone.isAlive()) return false;
        }
        return true;
    }
}
