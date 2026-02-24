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
     * Load-game lobbies only appear to their expected opponent.
     */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listLobbies(HttpServletRequest request) {
        Long userId = null;
        try {
            userId = extractUserId(request);
        } catch (Exception ignored) {
            // User not authenticated, show only public lobbies
        }

        List<Lobby> lobbies = lobbyService.getAllLobbies();
        final Long currentUserId = userId;
        
        List<Map<String, Object>> lobbyDtos = lobbies.stream()
                .filter(lobby -> {
                    // Hide load-game lobbies from everyone except the expected opponent
                    if (lobby.isLoadGameLobby()) {
                        return currentUserId != null && 
                               lobby.getExpectedOpponentId() != null &&
                               lobby.getExpectedOpponentId().equals(currentUserId);
                    }
                    return true; // Show normal lobbies to everyone
                })
                .map(lobby -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("lobbyId", lobby.getLobbyId());
                    map.put("creatorUsername", lobby.getCreatorUsername());
                    map.put("playerCount", lobby.getPlayerIds().size());
                    map.put("maxPlayers", 2);
                    map.put("status", lobby.getStatus().name());
                    map.put("isFull", lobby.isFull());
                    map.put("isLoadGame", lobby.isLoadGameLobby());
                    if (lobby.isLoadGameLobby()) {
                        map.put("gameId", lobby.getGameId());
                    }
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());
        
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
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("inLobby", true);
            response.put("lobbyId", lobby.getLobbyId());
            response.put("creatorUsername", lobby.getCreatorUsername());
            response.put("playerCount", lobby.getPlayerIds().size());
            response.put("status", lobby.getStatus().name());
            response.put("isLoadGame", lobby.isLoadGameLobby());
            if (lobby.isLoadGameLobby()) {
                response.put("gameId", lobby.getGameId());
            }
            return ResponseEntity.ok(response);
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

    /**
     * Create a lobby to resume a saved game.
     * POST /api/lobby/load-game/{gameId}
     */
    @PostMapping("/load-game/{gameId}")
    public ResponseEntity<?> createLoadGameLobby(
            @PathVariable Long gameId,
            Authentication auth,
            HttpServletRequest request) {
        try {
            Long userId = extractUserId(request);
            String username = auth.getName();
            
            Lobby lobby = lobbyService.createLoadGameLobby(gameId, userId, username);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "lobbyId", lobby.getLobbyId(),
                    "gameId", gameId,
                    "status", lobby.getStatus().name(),
                    "playerCount", lobby.getPlayerIds().size()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
