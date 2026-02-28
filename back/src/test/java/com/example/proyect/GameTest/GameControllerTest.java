package com.example.proyect.GameTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.proyect.VOs.GameResult;
import com.example.proyect.auth.service.GameService;
import com.example.proyect.controller.GameController;
import com.example.proyect.lobby.Lobby;
import com.example.proyect.lobby.service.LobbyService;
import com.example.proyect.persistence.classes.Game;
import com.example.proyect.websocket.packet.Packet;

@ExtendWith(MockitoExtension.class)
class GameControllerTest {

    @Mock
    private LobbyService lobbyService;

    @Mock
    private GameService gameService;

    @InjectMocks
    private GameController gameController;

    @BeforeEach
    void setUp() {
        // Set default configuration values
        ReflectionTestUtils.setField(gameController, "actionsPerTurn", 10);
        ReflectionTestUtils.setField(gameController, "aerialVisionRange", 4);
        ReflectionTestUtils.setField(gameController, "navalVisionRange", 3);
        ReflectionTestUtils.setField(gameController, "missileMaxDistance", 15);
        ReflectionTestUtils.setField(gameController, "missileDamagePercentOnNaval", 0.5);
        
        // Mock gameService.createGame to return a valid game (lenient for tests that don't use it)
        Game mockGame = new Game();
        mockGame.setId(1L);
        mockGame.setPlayer1Id(1L);
        mockGame.setPlayer2Id(2L);
        lenient().when(gameService.createGame(anyLong(), anyLong())).thenReturn(mockGame);
    }

    @Test
    void bindSessionUser_shouldBindUserToSession() {
        gameController.bindSessionUser("session-1", 100L);

        Long userId = gameController.getUserIdBySession("session-1");

        assertThat(userId).isEqualTo(100L);
    }

