package com.example.proyect.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.proyect.VOs.GameResult;
import com.example.proyect.auth.service.GameService;
import com.example.proyect.game.GameRoom;
import com.example.proyect.game.PlayerState;
import com.example.proyect.game.units.drone.Drone;
import com.example.proyect.lobby.Lobby;
import com.example.proyect.lobby.service.LobbyService;
import com.example.proyect.persistence.classes.Game;
import com.example.proyect.persistence.classes.GameStatus;
import com.example.proyect.websocket.packet.Packet;

/**
 * Game service layer that handles all game logic orchestration.
 * Manages multiple GameRoom instances for concurrent 1v1 matches.
 */
@Service
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private final LobbyService lobbyService;

    private final GameService gameService;
    // All game rooms by room ID
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    
    // Track which room each session is in
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();
    
    // Track userId for each session
    private final Map<String, Long> sessionToUserId = new ConcurrentHashMap<>();
    
    //GamesId de la session
    private final Map<Long, Game> games = new ConcurrentHashMap<>();    

    
    // Sequential room ID counter
    private final AtomicInteger roomCounter = new AtomicInteger(1);

    public GameController(LobbyService lobbyService, GameService gameService) {
        this.lobbyService = lobbyService;
        this.gameService = gameService;
    }

    /**
     * Create a GameRoom from a lobby when both players are ready.
     */
    private GameRoom createRoomFromLobby(Lobby lobby) { //aca no usa el lobby para nada
        String roomId = "room-" + roomCounter.getAndIncrement();
        GameRoom newRoom = new GameRoom(roomId);
        rooms.put(roomId, newRoom);
        log.info("Created game room {} from lobby {}", roomId, lobby.getLobbyId());
        return newRoom;
    }

    private void  createGameFromLobby(Lobby lobby) {
        List <Long> playersId = lobby.getPlayerIds();

        Game newGame = gameService.createGame(playersId.get(0), playersId.get(1));
        Long gameId = newGame.getId();
        games.put(gameId, newGame);
        log.info("Created game  {} from lobby {}", gameId, lobby.getLobbyId());
    }

    /**
     * Get the room for a given session, or null if not in any room.
     */
    private GameRoom getRoomForSession(String sessionId) {
        String roomId = sessionToRoom.get(sessionId);
        if (roomId == null) return null;
        return rooms.get(roomId);
    }

    /**
     * Remove empty rooms to free resources.
     */
    private void cleanupEmptyRooms() {
        rooms.entrySet().removeIf(entry -> {
            GameRoom room = entry.getValue();
            if (room.getPlayers().isEmpty()) {
                log.info("Removing empty room: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Join a player to a game room from their lobby.
     * The lobby must exist and the user must be a member.
     */
    public GameResult joinGame(String sessionId, String lobbyId, Long userId) {
        // Check if already in a room
        if (sessionToRoom.containsKey(sessionId)) {
            return GameResult.error("Already in a game");
        }

        // Validate lobby exists
        Lobby lobby = lobbyService.getLobbyById(lobbyId)
                .orElse(null);
       

        if (lobby == null) {
            return GameResult.error("Lobby not found");
        }

        // Validate user is in this lobby
        if (!lobby.getPlayerIds().contains(userId)) {
            return GameResult.error("You are not in this lobby");
        }

        // Store userId mapping
        sessionToUserId.put(sessionId, userId);

        // Find or create game room for this lobby
        GameRoom room = findRoomForLobby(lobbyId);
        if (room == null) {
            room = createRoomFromLobby(lobby);
            // Mark that this room is for this lobby (we'll use the room ID)
        }

        PlayerState player = room.addPlayer(sessionId);
        
        if (player == null) {
            log.warn("Player {} tried to join but room {} is full", sessionId, room.getRoomId());
            return GameResult.error("Game is full");
        }

        // Track which room this session is in
        sessionToRoom.put(sessionId, room.getRoomId());

        log.info("Player {} (userId {}) joined room {} from lobby {} as index {}", 
                sessionId, userId, room.getRoomId(), lobbyId, player.getPlayerIndex());
        
        Packet welcome = Packet.welcome(sessionId, player.getPlayerIndex());
        
        // Mark lobby as started when both players are in the game room
        if (room.isFull()) {
            lobby.markStarted();
            log.info("Lobby {} marked as STARTED", lobbyId);
        }

        //Si la partida no existe se crea
        List <Long> playersId = lobby.getPlayerIds();
        if (gameService.existsGameBetweenUsers(playersId.get(0), playersId.get(1))){
            createGameFromLobby(lobby);
        }

        return GameResult.ok(welcome);
    }

    /**
     * Find existing game room for a lobby (by checking if any room has players from this lobby).
     * This is a simplified approach - in production you'd want a direct lobby→room mapping.
     */
    private GameRoom findRoomForLobby(String lobbyId) {
        // For simplicity, we'll check if there's a room with space that was just created
        // A better approach would be to maintain a lobbyId → roomId mapping
        for (GameRoom room : rooms.values()) {
            if (!room.isFull() && !room.isGameStarted()) {
                // Check if any player in this room is from the lobby  
                // Since lobby members should join sequentially, we'll just return the first available room
                return room;
            }
        }
        return null;
    }
    
    /**
     * Handle player side selection.
     */
    public GameResult selectSide(String sessionId, String side) {
        // Validate side
        if (!"Naval".equals(side) && !"Aereo".equals(side)) {
            return GameResult.error("Invalid side. Must be 'Naval' or 'Aereo'");
        }
        
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) {
            return GameResult.error("You are not in a game room");
        }
        
        PlayerState player = room.getPlayerBySession(sessionId);
        if (player == null) {
            return GameResult.error("You are not in the game");
        }
        
        int playerIndex = player.getPlayerIndex();
        
        // Check if this player already selected a side
        if (room.getPlayerSide(playerIndex) != null) {
            return GameResult.error("You already selected a side");
        }
        
        // For first player, allow any side
        // For second player, assign opposite side automatically in the handler
        room.setPlayerSide(playerIndex, side);
        room.createDronesForSide(playerIndex, side);
        
        log.info("Player {} selected side {} in room {}", playerIndex, side, room.getRoomId());
        
        // Check if both players have selected
        if (room.bothSidesSelected() && room.isFull()) {
            room.startGame();
            log.info("Both sides selected! Game started in room {}. Player 0's turn", room.getRoomId());
            return GameResult.gameReady(Packet.sideChosen(playerIndex, side));
        }
        
        return GameResult.ok(Packet.sideChosen(playerIndex, side));
    }

    /**
     * Remove a player from their game room.
     */
    public int removePlayer(String sessionId) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) {
            return -1;
        }

        PlayerState removed = room.removePlayer(sessionId);
        if (removed != null) {
            log.info("Player {} (index {}) left room {}", sessionId, removed.getPlayerIndex(), room.getRoomId());
            sessionToRoom.remove(sessionId);
            sessionToUserId.remove(sessionId);
            room.reset();
            
            // Cleanup empty rooms
            cleanupEmptyRooms();
            
            return removed.getPlayerIndex();
        }
        return -1;
    }

    /**
     * Save current room state to DB and close the game room for all participants.
     */
    public GameResult saveAndExit(String sessionId) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) {
            return GameResult.error("You are not in a game room");
        }

        PlayerState actor = room.getPlayerBySession(sessionId);
        if (actor == null) {
            return GameResult.error("You are not in the game");
        }

        PlayerState p0 = room.getPlayerByIndex(0);
        PlayerState p1 = room.getPlayerByIndex(1);
        if (p0 == null || p1 == null) {
            return GameResult.error("Cannot save an incomplete game");
        }

        
        Long player1Id = sessionToUserId.get(p0.getSessionId());
        Long player2Id = sessionToUserId.get(p1.getSessionId());
        if (player1Id == null || player2Id == null) {
            return GameResult.error("Cannot resolve players for persistence");
        }
         

        com.example.proyect.persistence.classes.GameState persistedState =
                new com.example.proyect.persistence.classes.GameState();

        persistedState.setStatus(GameStatus.FINISHED);
        persistedState.setTurn(room.getCurrentTurn());
        
        //La meta dato en que va a ir guardada en jsonb
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("roomId", room.getRoomId());
        meta.put("savedByPlayerIndex", actor.getPlayerIndex());
        meta.put("savedByUserId", sessionToUserId.get(sessionId));
        meta.put("snapshot", room.toStateMap()); //esta es la partida en si
        persistedState.setMeta(meta);
        
       if (!gameService.existsGameBetweenUsers(player1Id, player2Id)){

            return GameResult.error("The game doesnt exist");
        }     
        
        Game gameToSave = gameService.getById(player2Id);  
        //Agarro el Game que ya existe y le piso la informacion de la partida que seria el GameRoom en el Meta   snapshot   
        gameToSave.setState(persistedState);
        gameToSave = gameService.saveGame(player1Id, player2Id, gameToSave);

            
        String roomId = room.getRoomId();
        java.util.List<String> sessionsInRoom = getSessionsInSameRoom(sessionId);
        sessionToRoom.entrySet().removeIf(entry -> roomId.equals(entry.getValue()));
        for (String sid : sessionsInRoom) {
            sessionToUserId.remove(sid);
        }
        room.reset();
        cleanupEmptyRooms();

        log.info("Game room {} saved and closed by player {}. Persisted gameId={}", roomId, actor.getPlayerIndex(), gameToSave.getId());

        return GameResult.ok(Packet.gameSaved(gameToSave.getId(), actor.getPlayerIndex()));
    }

    /**
     * Process a move action.
     */
    public GameResult processMove(String sessionId, int droneIndex, double x, double y) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) {
            return GameResult.error("You are not in a game room");
        }

        PlayerState player = room.getPlayerBySession(sessionId);
        
        if (player == null) {
            return GameResult.error("You are not in the game");
        }

        if (!room.isPlayerTurn(sessionId)) {
            return GameResult.error("Not your turn");
        }

        Drone drone = room.getDrone(player.getPlayerIndex(), droneIndex);
        if (drone == null) {
            return GameResult.error("Invalid drone index");
        }

        if (!drone.isAlive()) {
            return GameResult.error("Cannot move a destroyed drone");
        }

        // Apply move
        if (!room.moveDrone(sessionId, droneIndex, x, y)) {
            return GameResult.error("Invalid move");
        }

        // Consume action
        room.useAction();
        
        log.debug("Player {} moved drone {} to ({}, {}) in room {}", 
            player.getPlayerIndex(), droneIndex, x, y, room.getRoomId());

        Packet movePacket = Packet.moveDrone(player.getPlayerIndex(), droneIndex, x, y);
        
        // Check if turn should auto-end
        if (room.getActionsRemaining() <= 0) {
            room.endTurn();
            return GameResult.turnEnded(movePacket, room.getCurrentTurn(), room.getActionsRemaining());
        }
        
        return GameResult.withActionsRemaining(movePacket, room.getActionsRemaining());
    }

    /**
     * Process an attack action.
     */
    public GameResult processAttack(String sessionId, int attackerIndex, 
                                     int targetPlayerIndex, int targetDroneIndex) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) {
            return GameResult.error("You are not in a game room");
        }

        PlayerState attacker = room.getPlayerBySession(sessionId);
        
        if (attacker == null) {
            return GameResult.error("You are not in the game");
        }

        if (!room.isPlayerTurn(sessionId)) {
            return GameResult.error("Not your turn");
        }

        // Can't attack yourself
        if (attacker.getPlayerIndex() == targetPlayerIndex) {
            return GameResult.error("Cannot attack your own drones");
        }

        Drone attackerDrone = room.getDrone(attacker.getPlayerIndex(), attackerIndex);
        Drone targetDrone = room.getDrone(targetPlayerIndex, targetDroneIndex);

        if (attackerDrone == null) {
            return GameResult.error("Invalid attacker drone index");
        }
        if (targetDrone == null) {
            return GameResult.error("Invalid target drone index");
        }

        if (!attackerDrone.isAlive()) {
            return GameResult.error("Cannot attack with a destroyed drone");
        }
        if (!targetDrone.isAlive()) {
            return GameResult.error("Target drone is already destroyed");
        }

        // TODO: Add range validation here using hex distance
        // For now, all attacks are valid if drones exist

        // Apply damage
        int damage = attackerDrone.getWeapon().getDamage();
        targetDrone.receiveDamage(damage);

        // Consume action
        room.useAction();

        log.info("Player {} drone {} attacked player {} drone {} for {} damage (remaining HP: {}) in room {}",
            attacker.getPlayerIndex(), attackerIndex, 
            targetPlayerIndex, targetDroneIndex,
            damage, targetDrone.getCurrentHp(), room.getRoomId());

        Packet attackPacket = Packet.attackResult(
            attacker.getPlayerIndex(), attackerIndex,
            targetPlayerIndex, targetDroneIndex,
            damage, targetDrone.getCurrentHp()
        );

        // Check for game over
        if (isPlayerDefeated(room, targetPlayerIndex)) {
            log.info("Player {} has been defeated in room {}!", targetPlayerIndex, room.getRoomId());
            // Could return a game over result here
        }

        // Check if turn should auto-end
        if (room.getActionsRemaining() <= 0) {
            room.endTurn();
            return GameResult.turnEnded(attackPacket, room.getCurrentTurn(), room.getActionsRemaining());
        }

        return GameResult.withActionsRemaining(attackPacket, room.getActionsRemaining());
    }

    /**
     * End the current player's turn early.
     * @return Result with turnStart info or error
     */
    public GameResult endTurn(String sessionId) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) {
            return GameResult.error("You are not in a game room");
        }

        if (!room.isPlayerTurn(sessionId)) {
            return GameResult.error("Not your turn");
        }

        room.endTurn();
        
        log.info("Turn ended in room {}. Now player {}'s turn with {} actions",
            room.getRoomId(), room.getCurrentTurn(), room.getActionsRemaining());

        return GameResult.turnStarted(room.getCurrentTurn(), room.getActionsRemaining());
    }

    public Map<String, Object> getGameState(String sessionId) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) return Map.of();
        return room.toStateMap();
    }

    public int getCurrentTurn(String sessionId) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) return 0;
        return room.getCurrentTurn();
    }

    public int getActionsRemaining(String sessionId) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) return 0;
        return room.getActionsRemaining();
    }

    public boolean isGameStarted(String sessionId) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) return false;
        return room.isGameStarted();
    }

    private boolean isPlayerDefeated(GameRoom room, int playerIndex) {
        PlayerState player = room.getPlayerByIndex(playerIndex);
        if (player == null) return false;
        
        for (Drone drone : player.getDrones()) {
            if (drone.isAlive()) return false;
        }
        return true;
    }

    /**
     * Get the room ID for a session (useful for debugging/admin).
     */
    public String getRoomId(String sessionId) {
        return sessionToRoom.get(sessionId);
    }

    /**
     * Get all session IDs in the same room as the given session.
     */
    public java.util.List<String> getSessionsInSameRoom(String sessionId) {
        String roomId = sessionToRoom.get(sessionId);
        if (roomId == null) return java.util.List.of();
        
        return sessionToRoom.entrySet().stream()
            .filter(entry -> roomId.equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get total number of active rooms.
     */
    public int getActiveRoomCount() {
        return rooms.size();
    }
}
