package com.example.proyect.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Nombre de usuario requerido")
        String username,

        @NotBlank(message = "Password es requerida")
        String password
) {}