    @Test
    void joinGame_lobbyNotFound_shouldReturnError() {
        when(lobbyService.getLobbyById(anyString())).thenReturn(Optional.empty());

        GameResult result = gameController.joinGame("session-1", "lobby-1", 1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Lobby not found");
    }

    @Test
    void joinGame_userNotInLobby_shouldReturnError() {
        Lobby lobby = new Lobby("lobby-1", "player1");
        lobby.addPlayer(2L); // Different user

        when(lobbyService.getLobbyById("lobby-1")).thenReturn(Optional.of(lobby));

        GameResult result = gameController.joinGame("session-1", "lobby-1", 1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("not in this lobby");
    }

    @Test
    void joinGame_newGame_shouldAddPlayerToRoom() {
        Lobby lobby = new Lobby("lobby-1", "player1");
        lobby.addPlayer(1L);

        when(lobbyService.getLobbyById("lobby-1")).thenReturn(Optional.of(lobby));

        GameResult result = gameController.joinGame("session-1", "lobby-1", 1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPacket()).isNotNull();
        assertThat(result.getPacket().getType()).isEqualTo(com.example.proyect.websocket.packet.PacketType.WELCOME);
    }

    @Test
    void joinGame_alreadyInGame_shouldReturnError() {
        Lobby lobby1 = new Lobby("lobby-1", "player1");
        lobby1.addPlayer(1L);

        Lobby lobby2 = new Lobby("lobby-2", "player2");
        lobby2.addPlayer(1L);

        when(lobbyService.getLobbyById("lobby-1")).thenReturn(Optional.of(lobby1));

        gameController.joinGame("session-1", "lobby-1", 1L);
        GameResult result = gameController.joinGame("session-1", "lobby-2", 1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Already in a game");
    }

    @Test
    void selectSide_invalidSide_shouldReturnError() {
        Lobby lobby = new Lobby("lobby-1", "player1");
        lobby.addPlayer(1L);
        when(lobbyService.getLobbyById("lobby-1")).thenReturn(Optional.of(lobby));
        
        gameController.joinGame("session-1", "lobby-1", 1L);
        
        GameResult result = gameController.selectSide("session-1", "InvalidSide");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid side");
    }

    @Test
    void selectSide_notInRoom_shouldReturnError() {
        GameResult result = gameController.selectSide("session-999", "Naval");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("not in a game room");
    }

    @Test
    void selectSide_validSelection_shouldSucceed() {
        Lobby lobby = new Lobby("lobby-1", "player1");
        lobby.addPlayer(1L);
        when(lobbyService.getLobbyById("lobby-1")).thenReturn(Optional.of(lobby));
        
        gameController.joinGame("session-1", "lobby-1", 1L);

        GameResult result = gameController.selectSide("session-1", "Naval");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPacket()).isNotNull();
    }

    @Test
    void selectSide_bothPlayersSelectSides_shouldStartGame() {
        Lobby lobby = new Lobby("lobby-1", "player1");
        lobby.addPlayer(1L);
        lobby.addPlayer(2L);

        Game game = new Game();
        game.setId(10L);
        game.setPlayer1Id(1L);
        game.setPlayer2Id(2L);

        when(lobbyService.getLobbyById("lobby-1")).thenReturn(Optional.of(lobby));
        when(gameService.createGame(anyLong(), anyLong())).thenReturn(game);

        gameController.joinGame("session-1", "lobby-1", 1L);
        gameController.joinGame("session-2", "lobby-1", 2L);

        gameController.selectSide("session-1", "Naval");
        GameResult result = gameController.selectSide("session-2", "Aereo");

        assertThat(result.isSuccess()).isTrue();
        assertThat(gameController.isGameStarted("session-1")).isTrue();
        verify(gameService).createGame(1L, 2L);
    }

    @Test
    void removePlayer_playerNotInRoom_shouldReturnNegative() {
        int removedIndex = gameController.removePlayer("session-999");

        assertThat(removedIndex).isEqualTo(-1);
    }

    @Test
    void removePlayer_validPlayer_shouldRemoveAndCleanup() {
        Lobby lobby = new Lobby("lobby-1", "player1");
        lobby.addPlayer(1L);

        when(lobbyService.getLobbyById("lobby-1")).thenReturn(Optional.of(lobby));

        gameController.joinGame("session-1", "lobby-1", 1L);
        int removedIndex = gameController.removePlayer("session-1");

        assertThat(removedIndex).isEqualTo(0);
        assertThat(gameController.getUserIdBySession("session-1")).isNull();
    }

    @Test
    void endTurn_notPlayerTurn_shouldReturnError() {
        Lobby lobby = new Lobby("lobby-1", "player1");
        lobby.addPlayer(1L);
        lobby.addPlayer(2L);

        when(lobbyService.getLobbyById("lobby-1")).thenReturn(Optional.of(lobby));

        gameController.joinGame("session-1", "lobby-1", 1L);
        gameController.joinGame("session-2", "lobby-1", 2L);
        gameController.selectSide("session-1", "Naval");
        gameController.selectSide("session-2", "Aereo");

        // session-2 (player 1) tries to end turn when it's player 0's turn
        GameResult result = gameController.endTurn("session-2");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Not your turn");
    }

    @Test
    void getRoomId_sessionExists_shouldReturnRoomId() {
        Lobby lobby = new Lobby("lobby-123", "player1");
        lobby.addPlayer(1L);

        when(lobbyService.getLobbyById("lobby-123")).thenReturn(Optional.of(lobby));

        gameController.joinGame("session-1", "lobby-123", 1L);
        String roomId = gameController.getRoomId("session-1");

        assertThat(roomId).isEqualTo("lobby-123");
    }

    @Test
    void getSessionsInSameRoom_shouldReturnAllSessionsInRoom() {
        Lobby lobby = new Lobby("lobby-1", "player1");
        lobby.addPlayer(1L);
        lobby.addPlayer(2L);

        when(lobbyService.getLobbyById("lobby-1")).thenReturn(Optional.of(lobby));

        gameController.joinGame("session-1", "lobby-1", 1L);
        gameController.joinGame("session-2", "lobby-1", 2L);

        List<String> sessions = gameController.getSessionsInSameRoom("session-1");

        assertThat(sessions).hasSize(2);
        assertThat(sessions).contains("session-1", "session-2");
    }

    @Test
    void getActiveRoomCount_shouldReturnNumberOfActiveRooms() {
        Lobby lobby1 = new Lobby("lobby-1", "player1");
        lobby1.addPlayer(1L);

        Lobby lobby2 = new Lobby("lobby-2", "player2");
        lobby2.addPlayer(2L);

        when(lobbyService.getLobbyById("lobby-1")).thenReturn(Optional.of(lobby1));
        when(lobbyService.getLobbyById("lobby-2")).thenReturn(Optional.of(lobby2));

        gameController.joinGame("session-1", "lobby-1", 1L);
        gameController.joinGame("session-2", "lobby-2", 2L);

        int count = gameController.getActiveRoomCount();

        assertThat(count).isEqualTo(2);
    }

    @Test
    void getCurrentTurn_sessionNotInRoom_shouldReturnZero() {
        int turn = gameController.getCurrentTurn("session-999");

        assertThat(turn).isEqualTo(0);
    }

    @Test
    void getActionsRemaining_sessionNotInRoom_shouldReturnZero() {
        int actions = gameController.getActionsRemaining("session-999");

        assertThat(actions).isEqualTo(0);
    }

    @Test
    void isGameStarted_sessionNotInRoom_shouldReturnFalse() {
        boolean started = gameController.isGameStarted("session-999");

        assertThat(started).isFalse();
    }

    @Test
    void getGameState_sessionNotInRoom_shouldReturnEmptyMap() {
        var state = gameController.getGameState("session-999");

        assertThat(state).isEmpty();
    }
}
