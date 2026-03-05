package com.example.proyect.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.proyect.game.units.Unit.HexCoord;
import com.example.proyect.game.units.drone.AerialDrone;
import com.example.proyect.game.units.drone.Drone;
import com.example.proyect.game.units.drone.NavalDrone;

/**
me parece que este GameRoom sustituye nuestro Game o como es la movida
 */
public class GameRoom {
    private static final Logger log = LoggerFactory.getLogger(GameRoom.class);

    private final String roomId;

    public String getRoomId() {
        return roomId;
    }

    public static final int MAX_PLAYERS = 2;
    public static final int AERIAL_DRONES_PER_PLAYER = 12;
    public static final int NAVAL_DRONES_PER_PLAYER = 6;
    public static final int DEFAULT_ACTIONS_PER_TURN = 10;
    public static final int DEFAULT_AERIAL_VISION_RANGE = 4;
    public static final int DEFAULT_NAVAL_VISION_RANGE = 3;
    public static final int DEFAULT_CARRIER_HITS_TO_DESTROY = 5;
    public static final int AERIAL_CARRIER_HITS_TO_DESTROY = 6;
    public static final int NAVAL_CARRIER_HITS_TO_DESTROY = 3;

    public static final int MOVEMENT_FUEL_COST = 1;
    public static final int IDLE_FUEL_COST = 1;
    //  frontend tracks per-drone limits

    // Map dimensions used by spawning logic (must match frontend world size)
    private static final double WORLD_WIDTH = 3200.0;
    private static final double WORLD_HEIGHT = 2400.0;

    // Carrier spawn constraints: random Y, opposite map sides on X.
    private static final double CARRIER_EDGE_MARGIN = 320.0;
    private static final double CARRIER_SPAWN_Y_MARGIN = 240.0;

    private final List<PlayerState> players = new ArrayList<>();
    
    // Side selection state (Naval or Aereo)
    private final Map<Integer, String> playerSides = new HashMap<>();
    private final Map<Integer, HexCoord> playerSpawnAnchors = new HashMap<>();
    private final Map<Integer, HexCoord> carrierPositions = new HashMap<>();

    // Turn state
    private boolean gameStarted = false;
    private int currentTurn = 0; // Player index whose turn it is
    private int actionsPerTurn;
    private int aerialVisionRange;
    private int navalVisionRange;
    private int carrierHitsToDestroy;
    private int actionsRemaining;
    private final Map<Integer, Integer> carrierHealthByPlayer = new HashMap<>();

    public GameRoom(String roomId) {
        this(roomId, DEFAULT_ACTIONS_PER_TURN, DEFAULT_AERIAL_VISION_RANGE, DEFAULT_NAVAL_VISION_RANGE, DEFAULT_CARRIER_HITS_TO_DESTROY);
    }

    public GameRoom(String roomId, int actionsPerTurn) {
        this(roomId, actionsPerTurn, DEFAULT_AERIAL_VISION_RANGE, DEFAULT_NAVAL_VISION_RANGE, DEFAULT_CARRIER_HITS_TO_DESTROY);
    }

    public GameRoom(String roomId, int actionsPerTurn, int aerialVisionRange, int navalVisionRange) {
        this(roomId, actionsPerTurn, aerialVisionRange, navalVisionRange, DEFAULT_CARRIER_HITS_TO_DESTROY);
    }

    public GameRoom(String roomId, int actionsPerTurn, int aerialVisionRange, int navalVisionRange, int carrierHitsToDestroy) {
        this.roomId = roomId;
        if (actionsPerTurn <= 0) {
            throw new IllegalArgumentException("actionsPerTurn must be > 0");
        }
        if (aerialVisionRange < 0) {
            throw new IllegalArgumentException("aerialVisionRange must be >= 0");
        }
        if (navalVisionRange < 0) {
            throw new IllegalArgumentException("navalVisionRange must be >= 0");
        }
        if (carrierHitsToDestroy <= 0) {
            throw new IllegalArgumentException("carrierHitsToDestroy must be > 0");
        }
        this.actionsPerTurn = actionsPerTurn;
        this.aerialVisionRange = aerialVisionRange;
        this.navalVisionRange = navalVisionRange;
        this.carrierHitsToDestroy = carrierHitsToDestroy;
        this.actionsRemaining = actionsPerTurn;
    }

