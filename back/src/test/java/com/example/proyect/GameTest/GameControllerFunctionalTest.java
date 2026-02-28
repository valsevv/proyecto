package com.example.proyect.GameTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.VOs.GameResult;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.controller.GameController;
import com.example.proyect.lobby.Lobby;
import com.example.proyect.lobby.service.LobbyService;
import com.example.proyect.persistence.classes.User;

@SpringBootTest
@Transactional
public class GameControllerFunctionalTest {

    @Autowired
    private GameController gameController;

    @Autowired
    private LobbyService lobbyService;

    @Autowired
    private UserService userService;

    @Test
    void shouldJoinGameAndAssignPlayerIndex() {
        User user1 = userService.register("func_player1", "func_player1@mail.com", "password");
        Lobby lobby = lobbyService.createLobby("func_player1", user1.getUserId());

        GameResult result = gameController.joinGame("func_session1", lobby.getLobbyId(), user1.getUserId());

        assertTrue(result.isSuccess());
        assertNotNull(result.getPacket());
        assertEquals(com.example.proyect.websocket.packet.PacketType.WELCOME, result.getPacket().getType());
    }

    @Test
    void shouldBindSessionToUser() {
        gameController.bindSessionUser("func_session_bind", 123L);

        Long userId = gameController.getUserIdBySession("func_session_bind");

        assertEquals(123L, userId);
    }

    @Test
    void shouldSelectSideAndCreateDrones() {
        User user1 = userService.register("func_side_p1", "func_side_p1@mail.com", "password");
        Lobby lobby = lobbyService.createLobby("func_side_p1", user1.getUserId());

        gameController.joinGame("func_side_session", lobby.getLobbyId(), user1.getUserId());
        GameResult result = gameController.selectSide("func_side_session", "Naval");

        assertTrue(result.isSuccess());
        assertNotNull(result.getPacket());
    }

    @Test
    void shouldStartGameWhenBothPlayersSelectSides() {
        User user1 = userService.register("func_start_p1", "func_start_p1@mail.com", "password");
        User user2 = userService.register("func_start_p2", "func_start_p2@mail.com", "password");

        Lobby lobby = lobbyService.createLobby("func_start_p1", user1.getUserId());
        lobbyService.joinLobby(lobby.getLobbyId(), user2.getUserId());

        gameController.joinGame("func_start_s1", lobby.getLobbyId(), user1.getUserId());
        gameController.joinGame("func_start_s2", lobby.getLobbyId(), user2.getUserId());

        gameController.selectSide("func_start_s1", "Naval");
        GameResult result2 = gameController.selectSide("func_start_s2", "Aereo");

        assertTrue(result2.isSuccess());
        assertTrue(gameController.isGameStarted("func_start_s1"));
        assertEquals(0, gameController.getCurrentTurn("func_start_s1"));
        assertEquals(10, gameController.getActionsRemaining("func_start_s1"));
    }

    @Test
    void shouldRemovePlayerFromRoom() {
        User user1 = userService.register("func_remove_p1", "func_remove_p1@mail.com", "password");
        Lobby lobby = lobbyService.createLobby("func_remove_p1", user1.getUserId());

        gameController.joinGame("func_remove_s1", lobby.getLobbyId(), user1.getUserId());
        
        int removedIndex = gameController.removePlayer("func_remove_s1");

        assertEquals(0, removedIndex);
        assertFalse(gameController.isGameStarted("func_remove_s1"));
    }

    @Test
    void shouldGetRoomIdForSession() {
        User user1 = userService.register("func_roomid_p1", "func_roomid_p1@mail.com", "password");
        Lobby lobby = lobbyService.createLobby("func_roomid_p1", user1.getUserId());

        gameController.joinGame("func_roomid_s1", lobby.getLobbyId(), user1.getUserId());
        String roomId = gameController.getRoomId("func_roomid_s1");

        assertNotNull(roomId);
        assertEquals(lobby.getLobbyId(), roomId);
    }

