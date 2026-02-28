package com.example.proyect.GameTest;

import static org.junit.jupiter.api. Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.auth.service.GameService;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.Game;
import com.example.proyect.persistence.classes.GameState;
import com.example.proyect.persistence.classes.GameStatus;
import com.example.proyect.persistence.classes.User;
import com.example.proyect.persistence.repos.GameRepository;

import jakarta.persistence.EntityNotFoundException;

@SpringBootTest
@Transactional
public class GameServiceFunctionalTest {
    
    @Autowired
    private GameService gameService;

    @Autowired
    private UserService userService;

    @Autowired
    private GameRepository gameRepository;

    @Test
    void shouldCreateGameSuccessfully() {
        User user1 = userService.register("player1", "player1@mail.com", "password");
        User user2 = userService.register("player2", "player2@mail.com", "password");

        Game game = gameService.createGame(user1.getUserId(), user2.getUserId());

        assertNotNull(game);
        assertNotNull(game.getId());
        assertEquals(user1.getUserId(), game.getPlayer1Id());
        assertEquals(user2.getUserId(), game.getPlayer2Id());
        assertNotNull(game.getStartedAt());
    }

    @Test
    void shouldNotAllowSamePlayerInBothSides() {
        User user = userService.register("player_solo", "solo@mail.com", "password");

        assertThrows(IllegalArgumentException.class, () ->
            gameService.createGame(user.getUserId(), user.getUserId())
        );
    }

    @Test
    void shouldSaveAndRetrieveGame() {
        User user1 = userService.register("save_p1", "save_p1@mail.com", "password");
        User user2 = userService.register("save_p2", "save_p2@mail.com", "password");

        Game game = new Game();
        GameState state = new GameState();
        state.setStatus(GameStatus.SAVED);
        state.setTurn(5);
        game.setState(state);

        Game saved = gameService.saveGame(user1.getUserId(), user2.getUserId(), game);

        assertNotNull(saved.getId());
        
        Game retrieved = gameService.getById(saved.getId());
        assertEquals(saved.getId(), retrieved.getId());
        assertEquals(user1.getUserId(), retrieved.getPlayer1Id());
        assertEquals(user2.getUserId(), retrieved.getPlayer2Id());
    }

    @Test
    void shouldGetPausedGamesOfUser() {
        User user1 = userService.register("paused_p1", "paused_p1@mail.com", "password");
        User user2 = userService.register("paused_p2", "paused_p2@mail.com", "password");
        User user3 = userService.register("paused_p3", "paused_p3@mail.com", "password");

        // Create saved game
        Game savedGame = new Game();
        GameState savedState = new GameState();
        savedState.setStatus(GameStatus.SAVED);
        savedGame.setState(savedState);
        gameService.saveGame(user1.getUserId(), user2.getUserId(), savedGame);

        // Create active game
        Game activeGame = new Game();
        GameState activeState = new GameState();
        activeState.setStatus(GameStatus.IN_PROGRESS);
        activeGame.setState(activeState);
        gameService.saveGame(user1.getUserId(), user3.getUserId(), activeGame);

        List<Game> pausedGames = gameService.getPausedGamesOfUser(user1.getUserId());

        // Should only return the saved game, not the active one
        assertEquals(1, pausedGames.size());
        assertEquals(GameStatus.SAVED, pausedGames.get(0).getState().getStatus());
    }

    @Test
    void shouldValidateUserAccessToGame() {
        User user1 = userService.register("access_p1", "access_p1@mail.com", "password");
        User user2 = userService.register("access_p2", "access_p2@mail.com", "password");
        User user3 = userService.register("access_p3", "access_p3@mail.com", "password");

        Game game = gameService.createGame(user1.getUserId(), user2.getUserId());

        assertTrue(gameService.canUserAccessGame(user1.getUserId(), game));
        assertTrue(gameService.canUserAccessGame(user2.getUserId(), game));
        assertFalse(gameService.canUserAccessGame(user3.getUserId(), game));
    }

    @Test
    void shouldGetAllGamesOfUser() {
        User user1 = userService.register("all_games_p1", "all_games_p1@mail.com", "password");
        User user2 = userService.register("all_games_p2", "all_games_p2@mail.com", "password");
        User user3 = userService.register("all_games_p3", "all_games_p3@mail.com", "password");

        gameService.createGame(user1.getUserId(), user2.getUserId());
        gameService.createGame(user1.getUserId(), user3.getUserId());
        gameService.createGame(user2.getUserId(), user3.getUserId());

        List<Game> user1Games = gameService.getAllGamesOfUser(user1.getUserId());
        List<Game> user2Games = gameService.getAllGamesOfUser(user2.getUserId());
        List<Game> user3Games = gameService.getAllGamesOfUser(user3.getUserId());

        assertEquals(2, user1Games.size());
        assertEquals(2, user2Games.size());
        assertEquals(2, user3Games.size());
    }

    @Test
    void shouldCountGamesOfUser() {
        User user1 = userService.register("count_p1", "count_p1@mail.com", "password");
        User user2 = userService.register("count_p2", "count_p2@mail.com", "password");

        gameService.createGame(user1.getUserId(), user2.getUserId());
        gameService.createGame(user1.getUserId(), user2.getUserId());

        long count = gameService.countGamesOfUser(user1.getUserId());

        assertEquals(2, count);
    }

    @Test
    void shouldDeleteGame() {
        User user1 = userService.register("delete_p1", "delete_p1@mail.com", "password");
        User user2 = userService.register("delete_p2", "delete_p2@mail.com", "password");

        Game game = gameService.createGame(user1.getUserId(), user2.getUserId());
        Long gameId = game.getId();

        assertTrue(gameRepository.existsById(gameId));

        gameService.deleteGame(gameId);

        assertFalse(gameRepository.existsById(gameId));
    }

    @Test
    void shouldCheckGameExistsBetweenUsers() {
        User user1 = userService.register("exists_p1", "exists_p1@mail.com", "password");
        User user2 = userService.register("exists_p2", "exists_p2@mail.com", "password");
        User user3 = userService.register("exists_p3", "exists_p3@mail.com", "password");

        assertFalse(gameService.existsGameBetweenUsers(user1.getUserId(), user2.getUserId()));

        gameService.createGame(user1.getUserId(), user2.getUserId());

        assertTrue(gameService.existsGameBetweenUsers(user1.getUserId(), user2.getUserId()));
        assertTrue(gameService.existsGameBetweenUsers(user2.getUserId(), user1.getUserId())); // Order doesn't matter
        assertFalse(gameService.existsGameBetweenUsers(user1.getUserId(), user3.getUserId()));
    }

    @Test
    void shouldThrowExceptionWhenGettingNonExistentGame() {
        assertThrows(EntityNotFoundException.class, () ->
            gameService.getById(99999L)
        );
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistentGame() {
        assertThrows(EntityNotFoundException.class, () ->
            gameService.deleteGame(99999L)
        );
    }
}
