package com.example.proyect.auth.service;

import com.example.proyect.auth.security.PasswordHasher;
import com.example.proyect.persistencia.player.User;
import com.example.proyect.persistencia.player.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // =========================
    // REGISTER
    // =========================
    public User register(String username, String rawPassword) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username requerido");
        }

        if (rawPassword == null || rawPassword.length() < 4) {
            throw new IllegalArgumentException("Password demasiado corto");
        }

        if (userRepository.existsByUsername(username)) {
            throw new IllegalStateException("Username ya existe");
        }

        String hashedPassword = PasswordHasher.hash(rawPassword);

        User user = new User(username, hashedPassword);
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