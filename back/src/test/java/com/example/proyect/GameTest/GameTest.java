package com.example.proyect.GameTest;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.example.proyect.persistence.classes.Game;
import com.example.proyect.persistence.classes.GameState;
import com.example.proyect.persistence.classes.GameStatus;

class GameTest {

    @Test
    void shouldInitializeDefaultState() {
        Game game = new Game();

        assertNotNull(game.getState(), "El state no debería ser null por defecto");
        assertEquals(GameStatus.IN_PROGRESS, game.getState().getStatus());
        assertEquals(1, game.getState().getTurn());
    }

    @Test
    void shouldInitializeStartedAtOnPrePersistWhenNull() {
        Game game = new Game();
        game.setPlayer1Id(1L);
        game.setPlayer2Id(2L);
        game.setStartedAt(null);

        game.prePersist();

        assertNotNull(game.getStartedAt(), "startedAt debería inicializarse en prePersist()");
    }

    @Test
    void shouldNotOverrideStartedAtOnPrePersistWhenAlreadySet() {
        Game game = new Game();
        game.setPlayer1Id(1L);
        game.setPlayer2Id(2L);

        OffsetDateTime customStart = OffsetDateTime.now().minusDays(1);
        game.setStartedAt(customStart);

        game.prePersist();

        assertEquals(customStart, game.getStartedAt(), "prePersist() no debería pisar startedAt si ya existe");
    }

    @Test
    void shouldThrowOnPrePersistWhenPlayersAreSame() {
        Game game = new Game();
        game.setPlayer1Id(10L);
        game.setPlayer2Id(10L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, game::prePersist);
        assertEquals("player1 y player2 no pueden ser el mismo usuario", ex.getMessage());
    }

    @Test
    void shouldThrowOnPreUpdateWhenPlayersAreSame() {
        Game game = new Game();
        game.setPlayer1Id(10L);
        game.setPlayer2Id(10L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, game::preUpdate);
        assertEquals("player1 y player2 no pueden ser el mismo usuario", ex.getMessage());
    }

    @Test
    void shouldAllowSettingPlayers() {
        Game game = new Game();
        game.setPlayer1Id(5L);
        game.setPlayer2Id(7L);

        assertEquals(5L, game.getPlayer1Id());
        assertEquals(7L, game.getPlayer2Id());
    }

    @Test
    void shouldAllowSettingState() {
        Game game = new Game();

        GameState custom = new GameState();
        custom.setStatus(GameStatus.IN_PROGRESS);
        custom.setTurn(3);

        game.setState(custom);

        assertSame(custom, game.getState());
        assertEquals(3, game.getState().getTurn());
    }

    @Test
    void shouldAllowSettingEndedAt() {
        Game game = new Game();
        OffsetDateTime end = OffsetDateTime.now();

        game.setEndedAt(end);

        assertEquals(end, game.getEndedAt());
    }
}
