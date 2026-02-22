package com.example.proyect.auth.api;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
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

        // Create HttpOnly cookie for JWT
        ResponseCookie cookie = ResponseCookie.from("authToken", token)
                .httpOnly(true)
                .secure(false) // Set to true in production with HTTPS
                .path("/")
                .maxAge(3600) // 1 hour (matches JWT expiration)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(
                        user.getUserId(),
                        user.getUsername(),
                        null // No token in response body
                ));
    }       

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        
        User user = userService.login(
                request.username(),
                request.password()
        );

        String token = jwtService.generateToken(user.getUserId(), user.getUsername());
        
        // Create HttpOnly cookie for JWT
        ResponseCookie cookie = ResponseCookie.from("authToken", token)
                .httpOnly(true)
                .secure(false) // Set to true in production with HTTPS
                .path("/")
                .maxAge(3600) // 1 hour (matches JWT expiration)
                .sameSite("Strict")
                .build();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(
                        user.getUserId(),
                        user.getUsername(),
                        null // No token in response body
                ));
    }
        @GetMapping("/ping")
        public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "ok"));
        }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // Clear the authentication cookie
        ResponseCookie cookie = ResponseCookie.from("authToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0) // Expire immediately
                .sameSite("Strict")
                .build();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("message", "Logged out successfully"));
    }
        
     @RestController
        @RequestMapping("/api/users")
                public class UserMeController {
                        @GetMapping("/me")  
                        public ResponseEntity<?> me(@CookieValue(name = "authToken", required = false) String token) {

                        if (token == null || !jwtService.isTokenValid(token)) {
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                        }

                        String username = jwtService.extractUsername(token);

                        return ResponseEntity.ok(Map.of("username", username));
                        }
        //         @GetMapping("/me")
        //         public ResponseEntity<Map<String, Object>> me(Authentication auth) {
        //                 // auth.getName() ← el username que pusiste en el SecurityContext
        //                 return ResponseEntity.ok(Map.of(
        //                 "username", auth.getName()
        //                 ));
                }
}

       
