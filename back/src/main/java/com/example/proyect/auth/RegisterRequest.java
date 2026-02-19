package com.example.proyect.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Username es requerido")
        @Size(min = 3, max = 20, message = "El nombre de usuario debe tener entre 3 y 20 caracteres")
        String username,

        @Email(message = "Email must be valid")
        @NotBlank(message = "Email es requerido")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password debe tener al menos 8 caracteres")
        String password
) {}