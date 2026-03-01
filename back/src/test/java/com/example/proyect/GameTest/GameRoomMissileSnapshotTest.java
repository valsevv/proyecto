package com.example.proyect.GameTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.example.proyect.game.GameRoom;
import com.example.proyect.game.PlayerState;
import com.example.proyect.game.units.drone.Drone;
import com.example.proyect.game.units.drone.NavalDrone;

class GameRoomMissileSnapshotTest {

    @Test
        void shouldPersistAndRestoreNavalDroneMissilesInSnapshot() {
        GameRoom room = new GameRoom("room-test");
        PlayerState player = room.addPlayer("session-1");
                room.createDronesForSide(player.getPlayerIndex(), "Naval");
        Drone drone = player.getDrones().get(0);
                assertTrue(drone instanceof NavalDrone);

                ((NavalDrone) drone).setMissiles(1);

        Map<String, Object> snapshot = room.toStateMap();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) snapshot.get("players");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> drones = (List<Map<String, Object>>) players.get(0).get("drones");

        assertEquals(1, drones.get(0).get("missiles"));

        Map<String, Object> restoreSnapshot = Map.of(
                "gameStarted", false,
                "currentTurn", 0,
                "actionsPerTurn", 10,
                "aerialVisionRange", 4,
                "navalVisionRange", 3,
                "actionsRemaining", 10,
                "players", List.of(
                        Map.of(
                                "playerIndex", 0,
                                "side", "Naval",
                                "drones", List.of(
                                        Map.of(
                                                "x", 10.0,
                                                "y", 10.0,
                                                "health", 100,
                                                "alive", true,
                                                "droneType", "Naval",
                                                "missiles", 1
                                        )
                                )
                        )
                )
        );

        GameRoom restored = GameRoom.fromStateMap("room-restored", restoreSnapshot);
        Drone restoredDrone = restored.getPlayerByIndex(0).getDrones().get(0);
                assertTrue(restoredDrone instanceof NavalDrone);
                assertEquals(1, ((NavalDrone) restoredDrone).getMissiles());
    }
}
