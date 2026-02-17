//este ya no se usa, ahora se usa UserService

package com.example.proyect.auth.service;

import org.springframework.stereotype.Service;

import com.example.proyect.auth.security.PasswordHasher;
import com.example.proyect.persistence.classes.User;
import com.example.proyect.persistence.repos.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // =========================
    // REGISTER
    // =========================
    public User register(String username, String email, String rawPassword) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username requerido");
        }

        if (rawPassword == null || rawPassword.length() < 4) {
            throw new IllegalArgumentException("Password demasiado corto");
        }

        if (email == null || email.length() < 8) {
            throw new IllegalArgumentException("Email demasiado corto");
        }

        if (userRepository.existsByUsername(username)) {
            throw new IllegalStateException("Username ya existe");
        }

        String hashedPassword = PasswordHasher.hash(rawPassword);

        User user = new User(username, email, hashedPassword);
        return userRepository.save(user);
    }

    // =========================
    // LOGIN
    // =========================
    public User login(String username, String rawPassword) {

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

        if (!PasswordHasher.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        user.markLoginNow();

        return userRepository.save(user);
    }
}