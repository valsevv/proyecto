package com.example.proyect.GameTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api. Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.proyect.game.GameRoom;
import com.example.proyect.game.PlayerState;
import com.example.proyect.game.units.drone.AerialDrone;
import com.example.proyect.game.units.drone.Drone;
import com.example.proyect.game.units.drone.NavalDrone;

class GameRoomTest {

    @Test
    void shouldCreateGameRoomWithDefaultActions() {
        GameRoom room = new GameRoom("test-room");

        assertEquals("test-room", room.getRoomId());
        assertEquals(10, room.getActionsPerTurn());
        assertEquals(4, room.getAerialVisionRange());
        assertEquals(3, room.getNavalVisionRange());
        assertFalse(room.isGameStarted());
    }

    @Test
    void shouldCreateGameRoomWithCustomActions() {
        GameRoom room = new GameRoom("custom-room", 20, 5, 4);

        assertEquals(20, room.getActionsPerTurn());
        assertEquals(5, room.getAerialVisionRange());
        assertEquals(4, room.getNavalVisionRange());
    }

    @Test
    void shouldRejectInvalidActionsPerTurn() {
        assertThrows(IllegalArgumentException.class, () ->
            new GameRoom("invalid", 0)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new GameRoom("invalid", -5)
        );
    }

    @Test
    void shouldRejectNegativeVisionRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new GameRoom("invalid", 10, -1, 3)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new GameRoom("invalid", 10, 4, -1)
        );
    }

    @Test
    void shouldAddPlayers() {
        GameRoom room = new GameRoom("room-1");

        PlayerState player1 = room.addPlayer("session-1");
        assertNotNull(player1);
        assertEquals("session-1", player1.getSessionId());
        assertEquals(0, player1.getPlayerIndex());
        assertEquals(12, player1.getDrones().size()); // default 12 aerial drones

        PlayerState player2 = room.addPlayer("session-2");
        assertNotNull(player2);
        assertEquals(1, player2.getPlayerIndex());

        assertTrue(room.isFull());
    }

    @Test
    void shouldRejectThirdPlayer() {
        GameRoom room = new GameRoom("room-full");
        room.addPlayer("session-1");
        room.addPlayer("session-2");

        PlayerState thirdPlayer = room.addPlayer("session-3");
        assertNull(thirdPlayer);
    }

    @Test
    void shouldRemovePlayer() {
        GameRoom room = new GameRoom("room-remove");
        room.addPlayer("session-1");
        room.addPlayer("session-2");

        PlayerState removed = room.removePlayer("session-1");
        assertNotNull(removed);
        assertEquals("session-1", removed.getSessionId());

        assertFalse(room.isFull());
    }

    @Test
    void shouldReturnNullWhenRemovingNonExistentPlayer() {
        GameRoom room = new GameRoom("room-notfound");
        room.addPlayer("session-1");

        PlayerState removed = room.removePlayer("session-999");
        assertNull(removed);
    }

    @Test
    void shouldSetAndGetPlayerSide() {
        GameRoom room = new GameRoom("room-sides");
        room.addPlayer("session-1");
        room.addPlayer("session-2");

        room.setPlayerSide(0, "Naval");
        room.setPlayerSide(1, "Aereo");

        assertEquals("Naval", room.getPlayerSide(0));
        assertEquals("Aereo", room.getPlayerSide(1));
        assertTrue(room.bothSidesSelected());
    }

    @Test
    void shouldCreateDronesForNavalSide() {
        GameRoom room = new GameRoom("room-naval");
        PlayerState player = room.addPlayer("session-1");

        room.createDronesForSide(0, "Naval");

        assertEquals("Naval", player.getSide());
        assertEquals(6, player.getDrones().size()); // Naval has 6 drones
        assertTrue(player.getDrones().get(0) instanceof NavalDrone);
        assertEquals(3, player.getDrones().get(0).getVisionRange()); // Naval vision range
    }

    @Test
    void shouldCreateDronesForAerialSide() {
        GameRoom room = new GameRoom("room-aerial");
        PlayerState player = room.addPlayer("session-1");

        room.createDronesForSide(0, "Aereo");

        assertEquals("Aereo", player.getSide());
        assertEquals(12, player.getDrones().size()); // Aerial has 12 drones
        assertTrue(player.getDrones().get(0) instanceof AerialDrone);
        assertEquals(4, player.getDrones().get(0).getVisionRange()); // Aerial vision range
    }


    @Test
    void shouldSpawnPlayersOnOppositeSidesWithinMapBounds() {
        GameRoom room = new GameRoom("room-spawn");
        PlayerState p0 = room.addPlayer("session-1");
        PlayerState p1 = room.addPlayer("session-2");

        Drone p0Drone = p0.getDrones().get(0);
        Drone p1Drone = p1.getDrones().get(0);

        assertTrue(p0Drone.getPosition().getX() < 1000);
        assertTrue(p1Drone.getPosition().getX() > 2000);

        assertTrue(p0Drone.getPosition().getY() >= 220);
        assertTrue(p0Drone.getPosition().getY() <= 2180);
        assertTrue(p1Drone.getPosition().getY() >= 220);
        assertTrue(p1Drone.getPosition().getY() <= 2180);
    }

    @Test
    void shouldStartGame() {
        GameRoom room = new GameRoom("room-start");
        room.addPlayer("session-1");
        room.addPlayer("session-2");

        assertFalse(room.isGameStarted());

        room.startGame();

        assertTrue(room.isGameStarted());
        assertEquals(0, room.getCurrentTurn());
        assertEquals(10, room.getActionsRemaining());
    }

    @Test
    void shouldMoveDrone() {
        GameRoom room = new GameRoom("room-move");
        PlayerState player = room.addPlayer("session-1");

        Drone drone = player.getDrones().get(0);
        int initialFuel = drone.getFuel();

        boolean moved = room.moveDrone("session-1", 0, 100, 200);

        assertTrue(moved);
        assertEquals(100, drone.getPosition().getX(), 0.01);
        assertEquals(200, drone.getPosition().getY(), 0.01);
        assertEquals(initialFuel - 1, drone.getFuel()); // Fuel consumed
    }

    @Test
    void shouldNotMoveDeadDrone() {
        GameRoom room = new GameRoom("room-dead");
        PlayerState player = room.addPlayer("session-1");

        Drone drone = player.getDrones().get(0);
        drone.receiveDamage(1000); // Kill the drone

        boolean moved = room.moveDrone("session-1", 0, 100, 200);

        assertFalse(moved);
    }

    @Test
    void shouldNotMoveInvalidDroneIndex() {
        GameRoom room = new GameRoom("room-invalid");
        room.addPlayer("session-1");

        boolean moved = room.moveDrone("session-1", 999, 100, 200);

        assertFalse(moved);
    }

    @Test
    void shouldConsumeIdleFuelForCurrentPlayer() {
        GameRoom room = new GameRoom("room-idle");
        room.addPlayer("session-1");
        room.addPlayer("session-2");
        room.startGame();

        PlayerState currentPlayer = room.getPlayerByIndex(0);
        Drone drone = currentPlayer.getDrones().get(0);
        int initialFuel = drone.getFuel();

        List<Integer> destroyed = room.consumeIdleFuelForCurrentPlayer();

        assertEquals(initialFuel - 1, drone.getFuel());
        assertTrue(destroyed.isEmpty()); // Drone should still be alive
    }

    @Test
    void shouldDestroyDroneWhenIdleFuelReachesZero() {
        GameRoom room = new GameRoom("room-idle-destroy");
        room.addPlayer("session-1");
        room.addPlayer("session-2");
        room.startGame();

        PlayerState currentPlayer = room.getPlayerByIndex(0);
        Drone drone = currentPlayer.getDrones().get(0);
        drone.setFuel(1); // Set to minimum

        List<Integer> destroyed = room.consumeIdleFuelForCurrentPlayer();

        assertEquals(0, drone.getFuel());
        assertFalse(drone.isAlive());
        assertTrue(destroyed.contains(0));
    }

    @Test
    void shouldEndTurnAndSwitchPlayers() {
        GameRoom room = new GameRoom("room-turn", 10);
        room.addPlayer("session-1");
        room.addPlayer("session-2");
        room.startGame();

        assertEquals(0, room.getCurrentTurn());
        assertEquals(10, room.getActionsRemaining());

        room.endTurn();

        assertEquals(1, room.getCurrentTurn());
        assertEquals(10, room.getActionsRemaining()); // Reset
    }

    @Test
    void shouldWrapTurnBackToZero() {
        GameRoom room = new GameRoom("room-wrap");
        room.addPlayer("session-1");
        room.addPlayer("session-2");
        room.startGame();

        room.endTurn(); // Turn 1
        room.endTurn(); // Back to turn 0

        assertEquals(0, room.getCurrentTurn());
    }

    @Test
    void shouldResetRoom() {
        GameRoom room = new GameRoom("room-reset");
        room.addPlayer("session-1");
        room.addPlayer("session-2");
        room.setPlayerSide(0, "Naval");
        room.setPlayerSide(1, "Aereo");
        room.startGame();

        room.reset();

        assertEquals(0, room.getPlayers().size());
        assertFalse(room.bothSidesSelected());
        assertFalse(room.isGameStarted());
        assertEquals(0, room.getCurrentTurn());
    }

    @Test
    void shouldGetPlayerByIndex() {
        GameRoom room = new GameRoom("room-index");
        room.addPlayer("session-1");
        room.addPlayer("session-2");

        PlayerState player0 = room.getPlayerByIndex(0);
        PlayerState player1 = room.getPlayerByIndex(1);
        PlayerState playerInvalid = room.getPlayerByIndex(2);

        assertNotNull(player0);
        assertNotNull(player1);
        assertNull(playerInvalid);
    }

    @Test
    void shouldGetPlayerBySession() {
        GameRoom room = new GameRoom("room-session");
        room.addPlayer("session-alpha");
        room.addPlayer("session-beta");

        PlayerState playerAlpha = room.getPlayerBySession("session-alpha");
        PlayerState playerBeta = room.getPlayerBySession("session-beta");
        PlayerState playerNone = room.getPlayerBySession("session-none");

        assertNotNull(playerAlpha);
        assertEquals("session-alpha", playerAlpha.getSessionId());
        assertNotNull(playerBeta);
        assertNull(playerNone);
    }

    @Test
    void shouldAssignSessionToPlayer() {
        GameRoom room = new GameRoom("room-assign");
        room.addPlayer("session-old");

        room.assignSessionToPlayer(0, "session-new");

        PlayerState player = room.getPlayerByIndex(0);
        assertEquals("session-new", player.getSessionId());
    }

    @Test
    void shouldCheckAllPlayersConnected() {
        GameRoom room = new GameRoom("room-connected");
        room.addPlayer("session-1");
        room.addPlayer("session-2");

        room.assignSessionToPlayer(0, "session-1-connected");
        room.assignSessionToPlayer(1, "session-2-connected");

        assertTrue(room.allPlayersConnected());
    }

    @Test
    void shouldReturnFalseWhenNotAllPlayersConnected() {
        GameRoom room = new GameRoom("room-partial");
        room.addPlayer("session-1");
        room.addPlayer("session-2");

        room.assignSessionToPlayer(0, "session-1-connected");
        room.assignSessionToPlayer(1, null);

        assertFalse(room.allPlayersConnected());
    }

    @Test
    void shouldReturnFalseWhenNotEnoughPlayers() {
        GameRoom room = new GameRoom("room-few");
        room.addPlayer("session-1");

        assertFalse(room.allPlayersConnected());
    }

    @Test
    void shouldSerializeToStateMap() {
        GameRoom room = new GameRoom("room-serialize", 15, 5, 4);
        room.addPlayer("session-1");
        room.addPlayer("session-2");
        room.createDronesForSide(0, "Naval");
        room.createDronesForSide(1, "Aereo");
        room.startGame();

        var stateMap = room.toStateMap();

        assertNotNull(stateMap);
        assertEquals(true, stateMap.get("gameStarted"));
        assertEquals(0, stateMap.get("currentTurn"));
        assertEquals(15, stateMap.get("actionsPerTurn"));
        assertEquals(5, stateMap.get("aerialVisionRange"));
        assertEquals(4, stateMap.get("navalVisionRange"));
        assertEquals(15, stateMap.get("actionsRemaining"));

        @SuppressWarnings("unchecked")
        List<Object> playersList = (List<Object>) stateMap.get("players");
        assertEquals(2, playersList.size());
    }
}
