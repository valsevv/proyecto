package com.example.proyect.persistencia.player;

import com.example.proyect.auth.security.PasswordHasher;
import org.springframework.stereotype.Service;


//logica de negocio de player (registro, loginn, raking, etc)
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username, String rawPassword) {

        if (userRepository.existsByUsername(username)) {
            throw new IllegalStateException("Username ya existe");
        }

        String hash = PasswordHasher.hash(rawPassword);

        User user = new User(username, hash);
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
