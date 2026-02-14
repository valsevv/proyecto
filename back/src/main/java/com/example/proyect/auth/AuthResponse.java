package com.example.proyect.auth;

public class AuthResponse {
    public Long playerId;
    public String username;

    public AuthResponse(Long playerId, String username) {
        this.playerId = playerId;
        this.username = username;
    }
}
