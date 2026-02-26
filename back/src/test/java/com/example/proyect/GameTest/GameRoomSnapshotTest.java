package com.example.proyect.GameTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.proyect.game.GameRoom;
import com.example.proyect.game.PlayerState;
import com.example.proyect.game.units.drone.AerialDrone;
import com.example.proyect.game.units.drone.NavalDrone;

class GameRoomSnapshotTest {

    @Test
    void shouldRestorePlayersDronesAndTurnStateFromSnapshot() {
        Map<String, Object> snapshot = Map.of(
                "gameStarted", true,
                "currentTurn", 1,
                "actionsPerTurn", 30,
                "aerialVisionRange", 8,
                "navalVisionRange", 5,
                "actionsRemaining", 23,
                "players", List.of(
                        Map.of(
                                "playerIndex", 0,
                                "side", "Naval",
                                "drones", List.of(
                                        Map.of("x", 10.0, "y", 11.0, "health", 80, "alive", true, "droneType", "Naval"),
                                        Map.of("x", 12.0, "y", 13.0, "health", 0, "alive", false, "droneType", "Naval")
                                )
                        ),
                        Map.of(
                                "playerIndex", 1,
                                "side", "Aereo",
                                "drones", List.of(
                                        Map.of("x", 20.0, "y", 21.0, "health", 100, "alive", true, "droneType", "Aereo")
                                )
                        )
                )
        );

        GameRoom room = GameRoom.fromStateMap("room-1", snapshot);

        assertTrue(room.isGameStarted());
        assertEquals(1, room.getCurrentTurn());
        assertEquals(30, room.getActionsPerTurn());
        assertEquals(8, room.getAerialVisionRange());
        assertEquals(5, room.getNavalVisionRange());
        assertEquals(23, room.getActionsRemaining());

        PlayerState p0 = room.getPlayerByIndex(0);
        assertEquals("Naval", p0.getSide());
        assertTrue(p0.getDrones().get(0) instanceof NavalDrone);
        assertEquals(80, p0.getDrones().get(0).getCurrentHp());
        assertTrue(p0.getDrones().get(0).isAlive());
        assertFalse(p0.getDrones().get(1).isAlive());

        PlayerState p1 = room.getPlayerByIndex(1);
        assertEquals("Aereo", p1.getSide());
        assertTrue(p1.getDrones().get(0) instanceof AerialDrone);
    }

    @Test
    void shouldFailWhenSnapshotMissesRequiredFields() {
        Map<String, Object> invalidSnapshot = Map.of(
                "gameStarted", true,
                "currentTurn", 0,
                "actionsRemaining", 1,
                "players", List.of(
                        Map.of(
                                "playerIndex", 0,
                                "side", "Naval",
                                "drones", List.of(
                                        Map.of("x", 10.0, "y", 11.0, "health", 80, "droneType", "Naval")
                                )
                        )
                )
        );

        assertThrows(IllegalArgumentException.class, () -> GameRoom.fromStateMap("room-2", invalidSnapshot));
    }

    @Test
    void shouldFailWhenTurnOrPlayerCountIsInconsistent() {
        Map<String, Object> invalidSnapshot = Map.of(
                "gameStarted", true,
                "currentTurn", 1,
                "actionsRemaining", 1,
                "players", List.of(
                        Map.of(
                                "playerIndex", 0,
                                "side", "Aereo",
                                "drones", List.of(
                                        Map.of("x", 1.0, "y", 2.0, "health", 50, "alive", true, "droneType", "Aereo")
                                )
                        )
                )
        );

        assertThrows(IllegalArgumentException.class, () -> GameRoom.fromStateMap("room-3", invalidSnapshot));
    }

    @Test
    void shouldUseDefaultActionsPerTurnWhenSnapshotDoesNotIncludeIt() {
        Map<String, Object> snapshot = Map.of(
                "gameStarted", true,
                "currentTurn", 0,
                "actionsRemaining", 10,
                "players", List.of(
                        Map.of(
                                "playerIndex", 0,
                                "side", "Aereo",
                                "drones", List.of(
                                        Map.of("x", 1.0, "y", 2.0, "health", 50, "alive", true, "droneType", "Aereo")
                                )
                        ),
                        Map.of(
                                "playerIndex", 1,
                                "side", "Naval",
                                "drones", List.of(
                                        Map.of("x", 3.0, "y", 4.0, "health", 60, "alive", true, "droneType", "Naval")
                                )
                        )
                )
        );

        GameRoom room = GameRoom.fromStateMap("room-4", snapshot);

        assertEquals(10, room.getActionsPerTurn());
        assertEquals(4, room.getAerialVisionRange());
        assertEquals(3, room.getNavalVisionRange());
        assertEquals(10, room.getActionsRemaining());
    }

    @Test
    void shouldFailWhenActionsRemainingIsAboveActionsPerTurn() {
        Map<String, Object> invalidSnapshot = Map.of(
                "gameStarted", true,
                "currentTurn", 0,
                "actionsPerTurn", 5,
                "actionsRemaining", 6,
                "players", List.of(
                        Map.of(
                                "playerIndex", 0,
                                "side", "Aereo",
                                "drones", List.of(
                                        Map.of("x", 1.0, "y", 2.0, "health", 50, "alive", true, "droneType", "Aereo")
                                )
                        ),
                        Map.of(
                                "playerIndex", 1,
                                "side", "Naval",
                                "drones", List.of(
                                        Map.of("x", 3.0, "y", 4.0, "health", 60, "alive", true, "droneType", "Naval")
                                )
                        )
                )
        );

        assertThrows(IllegalArgumentException.class, () -> GameRoom.fromStateMap("room-5", invalidSnapshot));
    }


    @Test
    void shouldFailWhenVisionRangesAreNegative() {
        Map<String, Object> invalidSnapshot = Map.of(
                "gameStarted", true,
                "currentTurn", 0,
                "actionsPerTurn", 10,
                "aerialVisionRange", -1,
                "navalVisionRange", 3,
                "actionsRemaining", 5,
                "players", List.of(
                        Map.of(
                                "playerIndex", 0,
                                "side", "Aereo",
                                "drones", List.of(
                                        Map.of("x", 1.0, "y", 2.0, "health", 50, "alive", true, "droneType", "Aereo")
                                )
                        ),
                        Map.of(
                                "playerIndex", 1,
                                "side", "Naval",
                                "drones", List.of(
                                        Map.of("x", 3.0, "y", 4.0, "health", 60, "alive", true, "droneType", "Naval")
                                )
                        )
                )
        );

        assertThrows(IllegalArgumentException.class, () -> GameRoom.fromStateMap("room-6", invalidSnapshot));
    }


    @Test
    void shouldCopyVisionAndActionsConfigOnRestoreFrom() {
        GameRoom target = new GameRoom("target", 10, 4, 3);
        GameRoom source = new GameRoom("source", 15, 7, 2);

        target.restoreFrom(source);

        assertEquals(15, target.getActionsPerTurn());
        assertEquals(7, target.getAerialVisionRange());
        assertEquals(2, target.getNavalVisionRange());
    }

}
