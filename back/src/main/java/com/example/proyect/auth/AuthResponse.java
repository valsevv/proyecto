package com.example.proyect.auth;

//se usa en el controlador para respuesta

public class AuthResponse {

    public Long userId;
    public String username;
    public String token;

    public AuthResponse(Long userId, String username, String token) {
        this.userId = userId;
        this.username = username;
        this.token = token;
    }
}
