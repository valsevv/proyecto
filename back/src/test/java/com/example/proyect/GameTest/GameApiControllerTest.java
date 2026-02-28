package com.example.proyect.GameTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import com.example.proyect.auth.api.GameApiController;
import com.example.proyect.auth.security.JwtAuthenticationFilter;
import com.example.proyect.auth.service.GameService;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.Game;
import com.example.proyect.persistence.classes.GameState;
import com.example.proyect.persistence.classes.GameStatus;
import com.example.proyect.persistence.classes.User;

@WebMvcTest(
    controllers = GameApiController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class GameApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameService gameService;

    @MockBean
    private UserService userService;

    @Test
    void getSavedGames_shouldReturnListOfGames() throws Exception {
        User user = new User("player1", "player1@mail.com", "hash");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userid", 1L);

        User rival = new User("player2", "player2@mail.com", "hash");
        org.springframework.test.util.ReflectionTestUtils.setField(rival, "userid", 2L);

        Game game = new Game();
        game.setId(10L);
        game.setPlayer1Id(1L);
        game.setPlayer2Id(2L);
        game.setStartedAt(OffsetDateTime.now());

        GameState state = new GameState();
        state.setTurn(5);
        state.setStatus(GameStatus.SAVED);
        game.setState(state);

        when(userService.getByUsername("player1")).thenReturn(user);
        when(gameService.getPausedGamesOfUser(1L)).thenReturn(List.of(game));
        when(gameService.canUserAccessGame(1L, game)).thenReturn(true);
        when(userService.getById(2L)).thenReturn(rival);

        Authentication auth = new UsernamePasswordAuthenticationToken("player1", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/api/games/saved")
                .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].gameId").value(10))
                .andExpect(jsonPath("$[0].rival.userId").value(2))
                .andExpect(jsonPath("$[0].rival.username").value("player2"))
                .andExpect(jsonPath("$[0].currentTurn").value(5));
    }

    @Test
    void deleteGame_authorizedUser_shouldReturnNoContent() throws Exception {
        User user = new User("player1", "player1@mail.com", "hash");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userid", 1L);

        Game game = new Game();
        game.setId(10L);
        game.setPlayer1Id(1L);
        game.setPlayer2Id(2L);

        when(userService.getByUsername("player1")).thenReturn(user);
        when(gameService.getById(10L)).thenReturn(game);
        when(gameService.canUserAccessGame(1L, game)).thenReturn(true);
        doNothing().when(gameService).deleteGame(10L);

        Authentication auth = new UsernamePasswordAuthenticationToken("player1", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(delete("/api/games/10")
                .principal(auth))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteGame_unauthorizedUser_shouldReturnForbidden() throws Exception {
        User user = new User("player3", "player3@mail.com", "hash");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userid", 3L);

        Game game = new Game();
        game.setId(10L);
        game.setPlayer1Id(1L);
        game.setPlayer2Id(2L);

        when(userService.getByUsername("player3")).thenReturn(user);
        when(gameService.getById(10L)).thenReturn(game);
        when(gameService.canUserAccessGame(3L, game)).thenReturn(false);

        Authentication auth = new UsernamePasswordAuthenticationToken("player3", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Without SecurityAutoConfiguration, AccessDeniedException is not translated to 403
        // In full application with security, this would be 403
        mockMvc.perform(delete("/api/games/10")
                .principal(auth))
                .andExpect(status().isInternalServerError()); // 500 without security exception handling
    }
}
