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

//vseverio Controlador REST que maneja autenticacion en el backend /api/auth y define un endpoint de usuario actual en una clase interna /api/users/me

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
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) { //POST recibe username, email, password, llama a registro, guarda token y devuelve authResponse
        User user = userService.register(
                request.username(),
                request.email(),
                request.password()
        );

        String token = jwtService.generateToken(user.getUserId(), user.getUsername());


        ResponseCookie cookie = ResponseCookie.from("authToken", token)
                .httpOnly(true)
                .secure(true) // Required for HTTPS (ngrok)
                .path("/")
                .maxAge(3600) // 1 hora de duracion
                .sameSite("None") // Required for cross-origin cookies
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(
                        user.getUserId(),
                        user.getUsername(),
                        null // No hay token en el body de respuesta
                ));
    }       

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) { //recibe username y password, llama a userService.login para valdiar credenciales, si son correctas lo mete en cookie autthoken y devuelve authresponse.
        
        User user = userService.login(
                request.username(),
                request.password()
        );

        String token = jwtService.generateToken(user.getUserId(), user.getUsername());

        ResponseCookie cookie = ResponseCookie.from("authToken", token)
                .httpOnly(true)
                .secure(true) // Required for HTTPS (ngrok)
                .path("/")
                .maxAge(3600) // 1 hora de duracion
                .sameSite("None") // Required for cross-origin cookies
                .build();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(
                        user.getUserId(),
                        user.getUsername(),
                        null
                ));
    }
        @GetMapping("/ping") //pingea para revisar conexion
        public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "ok"));
        }

    @PostMapping("/logout") //borra la sesion seteando cookie autToken vacia. envia mensaje de respuesta
    public ResponseEntity<Map<String, String>> logout() {
        ResponseCookie cookie = ResponseCookie.from("authToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0) // Expire immediately
                .sameSite("None")
                .build();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("message", "Logged out successfully"));
    }
        
     @RestController // clase interna que lee cookie de authToken.
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

       