    @Test
    void shouldGetAllSessionsInSameRoom() {
        User user1 = userService.register("func_sessions_p1", "func_sessions_p1@mail.com", "password");
        User user2 = userService.register("func_sessions_p2", "func_sessions_p2@mail.com", "password");

        Lobby lobby = lobbyService.createLobby("func_sessions_p1", user1.getUserId());
        lobbyService.joinLobby(lobby.getLobbyId(), user2.getUserId());

        gameController.joinGame("func_sessions_s1", lobby.getLobbyId(), user1.getUserId());
        gameController.joinGame("func_sessions_s2", lobby.getLobbyId(), user2.getUserId());

        List<String> sessions = gameController.getSessionsInSameRoom("func_sessions_s1");

        assertEquals(2, sessions.size());
        assertTrue(sessions.contains("func_sessions_s1"));
        assertTrue(sessions.contains("func_sessions_s2"));
    }

    @Test
    void shouldTrackActiveRoomCount() {
        User user1 = userService.register("func_count_p1", "func_count_p1@mail.com", "password");
        User user2 = userService.register("func_count_p2", "func_count_p2@mail.com", "password");

        Lobby lobby1 = lobbyService.createLobby("func_count_p1", user1.getUserId());
        Lobby lobby2 = lobbyService.createLobby("func_count_p2", user2.getUserId());

        gameController.joinGame("func_count_s1", lobby1.getLobbyId(), user1.getUserId());
        gameController.joinGame("func_count_s2", lobby2.getLobbyId(), user2.getUserId());

        int count = gameController.getActiveRoomCount();

        assertEquals(2, count);
    }

    @Test
    void shouldEndTurnAndSwitchPlayer() {
        User user1 = userService.register("func_endturn_p1", "func_endturn_p1@mail.com", "password");
        User user2 = userService.register("func_endturn_p2", "func_endturn_p2@mail.com", "password");

        Lobby lobby = lobbyService.createLobby("func_endturn_p1", user1.getUserId());
        lobbyService.joinLobby(lobby.getLobbyId(), user2.getUserId());

        gameController.joinGame("func_endturn_s1", lobby.getLobbyId(), user1.getUserId());
        gameController.joinGame("func_endturn_s2", lobby.getLobbyId(), user2.getUserId());

        gameController.selectSide("func_endturn_s1", "Naval");
        gameController.selectSide("func_endturn_s2", "Aereo");

        assertEquals(0, gameController.getCurrentTurn("func_endturn_s1"));

        GameResult result = gameController.endTurn("func_endturn_s1");

        assertTrue(result.isSuccess());
        assertEquals(1, gameController.getCurrentTurn("func_endturn_s1"));
        assertEquals(10, gameController.getActionsRemaining("func_endturn_s1"));
    }

    @Test
    void shouldNotEndTurnWhenNotPlayersTurn() {
        User user1 = userService.register("func_wrongturn_p1", "func_wrongturn_p1@mail.com", "password");
        User user2 = userService.register("func_wrongturn_p2", "func_wrongturn_p2@mail.com", "password");

        Lobby lobby = lobbyService.createLobby("func_wrongturn_p1", user1.getUserId());
        lobbyService.joinLobby(lobby.getLobbyId(), user2.getUserId());

        gameController.joinGame("func_wrongturn_s1", lobby.getLobbyId(), user1.getUserId());
        gameController.joinGame("func_wrongturn_s2", lobby.getLobbyId(), user2.getUserId());

        gameController.selectSide("func_wrongturn_s1", "Naval");
        gameController.selectSide("func_wrongturn_s2", "Aereo");

        // Player 2 tries to end turn when it's player 1's turn
        GameResult result = gameController.endTurn("func_wrongturn_s2");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Not your turn"));
    }

    @Test
    void shouldGetGameState() {
        User user1 = userService.register("func_state_p1", "func_state_p1@mail.com", "password");
        User user2 = userService.register("func_state_p2", "func_state_p2@mail.com", "password");

        Lobby lobby = lobbyService.createLobby("func_state_p1", user1.getUserId());
        lobbyService.joinLobby(lobby.getLobbyId(), user2.getUserId());

        gameController.joinGame("func_state_s1", lobby.getLobbyId(), user1.getUserId());
        gameController.joinGame("func_state_s2", lobby.getLobbyId(), user2.getUserId());

        gameController.selectSide("func_state_s1", "Naval");
        gameController.selectSide("func_state_s2", "Aereo");

        var state = gameController.getGameState("func_state_s1");

        assertNotNull(state);
        assertTrue((Boolean) state.get("gameStarted"));
        assertEquals(0, state.get("currentTurn"));
        assertEquals(10, state.get("actionsRemaining"));
    }
}