    /**
     * Add a player to the room. Returns the new PlayerState, or null if full.
     */
    public synchronized PlayerState addPlayer(String sessionId) {
        if (players.size() >= MAX_PLAYERS) return null;

        int index = players.size();

        List<Drone> drones = new ArrayList<>();
        // Note: Drones will be created as placeholders initially
        // They will be recreated with proper type after side selection
        for (int i = 0; i < AERIAL_DRONES_PER_PLAYER; i++) {
            AerialDrone drone = new AerialDrone();
            drone.setId(UUID.randomUUID().toString());
            drone.setOwnerPlayerId(index);
            drone.setPosition(getDroneSpawnPosition(index, i, AERIAL_DRONES_PER_PLAYER));
            applyVisionRange(drone);
            drones.add(drone);
        }

        PlayerState player = new PlayerState( sessionId, index, drones);
        players.add(player);
        initializeCarrierForPlayer(index);
        return player;
    }

    private void initializeCarrierForPlayer(int playerIndex) {
        carrierHealthByPlayer.put(playerIndex, getCarrierMaxHealth(playerIndex));
        carrierPositions.put(playerIndex, playerSpawnAnchors.computeIfAbsent(playerIndex, this::getCarrierSpawnPosition));
    }
    
    /*
     * Recreate player's drones based on selected side.
     * Called after side selection.
     */
    public synchronized void createDronesForSide(int playerIndex, String side) {
        PlayerState player = getPlayerByIndex(playerIndex);
        if (player == null) return;

        boolean isNaval = "Naval".equals(side);
        int droneCount = isNaval ? NAVAL_DRONES_PER_PLAYER : AERIAL_DRONES_PER_PLAYER;
        List<Drone> drones = new ArrayList<>();

        for (int i = 0; i < droneCount; i++) {
            Drone drone = isNaval ? new NavalDrone() : new AerialDrone();
            drone.setId(UUID.randomUUID().toString());
            drone.setOwnerPlayerId(playerIndex);
            drone.setPosition(getDroneSpawnPosition(playerIndex, i, droneCount));
            applyVisionRange(drone);
            drones.add(drone);
        }
        
        // Replace drones in player state
        player.getDrones().clear();
        player.getDrones().addAll(drones);
        player.setSide(side);
        playerSides.put(playerIndex, side);
        carrierHealthByPlayer.put(playerIndex, getCarrierMaxHealth(playerIndex));
    }

    private int getCarrierMaxHealth(int playerIndex) {
        String side = playerSides.get(playerIndex);
        if ("Aereo".equals(side)) {
            return AERIAL_CARRIER_HITS_TO_DESTROY;
        }
        if ("Naval".equals(side)) {
            return NAVAL_CARRIER_HITS_TO_DESTROY;
        }
        return carrierHitsToDestroy;
    }


    private HexCoord getDroneSpawnPosition(int playerIndex, int droneIndex, int droneCount) {
        HexCoord carrierSpawn = playerSpawnAnchors.computeIfAbsent(playerIndex, this::getCarrierSpawnPosition);
        if (droneCount <= 1) {
            return new HexCoord(carrierSpawn.getX(), carrierSpawn.getY());
        }

        int ringIndex = droneIndex / 6;
        int slotInRing = droneIndex % 6;
        double ringRadius = 16.0 * (ringIndex + 1);
        double angle = (Math.PI * 2.0 * slotInRing) / 6.0;

        double x = carrierSpawn.getX() + (Math.cos(angle) * ringRadius);
        double y = carrierSpawn.getY() + (Math.sin(angle) * ringRadius);
        return new HexCoord(x, y);
    }

