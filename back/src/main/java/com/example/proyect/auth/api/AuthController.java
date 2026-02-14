package com.example.proyect.auth.api;

//expone http, recibe request del Phaserm, llama al playerservice y devuelve JSON
//Phaser → AuthController → PlayerService → PlayerRepository → DB

import com.example.proyect.auth.AuthResponse;
import com.example.proyect.auth.LoginRequest;
import com.example.proyect.auth.RegisterRequest;
import com.example.proyect.persistencia.player.User;
import com.example.proyect.persistencia.player.UserService;
import org.springframework.web.bind.annotation.*;

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
