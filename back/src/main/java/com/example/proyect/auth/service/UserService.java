package com.example.proyect.auth.service;

import org.springframework.stereotype.Service;

import com.example.proyect.auth.Exceptions.EmailAlreadyExistsException;
import com.example.proyect.auth.Exceptions.InvalidCredentialsException;
import com.example.proyect.auth.Exceptions.UserAlreadyExistsException;
import com.example.proyect.auth.security.PasswordHasher;
import com.example.proyect.persistence.classes.User;
import com.example.proyect.persistence.repos.UserRepository;
import jakarta.persistence.EntityNotFoundException;

//vseverio Esta clase maneja alta,login y busqueda de usuario con validacion de negocio

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String email, String rawPassword) { //valida duplicados, hashea y crea usuario en BD

        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("El usuario ya existe");
        }

        if (userRepository.existsByEmail(email.toLowerCase())) {
            throw new EmailAlreadyExistsException("Email ya existe");
        }
        if (username == null || username.isBlank()) {
            throw new InvalidCredentialsException("Username requerido");
        }

        if (rawPassword == null || rawPassword.length() < 4) {
            throw new InvalidCredentialsException("Password demasiado corto");
        }

        if (email == null || email.length() < 8) {
            throw new InvalidCredentialsException("Email demasiado corto");
        }

        String hashedPassword = PasswordHasher.hash(rawPassword);
 
        User user = new User(username, email, hashedPassword);
        return userRepository.save(user);
    }

    public User login(String username, String rawPassword) { //busca usuario por username, valida contraseña y actualiza fecha de ltimo login

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales inválidas"));

        if (!PasswordHasher.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciales inválidas");
        }

        user.markLoginNow();
        return userRepository.save(user);
    }

    public User getByUsername(String username) { //obtiene usuario por nombre y lanza exception si no existe
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + username));
    }

    public User getById(Long userId) { //obtieenr usuario por ID y lanza excepcion si no existe
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con id=" + userId));
    }
}

