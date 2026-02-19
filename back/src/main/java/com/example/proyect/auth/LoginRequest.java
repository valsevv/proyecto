package com.example.proyect.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Nombre de usuario requerido")
        @Size(min = 3, max = 20, message = "El nombre de usuario debe tener entre 3 y 20 caracteres")
        String username,

        @NotBlank(message = "Password es requerida")
        @Size(min = 8, message = "Password debe tener al menos 8 caracteres")
        String password
) {}

 