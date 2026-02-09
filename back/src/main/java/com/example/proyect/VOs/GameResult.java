package com.example.proyect.VOs;

import com.example.proyect.websocket.packet.Packet;

/**
 * Value Object: Result wrapper for game operations.
 * Contains the response packet and metadata about what happened.
 */
public class GameResult {
    private final boolean success;
    private final Packet packet;
    private final String errorMessage;
    private final boolean gameReady;
    private final boolean turnEnded;
    private final int nextPlayer;
    private final int actionsRemaining;

    private GameResult(boolean success, Packet packet, String errorMessage, 
                      boolean gameReady, boolean turnEnded, int nextPlayer, int actionsRemaining) {
        this.success = success;
        this.packet = packet;
        this.errorMessage = errorMessage;
        this.gameReady = gameReady;
        this.turnEnded = turnEnded;
        this.nextPlayer = nextPlayer;
        this.actionsRemaining = actionsRemaining;
    }

    public static GameResult ok(Packet packet) {
        return new GameResult(true, packet, null, false, false, -1, -1);
    }

    public static GameResult error(String message) {
        return new GameResult(false, Packet.error(message), message, false, false, -1, -1);
    }

    public static GameResult gameReady(Packet welcomePacket) {
        return new GameResult(true, welcomePacket, null, true, false, -1, -1);
    }

    public static GameResult withActionsRemaining(Packet packet, int actionsRemaining) {
        return new GameResult(true, packet, null, false, false, -1, actionsRemaining);
    }

    public static GameResult turnEnded(Packet packet, int nextPlayer, int actionsRemaining) {
        return new GameResult(true, packet, null, false, true, nextPlayer, actionsRemaining);
    }

    public static GameResult turnStarted(int activePlayer, int actionsRemaining) {
        Packet packet = Packet.turnStart(activePlayer, actionsRemaining);
        return new GameResult(true, packet, null, false, true, activePlayer, actionsRemaining);
    }

    public boolean isSuccess() { return success; }
    public Packet getPacket() { return packet; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isGameReady() { return gameReady; }
    public boolean isTurnEnded() { return turnEnded; }
    public int getNextPlayer() { return nextPlayer; }
    public int getActionsRemaining() { return actionsRemaining; }
}
