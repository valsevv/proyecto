package com.example.proyect.persistence.classes;

import java.util.Map;

public class GameState {
    private GameStatus status;        // "IN_PROGRESS", "FINISHED"
    private Integer turn;            // turno actual (1 y 2 indicando el jugador)
    private Map<String, Object> meta;// datos extensibles Toda esta Metadata va a ir formateada en jsonb a la base, en el campo state de Game

    public GameState() {}

    public GameState(GameStatus status, Integer turn, Map<String, Object> meta) {
        this.status = status;
        this.turn = turn;
        this.meta = meta;
    } 

    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus status) { this.status = status; }

    public Integer getTurn() { return turn; }
    public void setTurn(Integer turn) { this.turn = turn; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
}