    private HexCoord getCarrierSpawnPosition(int playerIndex) {
        double minY = CARRIER_SPAWN_Y_MARGIN;
        double maxY = Math.max(minY, WORLD_HEIGHT - CARRIER_SPAWN_Y_MARGIN);
        double y = ThreadLocalRandom.current().nextDouble(minY, maxY + 1.0);

        double leftX = CARRIER_EDGE_MARGIN;
        double rightX = Math.max(CARRIER_EDGE_MARGIN, WORLD_WIDTH - CARRIER_EDGE_MARGIN);

        double x = playerIndex == 0 ? leftX : rightX;
        return new HexCoord(x, y);
    }


    private void applyVisionRange(Drone drone) {
        if (drone instanceof NavalDrone) {
            drone.setVisionRange(navalVisionRange);
            return;
        }
        drone.setVisionRange(aerialVisionRange);
    }

    /**
     * Remove a player by session ID. Returns the removed player, or null.
     */
    public synchronized PlayerState removePlayer(String sessionId) {
        Iterator<PlayerState> it = players.iterator();
        while (it.hasNext()) {
            PlayerState p = it.next();
            if (p.getSessionId().equals(sessionId)) {
                it.remove();
                playerSpawnAnchors.remove(p.getPlayerIndex());
                carrierHealthByPlayer.remove(p.getPlayerIndex());
                carrierPositions.remove(p.getPlayerIndex());
                return p;
            }
        }
        return null;
    }

    /**
     * Move a drone. Returns true if the move was valid.
     */
    public synchronized boolean moveDrone(String sessionId, int droneIndex, double x, double y) {
        PlayerState player = getPlayerBySession(sessionId);
        if (player == null) return false;
        if (droneIndex < 0 || droneIndex >= player.getDrones().size()) return false;

        Drone drone = player.getDrones().get(droneIndex);
        if (!drone.isAlive()) return false;

        // Check if target position is occupied by another unit
        HexCoord targetPos = new HexCoord(x, y);
        if (isPositionOccupied(targetPos, drone)) {
            log.warn("Move blocked: target position occupied at ({}, {})", x, y);
            return false;
        }

        drone.setPosition(targetPos);
        // First successful move = drone is deployed onto the battlefield.
        // Restore full fuel first so hangar wait time doesn't penalise the player.
        if (!drone.isDeployed()) {
            drone.setDeployed(true);
            drone.setFuel(drone.getMaxFuel());
        }
        consumeMovementFuel(drone);
        return true;
    }

    public synchronized boolean consumeMovementFuel(Drone drone) {
        if (drone == null || !drone.isAlive()) {
            return false;
        }
        drone.consumeFuel(MOVEMENT_FUEL_COST);
        if (drone.getFuel() <= 0) {
            destroyDroneByFuel(drone);
            return true;
        }
        return false;
    }

    public synchronized List<Integer> consumeIdleFuelForCurrentPlayer() {
        PlayerState activePlayer = getPlayerByIndex(currentTurn);
        if (activePlayer == null) {
            return List.of();
        }

        List<Integer> destroyedDroneIndexes = new ArrayList<>();
        List<Drone> drones = activePlayer.getDrones();
        for (int i = 0; i < drones.size(); i++) {
            Drone drone = drones.get(i);
            // Only consume idle fuel for drones that are deployed on the battlefield
            if (!drone.isAlive() || !drone.isDeployed()) {
                continue;
            }
            drone.consumeFuel(IDLE_FUEL_COST);
            if (drone.getFuel() <= 0) {
                destroyDroneByFuel(drone);
                destroyedDroneIndexes.add(i);
            }
        }
        return destroyedDroneIndexes;
    }

    private void destroyDroneByFuel(Drone drone) {
        if (!drone.isAlive()) {
            return;
        }
        drone.setCurrentHp(0);
        drone.receiveDamage(1);
    }

