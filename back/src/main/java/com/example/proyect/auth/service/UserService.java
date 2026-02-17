package com.example.proyect.auth.service;

import org.springframework.stereotype.Service;

import com.example.proyect.auth.security.PasswordHasher;
import com.example.proyect.persistence.classes.User;
import com.example.proyect.persistence.repos.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String email, String rawPassword) {

        if (userRepository.existsByUsername(username)) {
            throw new IllegalStateException("Username ya existe");
        }

        if (userRepository.existsByEmail(email.toLowerCase())) {
            throw new IllegalStateException("Email ya existe");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username requerido");
        }

        if (rawPassword == null || rawPassword.length() < 4) {
            throw new IllegalArgumentException("Password demasiado corto");
        }

        if (email == null || email.length() < 8) {
            throw new IllegalArgumentException("Email demasiado corto");
        }

        String hashedPassword = PasswordHasher.hash(rawPassword);
 
        User user = new User(username, email, hashedPassword);
        return userRepository.save(user);
    }

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

