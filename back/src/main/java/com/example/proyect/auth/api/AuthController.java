package com.example.proyect.auth.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.proyect.auth.AuthResponse;
import com.example.proyect.auth.LoginRequest;
import com.example.proyect.auth.RegisterRequest;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.User;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {

        User user = userService.register(request.username, request.password);

        return new AuthResponse(user.getId(), user.getUsername());
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {

        User user = userService.login(request.username, request.password);

        return new AuthResponse(user.getId(), user.getUsername());
    }
}