    /**
     * Recall a deployed drone back to its carrier, restoring fuel and missiles.
     * Returns true if the recall was valid and applied.
     */
    public synchronized boolean recallDrone(String sessionId, int droneIndex) {
        PlayerState player = getPlayerBySession(sessionId);
        if (player == null) return false;
        if (droneIndex < 0 || droneIndex >= player.getDrones().size()) return false;

        Drone drone = player.getDrones().get(droneIndex);
        if (!drone.isAlive()) return false;
        if (!drone.isDeployed()) return false; // already in hangar
        if (isCarrierDestroyed(player.getPlayerIndex())) return false;

        drone.setDeployed(false);
        drone.setFuel(drone.getMaxFuel());
        if (drone instanceof NavalDrone navalDrone) {
            navalDrone.setMissiles(NavalDrone.DEFAULT_MISSILES);
        }
        return true;
    }


    public synchronized void assignSessionToPlayer(int playerIndex, String sessionId) {
        PlayerState player = getPlayerByIndex(playerIndex);
        if (player != null) {
            player.setSessionId(sessionId);
        }
    }

    public synchronized boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }
    
    /**
     * Get the side chosen by a player.
     */
    public synchronized String getPlayerSide(int playerIndex) {
        return playerSides.get(playerIndex);
    }
    
    /**
     * Set the side for a player.
     */
    public synchronized void setPlayerSide(int playerIndex, String side) {
        playerSides.put(playerIndex, side);
    }
    
    /**
     * Check if both players have selected their sides.
     */
    public synchronized boolean bothSidesSelected() {
        return playerSides.size() == MAX_PLAYERS;
    }

    /**
     * Reset the room so new players can join.
     */
    public synchronized void reset() {
        players.clear();
        playerSides.clear();
        carrierHealthByPlayer.clear();
        carrierPositions.clear();
        gameStarted = false;
        currentTurn = 0;
        actionsRemaining = actionsPerTurn;
    }

    public synchronized int damageCarrier(int playerIndex, int attacks) {
        int currentHealth = carrierHealthByPlayer.getOrDefault(playerIndex, getCarrierMaxHealth(playerIndex));
        int updatedHealth = Math.max(0, currentHealth - Math.max(0, attacks));
        carrierHealthByPlayer.put(playerIndex, updatedHealth);
        if (updatedHealth <= 0) {
            destroyUndeployedDronesInCarrier(playerIndex);
        }
        return updatedHealth;
    }

    private void destroyUndeployedDronesInCarrier(int playerIndex) {
        PlayerState player = getPlayerByIndex(playerIndex);
        if (player == null) {
            return;
        }

        for (Drone drone : player.getDrones()) {
            if (!drone.isAlive() || drone.isDeployed()) {
                continue;
            }
            drone.setCurrentHp(0);
            drone.receiveDamage(1);
        }
    }

    public synchronized int getCarrierHealth(int playerIndex) {
        return carrierHealthByPlayer.getOrDefault(playerIndex, getCarrierMaxHealth(playerIndex));
    }

    public synchronized int getCarrierHitsToDestroy() {
        return carrierHitsToDestroy;
    }

    public synchronized boolean isCarrierDestroyed(int playerIndex) {
        return getCarrierHealth(playerIndex) <= 0;
    }

    // ========== Turn Management ==========

    public synchronized void startGame() {
        gameStarted = true;
        currentTurn = 0;
        actionsRemaining = actionsPerTurn;
    }

    public synchronized boolean allPlayersConnected() {
        if (players.size() < MAX_PLAYERS) return false;
        return players.stream().allMatch(p -> p.getSessionId() != null);
    }

    public synchronized boolean isGameStarted() {
        return gameStarted;
    }

    public synchronized int getCurrentTurn() {
        return currentTurn;
    }

    public synchronized int getActionsRemaining() {
        return actionsRemaining;
    }

    public synchronized int getActionsPerTurn() {
        return actionsPerTurn;
    }

    public synchronized int getAerialVisionRange() {
        return aerialVisionRange;
    }

    public synchronized int getNavalVisionRange() {
        return navalVisionRange;
    }

    public synchronized boolean isPlayerTurn(int playerIndex) {
        return gameStarted && currentTurn == playerIndex;
    }

    public synchronized boolean isPlayerTurn(String sessionId) {
        PlayerState player = getPlayerBySession(sessionId);
        return player != null && isPlayerTurn(player.getPlayerIndex());
    }

    public synchronized PlayerState getPlayerBySession(String sessionId) {
        if (sessionId == null) return null;

        for (PlayerState p : players) {
            if (sessionId.equals(p.getSessionId())) {
                return p;
            }
        }
        return null;
    }

    /**
     * Consume an action. Returns true if action was available.
     */
    public synchronized boolean useAction() {
        if (actionsRemaining > 0) {
            actionsRemaining--;
            return true;
        }
        return false;
    }

    /**
     * Consume multiple actions atomically. Returns true only if enough actions were available.
     */
    public synchronized boolean useActions(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (actionsRemaining < amount) {
            return false;
        }
        actionsRemaining -= amount;
        return true;
    }

    /**
     * End the current turn and switch to the other player.
     */
    public synchronized void endTurn() {
        currentTurn = (currentTurn + 1) % MAX_PLAYERS;
        actionsRemaining = actionsPerTurn;
    }

    // ========== Player/Drone Accessors ==========

    public synchronized PlayerState getPlayerByIndex(int index) {
        if (index >= 0 && index < players.size()) {
            return players.get(index);
        }
        return null;
    }

    public synchronized Drone getDrone(int playerIndex, int droneIndex) {
        PlayerState player = getPlayerByIndex(playerIndex);
        if (player == null) return null;
        List<Drone> drones = player.getDrones();
        if (droneIndex < 0 || droneIndex >= drones.size()) return null;
        return drones.get(droneIndex);
    }

    public synchronized List<PlayerState> getPlayers() {
        return new ArrayList<>(players);
    }


    public synchronized HexCoord getCarrierPosition(int playerIndex) {
        return carrierPositions.computeIfAbsent(playerIndex, idx -> playerSpawnAnchors.computeIfAbsent(idx, this::getCarrierSpawnPosition));
    }

    public synchronized boolean moveCarrier(String sessionId, double x, double y) {
        PlayerState player = getPlayerBySession(sessionId);
        if (player == null) return false;

        HexCoord current = getCarrierPosition(player.getPlayerIndex());
        HexCoord target = new HexCoord(x, y);
        if (isPositionOccupiedByCarrierOrDrone(target, current, null)) {
            return false;
        }

        carrierPositions.put(player.getPlayerIndex(), target);
        playerSpawnAnchors.put(player.getPlayerIndex(), target);
        return true;
    }

    /**
     * Check if a position is occupied by any drone (except the ignoreDrone).
     * Uses squared Euclidean distance with tolerance of 15 pixels.
     * 
     * @param position Target world position
     * @param ignoreDrone Drone to ignore in the check (can be null)
     * @return true if position is occupied
     */
    public synchronized boolean isPositionOccupied(HexCoord position, Drone ignoreDrone) {
        return isPositionOccupiedByCarrierOrDrone(position, null, ignoreDrone);
    }

    private boolean isPositionOccupiedByCarrierOrDrone(HexCoord position, HexCoord ignoreCarrierPosition, Drone ignoreDrone) {
        if (position == null) return false;

        final double TOLERANCE_SQ = 15 * 15;

        for (PlayerState player : players) {
            for (Drone drone : player.getDrones()) {
                if (!drone.isAlive() || drone == ignoreDrone) continue;
                HexCoord dronePos = drone.getPosition();
                if (dronePos == null) continue;
                double dx = dronePos.getX() - position.getX();
                double dy = dronePos.getY() - position.getY();
                if ((dx * dx + dy * dy) < TOLERANCE_SQ) return true;
            }
        }

        for (HexCoord carrierPos : carrierPositions.values()) {
            if (carrierPos == null || carrierPos == ignoreCarrierPosition) continue;
            double dx = carrierPos.getX() - position.getX();
            double dy = carrierPos.getY() - position.getY();
            if ((dx * dx + dy * dy) < TOLERANCE_SQ) return true;
        }

        return false;
    }

    /**
     * Serialize the full game state to a Map (for JSON).
     */
    public synchronized Map<String, Object> toStateMap() {

        log.info("[GameRoom] -> begin toStateMap {} ");

        List<Map<String, Object>> playerMaps = new ArrayList<>();
        for (PlayerState p : players) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("playerIndex", p.getPlayerIndex());
            pm.put("side", p.getSide());
            pm.put("carrierHealth", getCarrierHealth(p.getPlayerIndex()));
            pm.put("carrierMaxHealth", getCarrierMaxHealth(p.getPlayerIndex()));
            pm.put("carrierDestroyed", isCarrierDestroyed(p.getPlayerIndex()));
            HexCoord carrierPos = getCarrierPosition(p.getPlayerIndex());
            pm.put("carrierX", carrierPos.getX());
            pm.put("carrierY", carrierPos.getY());
            log.info("Saving player {} side={}", p.getPlayerIndex(), p.getSide());
            List<Map<String, Object>> droneMaps = new ArrayList<>();
            for (Drone d : p.getDrones()) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("x", d.getPosition().getX());
                dm.put("y", d.getPosition().getY());
                dm.put("health", d.getCurrentHp());
                dm.put("maxHealth", d.getMaxHp());
                dm.put("attackDamage", d.getWeapon().getDamage());
                dm.put("attackRange", d.getWeapon().getRange());
                dm.put("visionRange", d.getVisionRange());
                dm.put("alive", d.isAlive());
                dm.put("fuel", d.getFuel());
                dm.put("maxFuel", d.getMaxFuel());
                // Add drone type for frontend rendering
                dm.put("droneType", d instanceof NavalDrone ? "Naval" : "Aereo");
                dm.put("deployed", d.isDeployed());
                if (d instanceof NavalDrone navalDrone) {
                    dm.put("missiles", navalDrone.getMissiles());
                } else {
                    // Aerial drones use weapon ammo (bombs). Expose it as 'missiles' for a single
                    // generic "munición" field in the frontend.
                    dm.put("missiles", d.getWeapon() != null ? d.getWeapon().getAmmo() : 0);
                }
                droneMaps.add(dm);
            }
            pm.put("drones", droneMaps);
            playerMaps.add(pm);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("players", playerMaps);
        state.put("currentTurn", currentTurn);
        state.put("actionsRemaining", actionsRemaining);
        state.put("actionsPerTurn", actionsPerTurn);
        state.put("aerialVisionRange", aerialVisionRange);
        state.put("navalVisionRange", navalVisionRange);
        state.put("carrierHitsToDestroy", carrierHitsToDestroy);
        state.put("gameStarted", gameStarted);
        
        log.info("[GameRoom] -> End toStateMap");
        
        return state;
    }

    @SuppressWarnings("unchecked")
    public static GameRoom fromStateMap(String roomId, Map<String, Object> stateMap) {
        log.info("[GameRoom] -> begin fromStateMap ");
        log.info("[GameRoom] -> restoring stateMap {}", stateMap);

        if (stateMap == null) {
            throw new IllegalArgumentException("stateMap is required");
        }

        Object playersObj = stateMap.get("players");
        if (!(playersObj instanceof List<?> playersList)) {
            throw new IllegalArgumentException("players list is required");
        }
        if (playersList.isEmpty() || playersList.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("invalid players count");
        }

        boolean gameStarted = getBooleanField(stateMap, "gameStarted");
        int currentTurn = getIntField(stateMap, "currentTurn");
        int actionsPerTurn = getOptionalPositiveIntField(stateMap, "actionsPerTurn", DEFAULT_ACTIONS_PER_TURN);
        int aerialVisionRange = getOptionalNonNegativeIntField(stateMap, "aerialVisionRange", DEFAULT_AERIAL_VISION_RANGE);
        int navalVisionRange = getOptionalNonNegativeIntField(stateMap, "navalVisionRange", DEFAULT_NAVAL_VISION_RANGE);
        int carrierHitsToDestroy = getOptionalPositiveIntField(stateMap, "carrierHitsToDestroy", DEFAULT_CARRIER_HITS_TO_DESTROY);
        int actionsRemaining = getIntField(stateMap, "actionsRemaining");
        if (actionsRemaining < 0 || actionsRemaining > actionsPerTurn) {
            throw new IllegalArgumentException("actionsRemaining out of range");
        }
        if (currentTurn < 0 || currentTurn >= playersList.size()) {
            throw new IllegalArgumentException("currentTurn out of range");
        }
        if (gameStarted && playersList.size() != MAX_PLAYERS) {
            throw new IllegalArgumentException("started games must have two players");
        }

        GameRoom room = new GameRoom(roomId, actionsPerTurn, aerialVisionRange, navalVisionRange, carrierHitsToDestroy);
        room.gameStarted = gameStarted;
        room.currentTurn = currentTurn;
        room.actionsRemaining = actionsRemaining;

        boolean[] seenIndexes = new boolean[MAX_PLAYERS];
        for (Object playerObj : playersList) {
            if (!(playerObj instanceof Map<?, ?> rawPlayer)) {
                throw new IllegalArgumentException("invalid player payload");
            }

            int playerIndex = getIntField(rawPlayer, "playerIndex");
            if (playerIndex < 0 || playerIndex >= MAX_PLAYERS || seenIndexes[playerIndex]) {
                throw new IllegalArgumentException("invalid playerIndex");
            }
            seenIndexes[playerIndex] = true;

            String side = getStringField(rawPlayer, "side");
            if (!"Naval".equals(side) && !"Aereo".equals(side)) {
                throw new IllegalArgumentException("side must be Naval or Aereo");
            }

            Object dronesObj = rawPlayer.get("drones");
            if (!(dronesObj instanceof List<?> dronesList) || dronesList.isEmpty()) {
                throw new IllegalArgumentException("drones list is required");
            }

            List<Drone> drones = new ArrayList<>();
            for (Object droneObj : dronesList) {
                if (!(droneObj instanceof Map<?, ?> rawDrone)) {
                    throw new IllegalArgumentException("invalid drone payload");
                }

                String droneType = getStringField(rawDrone, "droneType");
                if (!"Naval".equals(droneType) && !"Aereo".equals(droneType)) {
                    throw new IllegalArgumentException("droneType must be Naval or Aereo");
                }

                Drone drone = "Naval".equals(droneType) ? new NavalDrone() : new AerialDrone();
                drone.setId(UUID.randomUUID().toString());
                drone.setOwnerPlayerId(playerIndex);

                double x = getDoubleField(rawDrone, "x");
                double y = getDoubleField(rawDrone, "y");
                if (!Double.isFinite(x) || !Double.isFinite(y)) {
                    throw new IllegalArgumentException("drone coordinates must be finite");
                }
                drone.setPosition(new HexCoord(x, y));

                int health = getIntField(rawDrone, "health");
                boolean alive = getBooleanField(rawDrone, "alive");
                if (health < 0 || health > drone.getMaxHp()) {
                    throw new IllegalArgumentException("health out of range");
                }
                if (alive && health <= 0) {
                    throw new IllegalArgumentException("alive drones must have positive health");
                }

                int visionRange = getOptionalNonNegativeIntField(rawDrone, "visionRange", "Naval".equals(droneType) ? room.navalVisionRange : room.aerialVisionRange);
                drone.setVisionRange(visionRange);

                int maxFuel = getOptionalNonNegativeIntField(rawDrone, "maxFuel", drone.getMaxFuel());
                drone.setMaxFuel(maxFuel);
                int fuel = getOptionalNonNegativeIntField(rawDrone, "fuel", drone.getFuel());
                if (fuel > drone.getMaxFuel()) {
                    throw new IllegalArgumentException("fuel out of range");
                }
                drone.setFuel(fuel);

                if (drone instanceof NavalDrone navalDrone) {
                    int missiles = getOptionalNonNegativeIntField(rawDrone, "missiles", NavalDrone.DEFAULT_MISSILES);
                    navalDrone.setMissiles(missiles);
                } else {
                    // Restore aerial drone weapon ammo (saved as 'missiles' for frontend/back-compat).
                    if (drone.getWeapon() != null) {
                        int ammo = getOptionalNonNegativeIntField(rawDrone, "missiles", drone.getWeapon().getAmmo());
                        drone.getWeapon().setAmmo(ammo);
                    }
                }

                drone.setCurrentHp(health);
                if (!alive) {
                    drone.setCurrentHp(0);
                    drone.receiveDamage(1);
                }

                // Restore deployed state (default false for backward-compat with old saves)
                Object deployedObj = rawDrone.get("deployed");
                if (deployedObj instanceof Boolean deployedBool) {
                    drone.setDeployed(deployedBool);
                }

                drones.add(drone);
            }
            
            PlayerState player = new PlayerState(
                null, // sessionId todavía no asignado
                playerIndex,
                drones
            );
            
                        
            log.info("[GameRoom] -> el playerState es {}", player);

            player.setSide(side);
            room.players.add(player);
            room.playerSides.put(playerIndex, side);
            int carrierMaxHealth = room.getCarrierMaxHealth(playerIndex);
            int carrierHealth = getOptionalNonNegativeIntField(rawPlayer, "carrierHealth", carrierMaxHealth);
            if (carrierHealth > carrierMaxHealth) {
                throw new IllegalArgumentException("carrierHealth out of range");
            }
            room.carrierHealthByPlayer.put(playerIndex, carrierHealth);
            double carrierX = getOptionalDoubleField(rawPlayer, "carrierX", room.getCarrierPosition(playerIndex).getX());
            double carrierY = getOptionalDoubleField(rawPlayer, "carrierY", room.getCarrierPosition(playerIndex).getY());
            HexCoord restoredCarrier = new HexCoord(carrierX, carrierY);
            room.carrierPositions.put(playerIndex, restoredCarrier);
            room.playerSpawnAnchors.put(playerIndex, restoredCarrier);
        }

        log.info("[GameRoom] -> End fromStateMap");
        
        return room;
    }


    private static int getIntField(Map<?, ?> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return ((Number) value).intValue();
    }

    private static double getDoubleField(Map<?, ?> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return ((Number) value).doubleValue();
    }

    private static double getOptionalDoubleField(Map<?, ?> map, String field, double defaultValue) {
        Object value = map.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return ((Number) value).doubleValue();
    }

    private static int getOptionalPositiveIntField(Map<?, ?> map, String field, int defaultValue) {
        Object value = map.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        int parsed = ((Number) value).intValue();
        if (parsed <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
        return parsed;
    }

    private static int getOptionalNonNegativeIntField(Map<?, ?> map, String field, int defaultValue) {
        Object value = map.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        int parsed = ((Number) value).intValue();
        if (parsed < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
        return parsed;
    }

    private static String getStringField(Map<?, ?> map, String field) {
        Object value = map.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.toString();
    }

    private static boolean getBooleanField(Map<?, ?> map, String field) {
        Object value = map.get(field);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return (Boolean) value;
    }

    /**
     * Restore state from another GameRoom (used when loading saved games).
     * Copies players, sides, turn state from source into this room.
     */
    public synchronized void restoreFrom(GameRoom source) {
         log.info("[GameRoom] -> begin restoreFrom ");
         log.info("[GameRoom] -> restoring  {}", source);
        this.players.clear();
        this.players.addAll(source.players);
        this.playerSides.clear();
        this.playerSides.putAll(source.playerSides);
        this.gameStarted = source.gameStarted;
        this.currentTurn = source.currentTurn;
        this.actionsPerTurn = source.actionsPerTurn;
        this.aerialVisionRange = source.aerialVisionRange;
        this.navalVisionRange = source.navalVisionRange;
        this.actionsRemaining = source.actionsRemaining;
        this.carrierHitsToDestroy = source.carrierHitsToDestroy;
        this.carrierHealthByPlayer.clear();
        this.carrierHealthByPlayer.putAll(source.carrierHealthByPlayer);
        this.carrierPositions.clear();
        this.carrierPositions.putAll(source.carrierPositions);
        log.info("[GameRoom] -> End restoreFrom ");
    }
}
