package com.example.proyect.LobbyTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders. post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import com.example.proyect.auth.security.JwtAuthenticationFilter;
import com.example.proyect.auth.security.JwtService;
import com.example.proyect.lobby.Lobby;
import com.example.proyect.lobby.LobbyStatus;
import com.example.proyect.lobby.api.LobbyController;
import com.example.proyect.lobby.service.LobbyService;

import jakarta.servlet.http.Cookie;

@WebMvcTest(
    controllers = LobbyController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class LobbyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LobbyService lobbyService;

    @MockBean
    private JwtService jwtService;

    @Test
    void createLobby_shouldReturnLobbyDetails() throws Exception {
        Lobby lobby = new Lobby("lobby-1", "player1");
        lobby.addPlayer(1L);

        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(1L);
        when(lobbyService.createLobby(anyString(), anyLong())).thenReturn(lobby);

        Authentication auth = new UsernamePasswordAuthenticationToken("player1", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/lobby/create")
                .principal(auth)
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lobbyId").value("lobby-1"))
                .andExpect(jsonPath("$.creatorUsername").value("player1"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.playerCount").value(1));
    }

    @Test
    void createLobby_invalidAuth_shouldReturnBadRequest() throws Exception {
        when(jwtService.isTokenValid(anyString())).thenReturn(false);

        Authentication auth = new UsernamePasswordAuthenticationToken("player1", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/lobby/create")
                .principal(auth)
                .cookie(new Cookie("authToken", "invalid.token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void listLobbies_shouldReturnAvailableLobbies() throws Exception {
        Lobby lobby1 = new Lobby("lobby-1", "player1");
        lobby1.addPlayer(1L);
        
        Lobby lobby2 = new Lobby("lobby-2", "player2");
        lobby2.addPlayer(2L);

        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(1L);
        when(lobbyService.getAllLobbies()).thenReturn(List.of(lobby1, lobby2));

        mockMvc.perform(get("/api/lobby/list")
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].lobbyId").value("lobby-1"))
                .andExpect(jsonPath("$[0].creatorUsername").value("player1"))
                .andExpect(jsonPath("$[0].isFull").value(false));
    }

    @Test
    void listLobbies_shouldHideLoadGameLobbiesFromNonExpectedOpponent() throws Exception {
        Lobby normalLobby = new Lobby("lobby-1", "player1");
        normalLobby.addPlayer(1L);

        Lobby loadGameLobby = new Lobby("lobby-2", "player2");
        loadGameLobby.addPlayer(2L);
        loadGameLobby.setGameId(100L);
        loadGameLobby.setExpectedOpponentId(3L); // Expected opponent is userId 3

        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(1L); // Current user is 1
        when(lobbyService.getAllLobbies()).thenReturn(List.of(normalLobby, loadGameLobby));

        mockMvc.perform(get("/api/lobby/list")
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1)) // Only normal lobby visible
                .andExpect(jsonPath("$[0].lobbyId").value("lobby-1"));
    }

    @Test
    void listLobbies_shouldShowLoadGameLobbyToExpectedOpponent() throws Exception {
        Lobby loadGameLobby = new Lobby("lobby-load", "player1");
        loadGameLobby.addPlayer(1L);
        loadGameLobby.setGameId(100L);
        loadGameLobby.setExpectedOpponentId(2L); // Expected opponent is userId 2

        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(2L); // Current user is 2
        when(lobbyService.getAllLobbies()).thenReturn(List.of(loadGameLobby));

        mockMvc.perform(get("/api/lobby/list")
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lobbyId").value("lobby-load"))
                .andExpect(jsonPath("$[0].isLoadGame").value(true))
                .andExpect(jsonPath("$[0].gameId").value(100));
    }

    @Test
    void joinLobby_shouldReturnSuccess() throws Exception {
        Lobby lobby = new Lobby("lobby-1", "player1");
        lobby.addPlayer(1L);
        lobby.addPlayer(2L);

        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(2L);
        when(lobbyService.joinLobby(anyString(), anyLong())).thenReturn(lobby);

        Authentication auth = new UsernamePasswordAuthenticationToken("player2", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/lobby/join/lobby-1")
                .principal(auth)
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lobbyId").value("lobby-1"))
                .andExpect(jsonPath("$.playerCount").value(2))
                .andExpect(jsonPath("$.isReady").value(true));
    }

    @Test
    void joinLobby_fullLobby_shouldReturnBadRequest() throws Exception {
        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(2L);
        when(lobbyService.joinLobby(anyString(), anyLong()))
                .thenThrow(new IllegalStateException("Lobby lleno"));

        Authentication auth = new UsernamePasswordAuthenticationToken("player2", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/lobby/join/lobby-1")
                .principal(auth)
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Lobby lleno"));
    }

    @Test
    void getMyLobby_userInLobby_shouldReturnLobbyInfo() throws Exception {
        Lobby lobby = new Lobby("my-lobby", "player1");
        lobby.addPlayer(1L);

        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(1L);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(Optional.of(lobby));

        mockMvc.perform(get("/api/lobby/my-lobby")
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inLobby").value(true))
                .andExpect(jsonPath("$.lobbyId").value("my-lobby"))
                .andExpect(jsonPath("$.creatorUsername").value("player1"));
    }

    @Test
    void getMyLobby_userNotInLobby_shouldReturnNotInLobby() throws Exception {
        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(1L);
        when(lobbyService.getLobbyByUserId(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/lobby/my-lobby")
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inLobby").value(false));
    }

    @Test
    void leaveLobby_shouldReturnSuccess() throws Exception {
        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(1L);
        doNothing().when(lobbyService).leaveLobby(1L);

        mockMvc.perform(post("/api/lobby/leave")
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createLoadGameLobby_shouldReturnLobbyDetails() throws Exception {
        Lobby lobby = new Lobby("load-lobby", "player1");
        lobby.addPlayer(1L);
        lobby.setGameId(100L);
        lobby.setExpectedOpponentId(2L);

        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(1L);
        when(lobbyService.createLoadGameLobby(anyLong(), anyLong(), anyString())).thenReturn(lobby);

        Authentication auth = new UsernamePasswordAuthenticationToken("player1", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/lobby/load-game/100")
                .principal(auth)
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lobbyId").value("load-lobby"))
                .andExpect(jsonPath("$.gameId").value(100));
    }

    @Test
    void createLoadGameLobby_invalidGameId_shouldReturnBadRequest() throws Exception {
        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUserId(anyString())).thenReturn(1L);
        when(lobbyService.createLoadGameLobby(anyLong(), anyLong(), anyString()))
                .thenThrow(new IllegalArgumentException("Game not found"));

        Authentication auth = new UsernamePasswordAuthenticationToken("player1", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/lobby/load-game/999")
                .principal(auth)
                .cookie(new Cookie("authToken", "valid.token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Game not found"));
    }
}
