package com.example.proyect.lobby.api;

import com.example.proyect.lobby.Lobby;
import com.example.proyect.lobby.service.LobbyService;
import com.example.proyect.auth.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/lobby")
public class LobbyController {

    private final LobbyService lobbyService;
    private final JwtService jwtService;

    public LobbyController(LobbyService lobbyService, JwtService jwtService) {
        this.lobbyService = lobbyService;
        this.jwtService = jwtService;
    }

    /**
     * Extract userId from JWT cookie.
     */
    private Long extractUserId(HttpServletRequest request) {
        String token = extractTokenFromCookie(request);
        
        // Fallback to Authorization header if needed
        if (token == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }
        
        if (token != null && jwtService.isTokenValid(token)) {
            return jwtService.extractUserId(token);
        }
        throw new IllegalArgumentException("Invalid authentication");
    }
    
    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("authToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Create a new lobby.
     * POST /api/lobby/create
     */
    @PostMapping("/create")
    public ResponseEntity<?> createLobby(
            Authentication auth,
            HttpServletRequest request) {
        try {
            String username = auth.getName();
            Long userId = extractUserId(request);

            Lobby lobby = lobbyService.createLobby(username, userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "lobbyId", lobby.getLobbyId(),
                    "creatorUsername", lobby.getCreatorUsername(),
                    "status", lobby.getStatus().name(),
                    "playerCount", lobby.getPlayerIds().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get all available lobbies.
     * GET /api/lobby/list
     */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listLobbies() {
        List<Lobby> lobbies = lobbyService.getAllLobbies();
        
        List<Map<String, Object>> lobbyDtos = lobbies.stream()
                .map(lobby -> Map.of(
                        "lobbyId", (Object) lobby.getLobbyId(),
                        "creatorUsername", lobby.getCreatorUsername(),
                        "playerCount", lobby.getPlayerIds().size(),
                        "maxPlayers", 2,
                        "status", lobby.getStatus().name(),
                        "isFull", lobby.isFull()
                ))
                .toList();
        
        return ResponseEntity.ok(lobbyDtos);
    }

    /**
     * Join an existing lobby.
     * POST /api/lobby/join/{lobbyId}
     */
    @PostMapping("/join/{lobbyId}")
    public ResponseEntity<?> joinLobby(
            @PathVariable String lobbyId,
            Authentication auth,
            HttpServletRequest request) {
        try {
            Long userId = extractUserId(request);
            
            Lobby lobby = lobbyService.joinLobby(lobbyId, userId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "lobbyId", lobby.getLobbyId(),
                    "status", lobby.getStatus().name(),
                    "playerCount", lobby.getPlayerIds().size(),
                    "isReady", lobby.isReady()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get current user's lobby (if any).
     * GET /api/lobby/my-lobby
     */
    @GetMapping("/my-lobby")
    public ResponseEntity<?> getMyLobby(HttpServletRequest request) {
        try {
            Long userId = extractUserId(request);
            Optional<Lobby> lobbyOpt = lobbyService.getLobbyByUserId(userId);
            
            if (lobbyOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("inLobby", false));
            }
            
            Lobby lobby = lobbyOpt.get();
            return ResponseEntity.ok(Map.of(
                    "inLobby", true,
                    "lobbyId", lobby.getLobbyId(),
                    "creatorUsername", lobby.getCreatorUsername(),
                    "playerCount", lobby.getPlayerIds().size(),
                    "status", lobby.getStatus().name()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Leave current lobby.
     * POST /api/lobby/leave
     */
    @PostMapping("/leave")
    public ResponseEntity<?> leaveLobby(HttpServletRequest request) {
        try {
            Long userId = extractUserId(request);
            lobbyService.leaveLobby(userId);
            
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
