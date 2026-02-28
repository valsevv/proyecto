package com.example.proyect.GameTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.example.proyect.auth.service.GameService;
import com.example.proyect.persistence.classes.Game;
import com.example.proyect.persistence.classes.GameStatus;
import com.example.proyect.persistence.repos.GameRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @InjectMocks
    private GameService gameService;

    @Test
    void createGame_validPlayers_shouldCreateAndSave() {
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game savedGame = invocation.getArgument(0);
            savedGame.setId(1L); // Simulate database ID generation
            return savedGame;
        });

        Game created = gameService.createGame(1L, 2L);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getPlayer1Id()).isEqualTo(1L);
        assertThat(created.getPlayer2Id()).isEqualTo(2L);
        assertThat(created.getStartedAt()).isNotNull();
        verify(gameRepository).save(any(Game.class));
    }

    @Test
    void createGame_samePlayer_shouldThrowException() {
        assertThatThrownBy(() -> gameService.createGame(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede jugar contra sí mismo");

        verify(gameRepository, never()).save(any());
    }

    @Test
    void createGame_nullPlayer_shouldThrowException() {
        assertThatThrownBy(() -> gameService.createGame(null, 2L))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> gameService.createGame(1L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void saveGame_validGame_shouldSave() {
        Game game = new Game();
        game.setStartedAt(OffsetDateTime.now().minusHours(1));
        
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        Game saved = gameService.saveGame(1L, 2L, game);

        assertThat(saved.getPlayer1Id()).isEqualTo(1L);
        assertThat(saved.getPlayer2Id()).isEqualTo(2L);
        assertThat(saved.getStartedAt()).isNotNull();
        verify(gameRepository).save(game);
    }

    @Test
    void saveGame_noStartedAt_shouldSetIt() {
        Game game = new Game();
        
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        Game saved = gameService.saveGame(1L, 2L, game);

        assertThat(saved.getStartedAt()).isNotNull();
    }

    @Test
    void getById_existingGame_shouldReturnGame() {
        Game game = new Game();
        game.setId(1L);

        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        Game found = gameService.getById(1L);

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(1L);
    }

    @Test
    void getById_nonExistingGame_shouldThrowException() {
        when(gameRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.getById(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("No existe partida con id=999");
    }

    @Test
    void getPausedGamesOfUser_shouldReturnSavedGames() {
        Game game1 = new Game();
        game1.setId(1L);
        Game game2 = new Game();
        game2.setId(2L);

        when(gameRepository.findByUserIdAndStateStatus(1L, GameStatus.SAVED.name()))
                .thenReturn(List.of(game1, game2));

        List<Game> pausedGames = gameService.getPausedGamesOfUser(1L);

        assertThat(pausedGames).hasSize(2);
        verify(gameRepository).findByUserIdAndStateStatus(1L, GameStatus.SAVED.name());
    }

    @Test
    void canUserAccessGame_player1_shouldReturnTrue() {
        Game game = new Game();
        game.setPlayer1Id(1L);
        game.setPlayer2Id(2L);

        boolean canAccess = gameService.canUserAccessGame(1L, game);

        assertThat(canAccess).isTrue();
    }

    @Test
    void canUserAccessGame_player2_shouldReturnTrue() {
        Game game = new Game();
        game.setPlayer1Id(1L);
        game.setPlayer2Id(2L);

        boolean canAccess = gameService.canUserAccessGame(2L, game);

        assertThat(canAccess).isTrue();
    }

    @Test
    void canUserAccessGame_otherUser_shouldReturnFalse() {
        Game game = new Game();
        game.setPlayer1Id(1L);
        game.setPlayer2Id(2L);

        boolean canAccess = gameService.canUserAccessGame(3L, game);

        assertThat(canAccess).isFalse();
    }

    @Test
    void getAllGamesOfUser_shouldReturnAllGames() {
        Game game1 = new Game();
        Game game2 = new Game();
        Page<Game> page = new PageImpl<>(List.of(game1, game2));

        when(gameRepository.findByPlayer1IdOrPlayer2Id(anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(page);

        List<Game> games = gameService.getAllGamesOfUser(1L);

        assertThat(games).hasSize(2);
    }

    @Test
    void getGamesOfUser_withPagination_shouldReturnPagedGames() {
        Game game = new Game();
        Page<Game> page = new PageImpl<>(List.of(game));

        when(gameRepository.findByPlayer1IdOrPlayer2Id(anyLong(), anyLong(), any(Pageable.class)))
                .thenReturn(page);

        List<Game> games = gameService.getGamesOfUser(1L, 0, 10);

        assertThat(games).hasSize(1);
    }

    @Test
    void countGamesOfUser_shouldReturnCount() {
        when(gameRepository.countByPlayer1IdOrPlayer2Id(1L, 1L)).thenReturn(5L);

        long count = gameService.countGamesOfUser(1L);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void deleteGame_existingGame_shouldDelete() {
        when(gameRepository.existsById(1L)).thenReturn(true);

        gameService.deleteGame(1L);

        verify(gameRepository).deleteById(1L);
    }

    @Test
    void deleteGame_nonExistingGame_shouldThrowException() {
        when(gameRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> gameService.deleteGame(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("No existe partida con id=999");

        verify(gameRepository, never()).deleteById(anyLong());
    }

    @Test
    void existsGameBetweenUsers_exists_shouldReturnTrue() {
        when(gameRepository.existsByPlayer1IdAndPlayer2Id(1L, 2L)).thenReturn(true);

        boolean exists = gameService.existsGameBetweenUsers(1L, 2L);

        assertThat(exists).isTrue();
    }

    @Test
    void existsGameBetweenUsers_existsReversed_shouldReturnTrue() {
        when(gameRepository.existsByPlayer1IdAndPlayer2Id(1L, 2L)).thenReturn(false);
        when(gameRepository.existsByPlayer1IdAndPlayer2Id(2L, 1L)).thenReturn(true);

        boolean exists = gameService.existsGameBetweenUsers(1L, 2L);

        assertThat(exists).isTrue();
    }

    @Test
    void existsGameBetweenUsers_notExists_shouldReturnFalse() {
        when(gameRepository.existsByPlayer1IdAndPlayer2Id(1L, 2L)).thenReturn(false);
        when(gameRepository.existsByPlayer1IdAndPlayer2Id(2L, 1L)).thenReturn(false);

        boolean exists = gameService.existsGameBetweenUsers(1L, 2L);

        assertThat(exists).isFalse();
    }

    @Test
    void existsGameBetweenUsers_sameUser_shouldThrowException() {
        assertThatThrownBy(() -> gameService.existsGameBetweenUsers(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede jugar contra sí mismo");
    }
}
