package com.example.proyect.websocket.packet;

/**
 * Enum defining all WebSocket message types for the game protocol.
 */
public enum PacketType {
    // Client -> Server (Inbound)
    JOIN("join"),
    SELECT_SIDE("selectSide"),
    MOVE("move"),
    ATTACK("attack"),
    END_TURN("endTurn"),
    SAVE_AND_EXIT("saveAndExit"),
    LOAD_GAME("loadGame"),

    // Server -> Client (Outbound)
    WELCOME("welcome"),
    SIDE_CHOSEN("sideChosen"),
    BOTH_READY("bothReady"),
    GAME_START("gameStart"),
    TURN_START("turnStart"),
    MOVE_DRONE("moveDrone"),
    ATTACK_RESULT("attackResult"),
    PLAYER_LEFT("playerLeft"),
    GAME_SAVED("gameSaved"),
    GAME_LOADED("gameLoaded"),
    ERROR("error");

    private final String value;

    PacketType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse a string to PacketType.
     */
    public static PacketType fromString(String type) {
        for (PacketType pt : values()) {
            if (pt.value.equals(type)) {
                return pt;
            }
        }
        return null;
    }
}
