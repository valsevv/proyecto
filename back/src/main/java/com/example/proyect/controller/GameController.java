package com.example.proyect.controller;

import java.time.OffsetDateTime;
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
import com.example.proyect.auth.service.RankingService;
import com.example.proyect.game.GameRoom;
import com.example.proyect.game.PlayerState;
import com.example.proyect.game.config.UnitBalanceRegistry;
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
import com.example.proyect.persistence.classes.User;
import com.example.proyect.persistence.repos.UserRepository;
import com.example.proyect.websocket.packet.Packet;

import jakarta.annotation.PostConstruct;

//vseverio Clase principal controladora de partida en tiempo real, salas, turnos, movimientos, ataques, guardado/cargado y estado por sesion
@Service
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);
    private static final int DEFAULT_ATTACK_ACTION_COST = 1;
    private static final int NAVAL_ATTACK_ACTION_COST = 2;
    private static final double NAVAL_ATTACK_VERTICAL_OFFSET = 90.0;

    @Value("${game.actions-per-turn:10}")
    private int actionsPerTurn;

    @Value("${game.units.aereo.vision-range:${game.vision-range-aereo:6}}")
    private int aerialVisionRange;

    @Value("${game.units.naval.vision-range:${game.vision-range-naval:4}}")
    private int navalVisionRange;

    @Value("${game.units.aereo.max-hp:100}")
    private int aerialDroneMaxHp;

    @Value("${game.units.aereo.movement-range:2}")
    private int aerialDroneMovementRange;

    @Value("${game.units.aereo.max-fuel:10}")
    private int aerialDroneMaxFuel;

    @Value("${game.units.aereo.ammo:1}")
    private int aerialDroneAmmo;

    @Value("${game.units.aereo.accuracy:0.60}")
    private double aerialDroneAccuracy;

    @Value("${game.units.naval.max-hp:100}")
    private int navalDroneMaxHp;

    @Value("${game.units.naval.movement-range:2}")
    private int navalDroneMovementRange;

    @Value("${game.units.naval.max-fuel:10}")
    private int navalDroneMaxFuel;

    @Value("${game.units.naval.weapon-ammo:10}")
    private int navalDroneWeaponAmmo;

    @Value("${game.units.naval.missiles:2}")
    private int navalDroneMissiles;

    @Value("${game.units.naval.accuracy:0.75}")
    private double navalDroneAccuracy;

    @Value("${game.missile.max-distance:15}")
    private int missileMaxDistance;

    @Value("${game.missile.damage-percent-on-naval:0.5}")
    private double missileDamagePercentOnNaval;

    @Value("${game.aerial-attack-fuel-cost:2}")
    private int aerialAttackFuelCost;

    @Value("${game.carrier-hits-to-destroy:5}")
    private int carrierHitsToDestroy;

    @Value("${game.units.aereo.carrier-hp:6}")
    private int aerialCarrierHitsToDestroy;

    @Value("${game.units.aereo.carrier-movement-range:3}")
    private int aerialCarrierMovementRange;

    @Value("${game.units.naval.carrier-hp:3}")
    private int navalCarrierHitsToDestroy;

    @Value("${game.units.naval.carrier-movement-range:1}")
    private int navalCarrierMovementRange;

    // Must match frontend HexGrid size (front/scenes/MainScene.js -> new HexGrid(this, 35, ...))
    private static final double HEX_SIZE_PX = 35.0;
    private static final double HEX_WIDTH_PX = Math.sqrt(3.0) * HEX_SIZE_PX;

    private final LobbyService lobbyService;

    private final GameService gameService;
    private final RankingService rankingService;
    private final UserRepository userRepository;
    // todos los rooms x id
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    
    // T trackea en que room esta cada sesion
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();
    
    // Trackea user id por sesion
    private final Map<String, Long> sessionToUserId = new ConcurrentHashMap<>();
    
    //GamesId de la session
    private final Map<Long, Game> games = new ConcurrentHashMap<>();

    // juegos cargados
    private final Map<Long, String> gameToRoom = new ConcurrentHashMap<>();
    private final Map<String, Long> roomToGame = new ConcurrentHashMap<>();
    private final Map<Long, Lock> gameLocks = new ConcurrentHashMap<>();

    //
    private final AtomicInteger roomCounter = new AtomicInteger(1);

    public GameController(LobbyService lobbyService, GameService gameService, RankingService rankingService, UserRepository userRepository) { //inicializa controlador
        this.lobbyService = lobbyService;
        this.gameService = gameService;
        this.rankingService = rankingService;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void configureUnitBalance() {
        UnitBalanceRegistry.configure(
            aerialDroneMaxHp,
            aerialDroneMovementRange,
            aerialVisionRange,
            aerialDroneMaxFuel,
            aerialDroneAmmo,
            aerialDroneAccuracy,
            navalDroneMaxHp,
            navalDroneMovementRange,
            navalVisionRange,
            navalDroneMaxFuel,
            navalDroneWeaponAmmo,
            navalDroneMissiles,
            navalDroneAccuracy,
            aerialCarrierHitsToDestroy,
            aerialCarrierMovementRange,
            navalCarrierHitsToDestroy,
            navalCarrierMovementRange
        );
    }
    public void bindSessionUser(String sessionId, Long userId) { //vincula sesion websocket con userid para trazabilidad
        if (sessionId != null && userId != null) {
            sessionToUserId.put(sessionId, userId);
        }
    }
    public Long getUserIdBySession(String sessionId) {
        return sessionToUserId.get(sessionId);
    } //devuelve userid asociado a una sesion
    

    //crea el froom apartir del lobby, si ya existe lo retorna
    private GameRoom findOrCreateRoomForLobby(Lobby lobby) {

        return rooms.computeIfAbsent(
            lobby.getLobbyId(),
            id -> {
                GameRoom newRoom = new GameRoom(
                    id,
                    actionsPerTurn,
                    aerialVisionRange,
                    navalVisionRange,
                    carrierHitsToDestroy,
                    aerialCarrierHitsToDestroy,
                    navalCarrierHitsToDestroy
                );
                log.info("Created game room {} from lobby {}", id, lobby.getLobbyId());
                return newRoom;
            }
        );
    }


    private Game createGameFromLobby(Lobby lobby) { //crea una partida desde un lobby

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
 

    private GameRoom getRoomForSession(String sessionId) { //obtiene el room de una sesion, o null si no hay rooms
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

    private void cleanupEmptyRooms() { //eliminar los rooms que estan vacios
        rooms.entrySet().removeIf(entry -> {
            GameRoom room = entry.getValue();
            if (room.getPlayers().isEmpty()) {
                log.info("Removing empty room: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

   public GameResult joinGame(String sessionId, String lobbyId, Long userId) { //mete al jugador en el lobby y devuelve packet de bienvenida

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
        // empieza uego si esta pronto
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
    private void loadSavedGameIntoRoom(Long gameId, GameRoom room) { //carga partida en un room

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
    
    

    public GameResult selectSide(String sessionId, String side) { //manejador de seleccion de lados para jugador
        log.info("[GameController] -> begin selectSide");
        log.info("[GameController] -> selectSide, sessionId {}, side {}", sessionId, side);
        
        // valida lado
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
        
        // revisa si el jugador ya eligio lado
        if (room.getPlayerSide(playerIndex) != null) {
            return GameResult.error("You already selected a side");
        }
        
        // el primer jugador elige
        // al segundo se le asigna el lado automaticamente
        room.setPlayerSide(playerIndex, side);
        room.createDronesForSide(playerIndex, side);
        
        log.info("Player {} selected side {} in room {}", playerIndex, side, room.getRoomId());
        
        // esto revisa que ambos jugadores tengan lado
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


    public int removePlayer(String sessionId) { //eliminar player de un room
        return removePlayerInternal(sessionId, true);
    }

    public int removePlayerWithoutForfeit(String sessionId) {
        return removePlayerInternal(sessionId, false);
    }

    private int removePlayerInternal(String sessionId, boolean countAsForfeitOnDisconnect) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) {
            return -1;
        }

        PlayerState leavingPlayer = room.getPlayerBySession(sessionId);
        boolean gameFinished = isPersistedGameFinished(room);
        PlayerState winnerOnDisconnect = null;
        Long winnerOnDisconnectUserId = null;
        Long loserOnDisconnectUserId = null;

        if (countAsForfeitOnDisconnect && room.isGameStarted() && !gameFinished && leavingPlayer != null) {
            winnerOnDisconnect = room.getPlayers().stream()
                .filter(player -> player.getPlayerIndex() != leavingPlayer.getPlayerIndex())
                .findFirst()
                .orElse(null);
            winnerOnDisconnectUserId = resolveUserId(room, winnerOnDisconnect);
            loserOnDisconnectUserId = resolveUserId(room, leavingPlayer);
        }

        PlayerState removed = room.removePlayer(sessionId);
        if (removed != null) {
            if (winnerOnDisconnect != null && winnerOnDisconnectUserId != null && loserOnDisconnectUserId != null) {
                markGameAsFinished(room, winnerOnDisconnect.getPlayerIndex());
                registerMatchResult(winnerOnDisconnectUserId, loserOnDisconnectUserId);
                log.info("Player {} disconnected from active game {}. Winner by forfeit: {}", sessionId, room.getRoomId(), winnerOnDisconnect.getPlayerIndex());
            }

            log.info("Player {} (index {}) left room {}", sessionId, removed.getPlayerIndex(), room.getRoomId());
            sessionToRoom.remove(sessionId);
            sessionToUserId.remove(sessionId);
            Long linkedGameId = roomToGame.get(room.getRoomId());
            clearLoadedGameMapping(linkedGameId, room.getRoomId());
            room.reset();
            
            // limpa rooms
            cleanupEmptyRooms();
            
            return removed.getPlayerIndex();
        }
        return -1;
    }

    public GameResult saveAndExit(String sessionId) { //funcionalidad e guardado y salida en base

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

    public GameResult forfeitGame(String sessionId) {
        GameRoom room = getRoomForSession(sessionId);
        if (room == null) {
            return GameResult.error("You are not in a game room");
        }

        PlayerState forfeitingPlayer = room.getPlayerBySession(sessionId);
        if (forfeitingPlayer == null) {
            return GameResult.error("You are not in the game");
        }

        PlayerState winnerPlayer = room.getPlayers().stream()
            .filter(player -> player.getPlayerIndex() != forfeitingPlayer.getPlayerIndex())
            .findFirst()
            .orElse(null);

        if (winnerPlayer == null) {
            return GameResult.error("Cannot forfeit without an opponent");
        }

        Long loserUserId = resolveUserId(room, forfeitingPlayer);
        if (loserUserId == null) {
            return GameResult.error("Cannot resolve forfeiting user");
        }

        Long winnerUserId = resolveUserId(room, winnerPlayer);
        if (winnerUserId == null) {
            return GameResult.error("Cannot resolve winner user");
        }

        markGameAsFinished(room, winnerPlayer.getPlayerIndex());
        registerMatchResult(winnerUserId, loserUserId);

        String roomId = room.getRoomId();
        Long linkedGameId = roomToGame.get(roomId);
        clearLoadedGameMapping(linkedGameId, roomId);

        List<String> sessionsInRoom = getSessionsInSameRoom(sessionId);
        sessionToRoom.entrySet().removeIf(entry -> roomId.equals(entry.getValue()));
        for (String sid : sessionsInRoom) {
            sessionToUserId.remove(sid);
        }

        room.reset();
        cleanupEmptyRooms();

        return GameResult.ok(Packet.gameForfeited(forfeitingPlayer.getPlayerIndex(), winnerPlayer.getPlayerIndex()));
    }


    private GameResult saveAndExitFirstValidation(String sessionId ) { //valida antes de guardar

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


    public GameResult processMove(String sessionId, int droneIndex, double x, double y) { //procesa un movimiento
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

        // aplica movimiento
        if (!room.moveDrone(sessionId, droneIndex, x, y)) {
            return GameResult.error("Invalid move");
        }

        // consume accion
        room.useAction();
        
        log.debug("Player {} moved drone {} to ({}, {}) in room {}", 
            player.getPlayerIndex(), droneIndex, x, y, room.getRoomId());

        boolean destroyedByFuel = !drone.isAlive();
        Packet movePacket = Packet.moveDrone(player.getPlayerIndex(), droneIndex, x, y, drone.getFuel(), destroyedByFuel);
        
        // revisa si deberia pasar de turno
        if (room.getActionsRemaining() <= 0) {
            room.endTurn();
            return GameResult.turnEnded(movePacket, room.getCurrentTurn(), room.getActionsRemaining());
        }

        log.info("[GameController] -> End processMove", room);

        return GameResult.withActionsRemaining(movePacket, room.getActionsRemaining());
    }



    public GameResult processCarrierMove(String sessionId, double x, double y) { //procesa movimiento de carrier
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

        if (!room.moveCarrier(sessionId, x, y)) {
            return GameResult.error("Invalid carrier move");
        }

        room.useAction();
        Packet movedPacket = Packet.carrierMoved(player.getPlayerIndex(), x, y, room.getActionsRemaining());

        if (room.getActionsRemaining() <= 0) {
            room.endTurn();
            return GameResult.turnEnded(movedPacket, room.getCurrentTurn(), room.getActionsRemaining());
        }

        return GameResult.withActionsRemaining(movedPacket, room.getActionsRemaining());
    }

    public GameResult processAttack(String sessionId, int attackerIndex, 
                                     int targetPlayerIndex, int targetDroneIndex,
                                     Double manualLineX, Double manualLineY,
                                     Double destinationX, Double destinationY, String targetType) { //procesa ataque

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
        boolean carrierTarget = "carrier".equalsIgnoreCase(targetType);
                                        
        GameResult droneValidation = validateDrones(room, attacker, attackerDrone, targetDrone, targetPlayerIndex, manualBlindShot, carrierTarget);
        log.info("[GameController] ->  droneValidation {}", droneValidation);
        if (droneValidation != null) return droneValidation;

        int actionCost = getAttackActionCost(attackerDrone);
        if (room.getActionsRemaining() < actionCost) {
            return GameResult.error("Not enough actions remaining for this attack");
        }
        
        if (manualBlindShot && (manualLineX == null || manualLineY == null)) {
            return GameResult.error("Manual shot requires target coordinates");
        }

        double lineX = manualLineX != null ? manualLineX : (targetDrone != null ? targetDrone.getPosition().getX() : 0.0);
        double lineY = manualLineY != null ? manualLineY : (targetDrone != null ? targetDrone.getPosition().getY() : 0.0);

        if (attackerDrone instanceof AerialDrone) {
            if (attackerDrone.getWeapon() == null) {
                return GameResult.error("Attacker has no weapon configured");
            }
            double targetX = targetDrone != null ? targetDrone.getPosition().getX() : lineX;
            double targetY = targetDrone != null ? targetDrone.getPosition().getY() : lineY;
            double targetDistance = hexDistanceBetween(
                attackerDrone.getPosition().getX(),
                attackerDrone.getPosition().getY(),
                targetX,
                targetY
            );
            if (targetDistance > attackerDrone.getWeapon().getRange()) {
                return GameResult.error("Target out of aerial attack range");
            }
        }

        HexCoord attackerFinalPosition = attackerDrone.getPosition();

        if (attackerDrone instanceof AerialDrone) {
            if (destinationX == null || destinationY == null) {
                return GameResult.error("Aerial attack requires destination coordinates");
            }
            HexCoord requestedDestination = new HexCoord(destinationX, destinationY);
            HexCoord finalDestination = resolveAerialFinalPosition(room, requestedDestination, attackerDrone);
            attackerDrone.setPosition(finalDestination);
            if (aerialAttackFuelCost > 0) {
                attackerDrone.consumeFuel(aerialAttackFuelCost);
                if (attackerDrone.getFuel() <= 0) {
                    attackerDrone.setCurrentHp(0);
                    attackerDrone.receiveDamage(1);
                }
            }
            attackerFinalPosition = finalDestination;
        }
        if (attackerDrone instanceof NavalDrone && targetDrone != null) {
            attackerFinalPosition = getNavalAttackPosition(targetDrone);
            attackerDrone.setPosition(attackerFinalPosition);
        }

        AttackResolution attackResolution = resolveAttack(attackerDrone, targetDrone, lineX, lineY);
        int attackerAmmo = getAttackerAmmoForPacket(attackerDrone);
        if (!attackResolution.hit()) {
            room.useActions(actionCost);
            Packet missPacket = Packet.attackResult(
                attacker.getPlayerIndex(), attackerIndex,
                targetPlayerIndex, targetDroneIndex,
                0, targetDrone != null ? targetDrone.getCurrentHp() : 0, false,
                lineX, lineY,
                room.getActionsRemaining(),
                attackerFinalPosition.getX(), attackerFinalPosition.getY(),
                attackerDrone.getCurrentHp(), !attackerDrone.isAlive(),
                attackerAmmo,
                room.getCarrierHealth(targetPlayerIndex),
                room.isCarrierDestroyed(targetPlayerIndex)
            );
            return finalizeTurn(room, missPacket);
        }

        int targetCarrierHealth = room.getCarrierHealth(targetPlayerIndex);
        boolean targetCarrierDestroyed = room.isCarrierDestroyed(targetPlayerIndex);

        // Aplica el daño
        int damage = attackResolution.damage();
        if (carrierTarget) {
            targetCarrierHealth = room.damageCarrier(targetPlayerIndex, 1);
            targetCarrierDestroyed = targetCarrierHealth <= 0;
        } else if (targetDrone != null) {
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
            attackerFinalPosition.getX(), attackerFinalPosition.getY(),
            attackerDrone.getCurrentHp(), !attackerDrone.isAlive(),
            attackerAmmo,
            targetCarrierHealth,
            targetCarrierDestroyed
        );

        boolean gameFinished = false;
        int winnerPlayerIndex = -1;

        // Check game over
        if (isPlayerDefeated(room, targetPlayerIndex)) {
            winnerPlayerIndex = attacker.getPlayerIndex();
            gameFinished = true;
            markGameAsFinished(room, winnerPlayerIndex);
            PlayerState winner = room.getPlayerByIndex(winnerPlayerIndex);
            PlayerState loser = room.getPlayerByIndex(targetPlayerIndex);
            registerMatchResult(resolveUserId(room, winner), resolveUserId(room, loser));
            log.info("Player {} has been defeated in room {}. Winner: {}", targetPlayerIndex, room.getRoomId(), winnerPlayerIndex);
        }

        if (gameFinished) {
            Packet finishedAttackPacket = Packet.attackResult(
                attacker.getPlayerIndex(), attackerIndex,
                targetPlayerIndex, targetDroneIndex,
                damage, targetDrone != null ? targetDrone.getCurrentHp() : 0, true,
                lineX, lineY,
                room.getActionsRemaining(),
                attackerFinalPosition.getX(), attackerFinalPosition.getY(),
                attackerDrone.getCurrentHp(), !attackerDrone.isAlive(),
                attackerAmmo,
                targetCarrierHealth,
                targetCarrierDestroyed,
                true,
                winnerPlayerIndex
            );
            return GameResult.ok(finishedAttackPacket);
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

    private GameResult validateDrones(GameRoom room, PlayerState attacker, Drone attackerDrone,
            Drone targetDrone,int targetPlayerIndex, boolean manualBlindShot, boolean carrierTarget) {
                
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

        if (attackerDrone instanceof AerialDrone) {
            if (attackerDrone.getWeapon() == null) {
                return GameResult.error("Attacker has no weapon configured");
            }
            if (!attackerDrone.getWeapon().hasAmmo()) {
                return GameResult.error("No ammo remaining for this aerial drone");
            }
        }

        if (attackerDrone instanceof AerialDrone && manualBlindShot && !carrierTarget) {
            return GameResult.error("Aerial drone attack requires selecting an enemy unit");
        }

        if (carrierTarget && !manualBlindShot) {
            return GameResult.error("Carrier attacks must target the enemy carrier");
        }

        if (carrierTarget && targetDrone != null) {
            return GameResult.error("Carrier attacks cannot target drones");
        }

        if (carrierTarget && room.isCarrierDestroyed(targetPlayerIndex)) {
            return GameResult.error("Target carrier is already destroyed");
        }

        if (!manualBlindShot && !targetDrone.isAlive()) {
            return GameResult.error("Target drone is already destroyed");
        }

        log.info("[GameController] -> End validateDrones");
        
        return null;
    }


    private HexCoord resolveAerialFinalPosition(GameRoom room, HexCoord requestedDestination, Drone attackerDrone) { //resuelve la posicion final del dron aereo
        if (!room.isPositionOccupied(requestedDestination, attackerDrone)) {
            return requestedDestination;
        }

        double step = HEX_WIDTH_PX;
        double[][] offsets = {
            {step, 0}, {-step, 0},
            {step / 2.0, (Math.sqrt(3.0) / 2.0) * step},
            {-step / 2.0, (Math.sqrt(3.0) / 2.0) * step},
            {step / 2.0, -(Math.sqrt(3.0) / 2.0) * step},
            {-step / 2.0, -(Math.sqrt(3.0) / 2.0) * step}
        };

        for (double[] offset : offsets) {
            HexCoord candidate = new HexCoord(requestedDestination.getX() + offset[0], requestedDestination.getY() + offset[1]);
            if (!room.isPositionOccupied(candidate, attackerDrone)) {
                return candidate;
            }
        }

        return attackerDrone.getPosition();
    }

    private GameResult finalizeTurn(GameRoom room, Packet packet) { //fin de turno
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
            if (!attackerDrone.getWeapon().hasAmmo()) {
                return new AttackResolution(0, false);
            }
            attackerDrone.getWeapon().consumeAmmo(1);
            boolean hit = Math.random() <= attackerDrone.getWeapon().getAccuracy();
            if (!hit) {
                return new AttackResolution(0, false);
            }
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

    private double hexDistanceBetween(double x1, double y1, double x2, double y2) { //convierte pixels en distancia efectiva
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

    private int getAttackerAmmoForPacket(Drone attackerDrone) {
        if (attackerDrone instanceof NavalDrone nd) {
            return nd.getMissiles();
        }
        if (attackerDrone != null && attackerDrone.getWeapon() != null) {
            return attackerDrone.getWeapon().getAmmo();
        }
        return 0;
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


    public GameResult processRecall(String sessionId, int droneIndex) { //procesa recalls, guarda un dron deplegado, llena de combustible y misiles o bombas
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
            return GameResult.error("Cannot recall a destroyed drone");
        }
        if (!drone.isDeployed()) {
            return GameResult.error("Drone is already in the hangar");
        }

        if (!room.recallDrone(sessionId, droneIndex)) {
            return GameResult.error("Cannot recall drone");
        }

        room.useAction();

        int missiles = drone instanceof NavalDrone nd ? nd.getMissiles() : 0;
        Packet recallPacket = Packet.droneRecalled(
            player.getPlayerIndex(), droneIndex,
            drone.getFuel(), drone.getMaxFuel(), missiles,
            room.getActionsRemaining()
        );

        log.info("Player {} recalled drone {} in room {}", player.getPlayerIndex(), droneIndex, room.getRoomId());

        return finalizeTurn(room, recallPacket);
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


    private void markGameAsFinished(GameRoom room, int winnerPlayerIndex) {
        Long gameId = roomToGame.get(room.getRoomId());
        if (gameId == null) return;

        Game game = games.get(gameId);
        if (game == null) {
            game = gameService.getById(gameId);
            games.put(gameId, game);
        }

        GameState state = game.getState();
        if (state == null) {
            state = new GameState();
            game.setState(state);
        }

        state.setStatus(GameStatus.FINISHED);
        state.setTurn(winnerPlayerIndex + 1);
        game.setEndedAt(OffsetDateTime.now());
        gameService.saveGame(game.getPlayer1Id(), game.getPlayer2Id(), game);
    }

    private void registerMatchResult(Long winnerUserId, Long loserUserId) {
        registerWinForUserId(winnerUserId);
        registerLossForUserId(loserUserId);
    }

    private Long resolveUserId(GameRoom room, PlayerState player) {
        if (room == null || player == null) return null;

        if (player.getSessionId() != null) {
            Long bySession = sessionToUserId.get(player.getSessionId());
            if (bySession != null) {
                return bySession;
            }
        }

        Long gameId = roomToGame.get(room.getRoomId());
        if (gameId == null) {
            return null;
        }

        Game game = games.get(gameId);
        if (game == null) {
            game = gameService.getById(gameId);
            games.put(gameId, game);
        }

        if (player.getPlayerIndex() == 0) {
            return game.getPlayer1Id();
        }
        if (player.getPlayerIndex() == 1) {
            return game.getPlayer2Id();
        }
        return null;
    }

    private boolean isPersistedGameFinished(GameRoom room) {
        Long gameId = roomToGame.get(room.getRoomId());
        if (gameId == null) {
            return false;
        }

        Game game = games.get(gameId);
        if (game == null) {
            game = gameService.getById(gameId);
            games.put(gameId, game);
        }

        GameState state = game.getState();
        return state != null && state.getStatus() == GameStatus.FINISHED;
    }

    private void registerWinForUserId(Long winnerUserId) {
        log.info("registerWinForUserIdfor user: {}", winnerUserId);
        if (winnerUserId == null) return;

        userRepository.findById(winnerUserId).ifPresent(user -> {
            user.registerWin();
            User savedUser = userRepository.save(user);
            rankingService.createSnapshot(savedUser.getUserId());
        });
    }

    private void registerLossForUserId(Long loserUserId) {
        log.info("registerLossForUserId for user: {}", loserUserId);
        if (loserUserId == null) return;

        userRepository.findById(loserUserId).ifPresent(user -> {
            user.registerLoss();
            User savedUser = userRepository.save(user);
            rankingService.createSnapshot(savedUser.getUserId());
        });
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
