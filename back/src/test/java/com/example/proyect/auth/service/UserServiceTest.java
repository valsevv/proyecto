package com.example.proyect.auth.service;

import com.example.proyect.auth.security.PasswordHasher;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.User;
import com.example.proyect.persistence.repos.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;                          // <— usa el import normal
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void register_newUser_hashesPassword_andPersists() {
        UserService service = new UserService(userRepository);
        String username = "alex";
        String raw = "secret123";
        String hashed = "$2a$10$hash";

        when(userRepository.existsByUsername(username)).thenReturn(false);

        // mockear estático PasswordHasher
        try (MockedStatic<PasswordHasher> mocked = mockStatic(PasswordHasher.class)) {
            mocked.when(() -> PasswordHasher.hash(raw)).thenReturn(hashed);

            when(userRepository.save(any(User.class)))
                    .thenAnswer(inv -> inv.getArgument(0, User.class));

            User created = service.register(username, raw);

            assertThat(created.getUsername()).isEqualTo(username);
            assertThat(created.getPasswordHash()).isEqualTo(hashed);

            verify(userRepository).existsByUsername(username);
            verify(userRepository).save(any(User.class));
        }
    }

    @Test
    void register_existingUsername_throws() {
        UserService service = new UserService(userRepository);
        when(userRepository.existsByUsername("alex")).thenReturn(true);

        assertThatThrownBy(() -> service.register("alex", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Username ya existe");

        verify(userRepository).existsByUsername("alex");
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_success_updatesLastLogin() {
        UserService service = new UserService(userRepository);
        String username = "alex";
        String raw = "secret";
        String hashed = "$2a$10$hash";

        User u = new User(username, hashed);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0, User.class));

        try (MockedStatic<PasswordHasher> mocked = mockStatic(PasswordHasher.class)) {
            mocked.when(() -> PasswordHasher.matches(raw, hashed)).thenReturn(true);

            User logged = service.login(username, raw);

            assertThat(logged.getLastLoginAt()).isNotNull();
            verify(userRepository).findByUsername(username);
            verify(userRepository).save(any(User.class));
        }
    }

    @Test
    void login_wrongPassword_throws() {
        UserService service = new UserService(userRepository);
        String username = "alex";
        String hashed = "$2a$10$hash";
        User u = new User(username, hashed);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(u));

        try (MockedStatic<PasswordHasher> mocked = mockStatic(PasswordHasher.class)) {
            mocked.when(() -> PasswordHasher.matches("bad", hashed)).thenReturn(false);

            assertThatThrownBy(() -> service.login(username, "bad"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Credenciales");
        }
    }

    @Test
    void login_userNotFound_throws() {
        UserService service = new UserService(userRepository);
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("missing", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credenciales");
    }
}
