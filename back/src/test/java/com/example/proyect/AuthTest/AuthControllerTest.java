package com.example.proyect.AuthTest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.proyect.auth.Exceptions.InvalidCredentialsException;
import com.example.proyect.auth.Exceptions.UserAlreadyExistsException;
import com.example.proyect.auth.api.AuthController;
import com.example.proyect.auth.security.JwtService;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.User;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @Test
    void register_shouldReturnAuthResponseWithCookie() throws Exception {
        User user = new User("testuser", "test@mail.com", "hashedpwd");
        // Simulate ID as if returned from database
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userid", 1L);

        when(userService.register(anyString(), anyString(), anyString())).thenReturn(user);
        when(jwtService.generateToken(any(Long.class), anyString())).thenReturn("fake.jwt.token");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"email\":\"test@mail.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(cookie().exists("authToken"))
                .andExpect(cookie().httpOnly("authToken", true))
                .andExpect(cookie().path("authToken", "/"));
    }

    @Test
    void register_duplicateUsername_shouldReturnBadRequest() throws Exception {
        when(userService.register(anyString(), anyString(), anyString()))
                .thenThrow(new UserAlreadyExistsException("El usuario ya existe"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"duplicate\",\"email\":\"dup@mail.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict()); // 409 Conflict for duplicate resource
    }

    @Test
    void login_validCredentials_shouldReturnAuthResponseWithCookie() throws Exception {
        User user = new User("loginuser", "login@mail.com", "hashedpwd");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userid", 2L);

        when(userService.login(anyString(), anyString())).thenReturn(user);
        when(jwtService.generateToken(any(Long.class), anyString())).thenReturn("fake.jwt.token");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"loginuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.username").value("loginuser"))
                .andExpect(cookie().exists("authToken"));
    }

    @Test
    void login_invalidCredentials_shouldReturnUnauthorized() throws Exception {
        when(userService.login(anyString(), anyString()))
                .thenThrow(new InvalidCredentialsException("Credenciales inválidas"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"wronguser\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized()); // 401 Unauthorized for invalid credentials
    }

    @Test
    void logout_shouldClearCookieAndReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"))
                .andExpect(cookie().maxAge("authToken", 0));
    }

    @Test
    void ping_shouldReturnOkStatus() throws Exception {
        mockMvc.perform(get("/api/auth/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void me_validToken_shouldReturnUsername() throws Exception {
        when(jwtService.isTokenValid(anyString())).thenReturn(true);
        when(jwtService.extractUsername(anyString())).thenReturn("authenticateduser");

        mockMvc.perform(get("/api/users/me")
                .cookie(new jakarta.servlet.http.Cookie("authToken", "valid.jwt.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("authenticateduser"));
    }

    @Test
    void me_invalidToken_shouldReturnUnauthorized() throws Exception {
        when(jwtService.isTokenValid(anyString())).thenReturn(false);

        mockMvc.perform(get("/api/users/me")
                .cookie(new jakarta.servlet.http.Cookie("authToken", "invalid.jwt.token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_noToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
