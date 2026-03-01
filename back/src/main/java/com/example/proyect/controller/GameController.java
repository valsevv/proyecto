package com.example.proyect.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.proyect.VOs.GameResult;
import com.example.proyect.auth.service.GameService;
import com.example.proyect.game.GameRoom;
import com.example.proyect.game.PlayerState;
import com.example.proyect.game.units.Unit.HexCoord;
import com.example.proyect.game.units.drone.AerialDrone;
import com.example.proyect.game.units.drone.Drone;
import com.example.proyect.game.units.drone.NavalDrone;
import com.example.proyect.game.units.weapons.MissileWeapon;
import com.example.proyect.lobby.Lobby;
import com.example.proyect.lobby.service.LobbyService;
import com.example.proyect.persistence.classes.Game;
import com.example.proyect.persistence.classes.GameState;
import com.example.proyect.persistence.classes.GameStatus;
import com.example.proyect.websocket.packet.Packet;

/**
 * Game service layer that handles all game logic orchestration.
 * Manages multiple GameRoom instances for concurrent 1v1 matches.
 */
@Service
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);
    private static final int DEFAULT_ATTACK_ACTION_COST = 1;
    private static final int NAVAL_ATTACK_ACTION_COST = 2;
    private static final double NAVAL_ATTACK_VERTICAL_OFFSET = 90.0;

    @Value("${game.actions-per-turn:10}")
    private int actionsPerTurn;

    @Value("${game.vision-range-aereo:4}")
    private int aerialVisionRange;

    @Value("${game.vision-range-naval:3}")
    private int navalVisionRange;

    @Value("${game.missile.max-distance:15}")
    private int missileMaxDistance;

    @Value("${game.missile.damage-percent-on-naval:0.5}")
    private double missileDamagePercentOnNaval;

    // Must match frontend HexGrid size (front/scenes/MainScene.js -> new HexGrid(this, 35, ...))
    private static final double HEX_SIZE_PX = 35.0;
    private static final double HEX_WIDTH_PX = Math.sqrt(3.0) * HEX_SIZE_PX;

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

    // Loaded games tracking (gameId -> roomId)
    private final Map<Long, String> gameToRoom = new ConcurrentHashMap<>();
    private final Map<String, Long> roomToGame = new ConcurrentHashMap<>();
    private final Map<Long, Lock> gameLocks = new ConcurrentHashMap<>();

    // Sequential room ID counter
    private final AtomicInteger roomCounter = new AtomicInteger(1);

    public GameController(LobbyService lobbyService, GameService gameService) {
        this.lobbyService = lobbyService;
        this.gameService = gameService;
    }
    public void bindSessionUser(String sessionId, Long userId) {
        if (sessionId != null && userId != null) {
            sessionToUserId.put(sessionId, userId);
        }
    }
    public Long getUserIdBySession(String sessionId) {
        return sessionToUserId.get(sessionId);
    }
    

    //Crea el froom apartir del lobby, si ya existe lo retorna
    private GameRoom findOrCreateRoomForLobby(Lobby lobby) {

        return rooms.computeIfAbsent(
            lobby.getLobbyId(),
            id -> {
                GameRoom newRoom = new GameRoom(id, actionsPerTurn, aerialVisionRange, navalVisionRange);
                log.info("Created game room {} from lobby {}", id, lobby.getLobbyId());
                return newRoom;
            }
        );
    }


    private Game createGameFromLobby(Lobby lobby) {

        String lobbyId = lobby.getLobbyId();

        // Si ya hay un game para esta room/lobby, no crear otro
        if (roomToGame.containsKey(lobbyId)) {
            Long existingGameId = roomToGame.get(lobbyId);
            return games.get(existingGameId);
        }

        List<Long> playersId = lobby.getPlayerIds();

        if (playersId.size() != 2) {
            throw new IllegalStateException("Cannot create game: lobby does not have 2 players");
        }

        Game newGame = gameService.createGame(playersId.get(0), playersId.get(1));
        Long gameId = newGame.getId();

        games.put(gameId, newGame);

        // Vinculaciones cruzadas
        gameToRoom.put(gameId, lobbyId);
        roomToGame.put(lobbyId, gameId);

        // Lock por partida
        gameLocks.put(gameId, new ReentrantLock());

        log.info("Created game {} from lobby {}", gameId, lobbyId);

        return newGame;
}
 
    /**
     * Get the room for a given session, or null if not in any room.
     */
    private GameRoom getRoomForSession(String sessionId) {
        String roomId = sessionToRoom.get(sessionId);
        if (roomId == null) return null;
        return rooms.get(roomId);
    }

    private Lock getGameLock(Long gameId) {
        return gameLocks.computeIfAbsent(gameId, ignored -> new ReentrantLock());
    }

    private void clearLoadedGameMapping(Long gameId, String roomId) {
        if (gameId != null) {
            gameToRoom.remove(gameId, roomId);
        }
        if (roomId != null) {
            roomToGame.remove(roomId, gameId);
        }
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

   public GameResult joinGame(String sessionId, String lobbyId, Long userId) {

        if (sessionToRoom.containsKey(sessionId)) {
            return GameResult.error("Already in a game");
        }

        Lobby lobby = lobbyService.getLobbyById(lobbyId).orElse(null);
        if (lobby == null) return GameResult.error("Lobby not found");

        if (!lobby.getPlayerIds().contains(userId)) {
            return GameResult.error("You are not in this lobby");
        }

        sessionToUserId.put(sessionId, userId);

        GameRoom room = findOrCreateRoomForLobby(lobby);
        boolean isLoadGame = lobby.isLoadGameLobby();

        PlayerState player;

        if (!isLoadGame) {
            // PARTIDA NUEVA
            player = room.addPlayer(sessionId);
            if (player == null) return GameResult.error("Game is full");

        } else {
            // PARTIDA CARGADA
            // Cargar snapshot solo una vez
            if (room.getPlayers().isEmpty()) {
                loadSavedGameIntoRoom(lobby.getGameId(), room);
            }

            try {
                player = bindLoadedPlayerSession(room, lobby.getGameId(), userId, sessionId);
            } catch (IllegalStateException ex) {
                return GameResult.error(ex.getMessage());
            }

            if (player == null) {
                return GameResult.error("User not part of saved game");
            }
        }

        sessionToRoom.put(sessionId, room.getRoomId());

        Packet welcome = Packet.welcome(
            sessionId,
            player.getPlayerIndex(),
            isLoadGame,
            lobby.getGameId()
        );

        // =========================
        // START GAME WHEN READY
        // =========================

        if (room.allPlayersConnected()) {
            if (isLoadGame) {
                lobby.markStarted();
                return GameResult.gameReady(welcome);
            }
        }

        return GameResult.ok(welcome);
    }

    private PlayerState bindLoadedPlayerSession(GameRoom room, Long gameId, Long userId, String sessionId) {
        if (room == null || gameId == null || userId == null || sessionId == null) {
            return null;
        }

        Game game = games.get(gameId);
        if (game == null) {
            game = gameService.getById(gameId);
            games.put(gameId, game);
        }

        int playerIndex;
        if (userId.equals(game.getPlayer1Id())) {
            playerIndex = 0;
        } else if (userId.equals(game.getPlayer2Id())) {
            playerIndex = 1;
        } else {
            return null;
        }

        PlayerState bySession = room.getPlayerBySession(sessionId);
        if (bySession != null && bySession.getPlayerIndex() != playerIndex) {
            throw new IllegalStateException("Session already bound to another player");
        }

        PlayerState player = room.getPlayerByIndex(playerIndex);
        if (player == null) {
            throw new IllegalStateException("Saved game player slot not found");
        }

        if (player.getSessionId() != null && !player.getSessionId().equals(sessionId)) {
            throw new IllegalStateException("Player already connected");
        }

        player.setSessionId(sessionId);
        return player;
    }

   @SuppressWarnings("unchecked")
    private void loadSavedGameIntoRoom(Long gameId, GameRoom room) {

        log.info("[GameController] -> begin loadSavedGameIntoRoom ");
        Game game = gameService.getById(gameId);

        if (game.getState() == null || game.getState().getStatus() != GameStatus.SAVED) {
            throw new IllegalStateException("Game is not in SAVED state");
        }

        Map<String, Object> meta = game.getState().getMeta();
        Map<String, Object> snapshot = (Map<String, Object>) meta.get("snapshot");

        // reconstruye el estado dentro de la room actual
        GameRoom restoredRoom = GameRoom.fromStateMap(room.getRoomId(), snapshot);
        room.restoreFrom(restoredRoom);

        // Vincular estructuras internas
        gameToRoom.put(gameId, room.getRoomId());
        roomToGame.put(room.getRoomId(), gameId);
        games.put(gameId, game);

        gameLocks.putIfAbsent(gameId, new ReentrantLock());

        log.info("[GameController] -> end loadSavedGameIntoRoom ");
        

    }   
    
    
    /**
     * Handle player side selection.
     */
    public GameResult selectSide(String sessionId, String side) {
        log.info("[GameController] -> begin selectSide");
        log.info("[GameController] -> selectSide, sessionId {}, side {}", sessionId, side);
        
        // Validate side
        if (!"Naval".equals(side) && !"Aereo".equals(side)) {
            return GameResult.error("Invalid side. Must be 'Naval' or 'Aereo'");
        }
        
        GameRoom room = getRoomForSession(sessionId);
        log.info("[GameController] -> room {}", room);

        if (room == null) {
            return GameResult.error("You are not in a game room");
        }
        
        PlayerState player = room.getPlayerBySession(sessionId);
        log.info("[GameController] -> player {}", player);

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
            lobbyService.getLobbyById(room.getRoomId()).ifPresent(lobby -> {
                lobby.markStarted();
                createGameFromLobby(lobby);
            });
            log.info("Both sides selected! Game started in room {}. Player 0's turn", room.getRoomId());
            return GameResult.gameReady(Packet.sideChosen(playerIndex, side));
        }

        log.info("[GameController] -> End selectSide");

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
            Long linkedGameId = roomToGame.get(room.getRoomId());
            clearLoadedGameMapping(linkedGameId, room.getRoomId());
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

        log.info("[GameController] -> begin saveAndExit ");
     
        GameResult firstValidation = saveAndExitFirstValidation(sessionId);
        if (firstValidation != null) {
            return firstValidation;
        }

        GameRoom room = getRoomForSession(sessionId);
        PlayerState actor = room.getPlayerBySession(sessionId);

        PlayerState p0 = room.getPlayerByIndex(0);
        PlayerState p1 = room.getPlayerByIndex(1);
        
        Long player1Id = sessionToUserId.get(p0.getSessionId());
        Long player2Id = sessionToUserId.get(p1.getSessionId());
        
        log.info("[GameController] -> player1Id {}, player2Id {}", player1Id, player2Id);

        if (player1Id == null || player2Id == null) {
            return GameResult.error("Cannot resolve players for persistence");
        }
         
        GameState persistedState = buildPersistedState(room,  actor,  sessionId);

        String roomId = room.getRoomId();
        Long gameId = resolveGameId(roomId, player1Id, player2Id);

        if (gameId == null) {
            return GameResult.error("Could not resolve persisted game id");
        }

        Lock gameLock = getGameLock(gameId);
        gameLock.lock();
        Game gameToSave;

        try {
            gameToSave = gameService.getById(gameId);
            //Agarro el Game que ya existe y le piso la informacion de la partida que seria el GameRoom en el Meta   snapshot   
            gameToSave.setState(persistedState);
            gameToSave = gameService.saveGame(player1Id, player2Id, gameToSave);
        } finally {
            gameLock.unlock();
        }

        
        clearLoadedGameMapping(gameToSave.getId(), roomId);
        List<String> sessionsInRoom = getSessionsInSameRoom(sessionId);

        sessionToRoom.entrySet().removeIf(entry -> roomId.equals(entry.getValue()));

        for (String sid : sessionsInRoom) {
            sessionToUserId.remove(sid);
        }

        room.reset();
        cleanupEmptyRooms();

        log.info("Game room {} saved and closed by player {}. Persisted gameId={}", roomId, actor.getPlayerIndex(), gameToSave.getId());
        
        return GameResult.ok(Packet.gameSaved(gameToSave.getId(), actor.getPlayerIndex()));
    }


    private GameResult saveAndExitFirstValidation(String sessionId ) {

        GameRoom room = getRoomForSession(sessionId);
        log.info("[GameController] -> saveAndExitFirstValidation, sessionId {}", sessionId);
        
        if (room == null) {
            return GameResult.error("You are not in a game room");
        }
        
        PlayerState actor = room.getPlayerBySession(sessionId);
        log.info("[GameController] -> saveAndExitFirstValidation, actor {}", actor);

        if (actor == null) {
            return GameResult.error("You are not in the game");
        }

        PlayerState p0 = room.getPlayerByIndex(0);
        PlayerState p1 = room.getPlayerByIndex(1);
        if (p0 == null || p1 == null) {
            return GameResult.error("Cannot save an incomplete game");
        }

        return null;
    }

    private GameState buildPersistedState(GameRoom room, PlayerState actor, String sessionId){
        log.info("[GameController] -> begin buildPersistedState, room {}, actor {}, sessionId {}", room, actor, sessionId);

        GameState persistedState = new GameState();

        persistedState.setStatus(GameStatus.SAVED);
        persistedState.setTurn(room.getCurrentTurn());
        
        //La meta dato en que va a ir guardada en jsonb
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("roomId", room.getRoomId());
        meta.put("savedByPlayerIndex", actor.getPlayerIndex());
        meta.put("savedByUserId", sessionToUserId.get(sessionId));
        meta.put("savedAt", java.time.Instant.now().toString());
        meta.put("snapshot", room.toStateMap()); //esta es la partida en si
        meta.put("schemaVersion", 1);
        persistedState.setMeta(meta);

        log.info("[GameController] -> end buildPersistedState, persistedState {} ", persistedState);

        return persistedState;
    }
     
    private Long resolveGameId(String roomId, Long player1Id, Long player2Id){
        if (!gameService.existsGameBetweenUsers(player1Id, player2Id)){
            return null;
        }    

        Long gameId = roomToGame.getOrDefault(roomId, null);
            
        if (gameId == null) {
            gameId = games.values().stream()
                    .filter(g -> (player1Id.equals(g.getPlayer1Id()) && player2Id.equals(g.getPlayer2Id()))
                            || (player1Id.equals(g.getPlayer2Id()) && player2Id.equals(g.getPlayer1Id())))
                    .map(Game::getId)
                    .findFirst()
                    .orElse(null);
        }
        return gameId;
    }

    /**
     * Process a move action.
     */
    public GameResult processMove(String sessionId, int droneIndex, double x, double y) {
        log.info("[GameController] -> processMove sessionId {}, droneIndex {} ", sessionId, droneIndex);

        GameRoom room = getRoomForSession(sessionId);
        if (room == null) {
            return GameResult.error("You are not in a game room");
        }
        log.info("[GameController] -> room {}", room);

        PlayerState player = room.getPlayerBySession(sessionId);//player llega en null al cargar partida
        log.info("[GameController] -> player  {}", player );
        
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

        boolean destroyedByFuel = !drone.isAlive();
        Packet movePacket = Packet.moveDrone(player.getPlayerIndex(), droneIndex, x, y, drone.getFuel(), destroyedByFuel);
        
        // Check if turn should auto-end
        if (room.getActionsRemaining() <= 0) {
            room.endTurn();
            return GameResult.turnEnded(movePacket, room.getCurrentTurn(), room.getActionsRemaining());
        }

        log.info("[GameController] -> End processMove", room);

        return GameResult.withActionsRemaining(movePacket, room.getActionsRemaining());
    }


    public GameResult processAttack(String sessionId, int attackerIndex, 
                                     int targetPlayerIndex, int targetDroneIndex,
                                     Double manualLineX, Double manualLineY) {

        GameRoom room = getRoomForSession(sessionId);
        if (room == null) {
            return GameResult.error("You are not in a game room");
        }

        PlayerState attacker = room.getPlayerBySession(sessionId);
        GameResult validation = validateAttackContext(room, sessionId, attacker);
        log.info("[GameController] ->  validation {}", validation);
        if (validation != null) return validation;
                
        Drone attackerDrone = room.getDrone(attacker.getPlayerIndex(), attackerIndex);
        Drone targetDrone = targetDroneIndex >= 0 ? room.getDrone(targetPlayerIndex, targetDroneIndex) : null;
        boolean manualBlindShot = targetDroneIndex < 0;
                                        
        GameResult droneValidation = validateDrones(attacker, attackerDrone, targetDrone, targetPlayerIndex, manualBlindShot);
        log.info("[GameController] ->  droneValidation {}", droneValidation);
        if (droneValidation != null) return droneValidation;

        int actionCost = getAttackActionCost(attackerDrone);
        if (room.getActionsRemaining() < actionCost) {
            return GameResult.error("Not enough actions remaining for this attack");
        }
        
        if (manualBlindShot && (manualLineX == null || manualLineY == null)) {
            return GameResult.error("Manual missile shot requires target coordinates");
        }

        double lineX = manualLineX != null ? manualLineX : (targetDrone != null ? targetDrone.getPosition().getX() : 0.0);
        double lineY = manualLineY != null ? manualLineY : (targetDrone != null ? targetDrone.getPosition().getY() : 0.0);

        if (attackerDrone instanceof AerialDrone && targetDrone != null) {
            double targetDistance = hexDistanceBetween(
                attackerDrone.getPosition().getX(),
                attackerDrone.getPosition().getY(),
                targetDrone.getPosition().getX(),
                targetDrone.getPosition().getY()
            );
            if (attackerDrone.getWeapon() == null) {
                return GameResult.error("Attacker has no weapon configured");
            }
            if (targetDistance > attackerDrone.getWeapon().getRange()) {
                return GameResult.error("Target out of aerial attack range");
            }
        }

        HexCoord attackerFinalPosition = attackerDrone.getPosition();
        if (attackerDrone instanceof NavalDrone && targetDrone != null) {
            attackerFinalPosition = getNavalAttackPosition(targetDrone);
            attackerDrone.setPosition(attackerFinalPosition);
        }

        AttackResolution attackResolution = resolveAttack(attackerDrone, targetDrone, lineX, lineY);
        if (!attackResolution.hit()) {
            room.useActions(actionCost);
            Packet missPacket = Packet.attackResult(
                attacker.getPlayerIndex(), attackerIndex,
                targetPlayerIndex, targetDroneIndex,
                0, targetDrone != null ? targetDrone.getCurrentHp() : 0, false,
                lineX, lineY,
                room.getActionsRemaining(),
                attackerFinalPosition.getX(), attackerFinalPosition.getY()
            );
            return finalizeTurn(room, missPacket);
        }

        // Aplica el daño
        int damage = attackResolution.damage();
        if (targetDrone != null) {
            targetDrone.receiveDamage(damage);
        }

        // Consume una accion
        room.useActions(actionCost);

        log.info("Player {} drone {} attacked player {} drone {} for {} damage (remaining HP: {}) in room {}",
            attacker.getPlayerIndex(), attackerIndex, 
            targetPlayerIndex, targetDroneIndex,
            damage, targetDrone != null ? targetDrone.getCurrentHp() : 0, room.getRoomId());

        Packet attackPacket = Packet.attackResult(
            attacker.getPlayerIndex(), attackerIndex,
            targetPlayerIndex, targetDroneIndex,
            damage, targetDrone != null ? targetDrone.getCurrentHp() : 0, true,
            lineX, lineY,
            room.getActionsRemaining(),
            attackerFinalPosition.getX(), attackerFinalPosition.getY()
        );

        // Check game over
        if (targetDrone != null && isPlayerDefeated(room, targetPlayerIndex)) {
            log.info("Player {} has been defeated in room {}!", targetPlayerIndex, room.getRoomId());
            //Aca hay que manejar el final del juego
        }

        return finalizeTurn(room, attackPacket);
    }

    private GameResult validateAttackContext(GameRoom room, String sessionId, PlayerState attacker) {
        log.info("[GameController] -> validateAttackContext, room {}, sessionId {}, playerState {}", room, sessionId, attacker);

        if (room == null) {
            return GameResult.error("You are not in a game room");
        }
    
        if (attacker == null) {
            return GameResult.error("You are not in the game");
        }

        if (!room.isPlayerTurn(sessionId)) {
            return GameResult.error("Not your turn");
        }

        log.info("[GameController] -> End validateAttackContext");
        return null;
    }

    private GameResult validateDrones(PlayerState attacker, Drone attackerDrone,
            Drone targetDrone,int targetPlayerIndex, boolean manualBlindShot) {
                
    log.info("[GameController] -> begin validateDrones, attacker {}, attackerDrone {}", attacker, attackerDrone);
    
        if (attacker.getPlayerIndex() == targetPlayerIndex) {
            return GameResult.error("Cannot attack your own drones");
        }

        if (attackerDrone == null) {
            return GameResult.error("Invalid attacker drone index");
        }

        if (!manualBlindShot && targetDrone == null) {
            return GameResult.error("Invalid target drone index");
        }

        if (!attackerDrone.isAlive()) {
            return GameResult.error("Cannot attack with a destroyed drone");
        }

        if (attackerDrone instanceof NavalDrone navalDrone && !navalDrone.hasMissiles()) {
            return GameResult.error("No missiles remaining for this naval drone");
        }

        if (attackerDrone instanceof AerialDrone && manualBlindShot) {
            return GameResult.error("Aerial drone attack requires selecting an enemy drone");
        }

        if (!manualBlindShot && !targetDrone.isAlive()) {
            return GameResult.error("Target drone is already destroyed");
        }

        log.info("[GameController] -> End validateDrones");
        
        return null;
    }

    private GameResult finalizeTurn(GameRoom room, Packet packet) {
        if (room.getActionsRemaining() <= 0) {
            room.endTurn();
            return GameResult.turnEnded(packet,
                    room.getCurrentTurn(),
                    room.getActionsRemaining());
        }

        return GameResult.withActionsRemaining(packet,
                room.getActionsRemaining());
    }

    private AttackResolution resolveAttack(Drone attackerDrone, Drone targetDrone, double lineX, double lineY) {
        if (attackerDrone.getWeapon() == null) {
            return new AttackResolution(0, false);
        }

        if (!(attackerDrone instanceof NavalDrone navalDrone)) {
            return new AttackResolution(attackerDrone.getWeapon().getDamage(), true);
        }

        if (!(navalDrone.getWeapon() instanceof MissileWeapon missileWeapon)) {
            return new AttackResolution(attackerDrone.getWeapon().getDamage(), true);
        }

        if (!navalDrone.hasMissiles()) {
            return new AttackResolution(0, false);
        }

        double traveledDistance = hexDistanceBetween(
            attackerDrone.getPosition().getX(),
            attackerDrone.getPosition().getY(),
            lineX,
            lineY
        );
        if (!missileWeapon.canReach(traveledDistance) || traveledDistance > missileMaxDistance) {
            navalDrone.consumeMissile();
            return new AttackResolution(0, false);
        }

        double effectiveAccuracy = missileWeapon.getEffectiveAccuracy(traveledDistance);

        if (targetDrone instanceof NavalDrone) {
            double targetOffset = distanceBetween(lineX, lineY, targetDrone.getPosition().getX(), targetDrone.getPosition().getY());
            double alignmentFactor = Math.max(0.0, 1.0 - (targetOffset / 120.0));
            effectiveAccuracy *= alignmentFactor;
        }

        boolean hit = Math.random() <= effectiveAccuracy;
        navalDrone.consumeMissile();
        if (!hit) {
            return new AttackResolution(0, false);
        }

        if (!(targetDrone instanceof NavalDrone navalTarget)) {
            return new AttackResolution(attackerDrone.getWeapon().getDamage(), true);
        }

        int damage = (int) Math.round(navalTarget.getMaxHp() * missileDamagePercentOnNaval);
        return new AttackResolution(Math.max(1, damage), true);
    }

    private double distanceBetween(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Converts pixel distance into an approximate hex distance.
     * Mirrors front/utils/HexGrid.js getHexDistance() (pixelDist / (sqrt(3) * size)).
     */
    private double hexDistanceBetween(double x1, double y1, double x2, double y2) {
        double pixelDistance = distanceBetween(x1, y1, x2, y2);
        if (HEX_WIDTH_PX <= 0) {
            return pixelDistance;
        }
        return pixelDistance / HEX_WIDTH_PX;
    }

    private int getAttackActionCost(Drone attackerDrone) {
        return attackerDrone instanceof NavalDrone ? NAVAL_ATTACK_ACTION_COST : DEFAULT_ATTACK_ACTION_COST;
    }

    private HexCoord getNavalAttackPosition(Drone targetDrone) {
        return new HexCoord(
            targetDrone.getPosition().getX(),
            targetDrone.getPosition().getY() - NAVAL_ATTACK_VERTICAL_OFFSET
        );
    }

    private record AttackResolution(int damage, boolean hit) {
    }

//Las 3 funciones anteriores se separan para bajar la complejidad ciclomatica


    public GameResult endTurn(String sessionId) {
        
        GameRoom room = getRoomForSession(sessionId);
        log.info("[GameController] -> endTurn, room {}, sessionId {}", room, sessionId);

        if (room == null) {
            return GameResult.error("You are not in a game room");
        }

        if (!room.isPlayerTurn(sessionId)) {
            return GameResult.error("Not your turn");
        }

        int endingPlayer = room.getCurrentTurn();
        List<Integer> destroyedByIdleIndexes = room.consumeIdleFuelForCurrentPlayer();
        room.endTurn();

        log.info("Turn ended in room {}. Now player {}'s turn with {} actions",
            room.getRoomId(), room.getCurrentTurn(), room.getActionsRemaining());

        Packet turnPacket = Packet.turnStart(room.getCurrentTurn(), room.getActionsRemaining());
        if (destroyedByIdleIndexes.isEmpty()) {
            return GameResult.turnStarted(room.getCurrentTurn(), room.getActionsRemaining());
        }

        List<Map<String, Object>> fuelUpdates = new ArrayList<>();
        for (Integer droneIndex : destroyedByIdleIndexes) {
            Drone destroyedDrone = room.getDrone(endingPlayer, droneIndex);
            if (destroyedDrone == null) {
                continue;
            }
            fuelUpdates.add(Packet.fuelUpdate(endingPlayer, droneIndex, destroyedDrone.getFuel(), true).toMap());
        }

        Map<String, Object> payload = new LinkedHashMap<>(turnPacket.getPayload());
        payload.put("fuelUpdates", fuelUpdates);
        return GameResult.ok(Packet.of(turnPacket.getType(), payload));
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

    // room ID de la session  
     
    public String getRoomId(String sessionId) {
        return sessionToRoom.get(sessionId);
    }

    // Todas las sessionId de una session
    public java.util.List<String> getSessionsInSameRoom(String sessionId) {
        String roomId = sessionToRoom.get(sessionId);
        if (roomId == null) return java.util.List.of();
        
        return sessionToRoom.entrySet().stream()
            .filter(entry -> roomId.equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toList());
    }

    public int getActiveRoomCount() {
        return rooms.size();
    }
}
