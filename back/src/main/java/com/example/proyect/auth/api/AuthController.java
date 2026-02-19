package com.example.proyect.auth.api;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.proyect.auth.AuthResponse;
import com.example.proyect.auth.LoginRequest;
import com.example.proyect.auth.RegisterRequest;
import com.example.proyect.auth.security.JwtService;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.User;

import jakarta.validation.Valid;


@RestController
//@CrossOrigin(origins = "http://127.0.0.1:3000")
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        User user = userService.register(
                request.username(),
                request.email(),
                request.password()
        );

        String token = jwtService.generateToken(user.getUserId(), user.getUsername());

        return ResponseEntity.ok(
                new AuthResponse(
                        user.getUserId(),
                        user.getUsername(),
                        token
                )
        );
    }       

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        User user = userService.login(
                request.username(),
                request.password()
        );

        String token = jwtService.generateToken(user.getUserId(), user.getUsername());
        
        return ResponseEntity.ok(
                new AuthResponse(
                        user.getUserId(),
                        user.getUsername(),
                        token
                )
            );
    }
}