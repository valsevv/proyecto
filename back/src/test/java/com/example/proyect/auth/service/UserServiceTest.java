<<<<<<<< HEAD:back/src/test/java/com/example/proyect/auth/service/UserServiceTest.java
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
========
package com.example.proyect;
>>>>>>>> eafe0bd4ad3efe9e4f1d59c60a64bdee48fa202f:back/src/test/java/com/example/proyect/UserServiceTest.java

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.proyect.auth.security.PasswordHasher;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.User;
import com.example.proyect.persistence.repos.UserRepository;

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
        String email = "alex@mail.com";

        when(userRepository.existsByUsername(username)).thenReturn(false);

        // mockear estático PasswordHasher
        try (MockedStatic<PasswordHasher> mocked = mockStatic(PasswordHasher.class)) {
            mocked.when(() -> PasswordHasher.hash(raw)).thenReturn(hashed);

            when(userRepository.save(any(User.class)))
                    .thenAnswer(inv -> inv.getArgument(0, User.class));

            User created = service.register(username, email, raw);

            assertThat(created.getUsername()).isEqualTo(username);
            assertThat(created.getEmail()).isEqualTo(email);
            assertThat(created.getPasswordHash()).isEqualTo(hashed);

            verify(userRepository).existsByUsername(username);
            verify(userRepository).save(any(User.class));
        }
    }

    @Test
    void register_existingUsername_throws() {
        UserService service = new UserService(userRepository);
        when(userRepository.existsByUsername("alex")).thenReturn(true);

        assertThatThrownBy(() -> service.register("alex","alex@mail.com", "x"))
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
        String email = "alex@mail.com";

        User u = new User(username, email, hashed);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0, User.class));

        try (MockedStatic<PasswordHasher> mocked = mockStatic(PasswordHasher.class)) {
            mocked.when(() -> PasswordHasher.matches(raw, hashed)).thenReturn(true);

            User logged = service.login(username, raw);

            assertThat(logged.getLastConnection()).isNotNull();
            verify(userRepository).findByUsername(username);
            verify(userRepository).save(any(User.class));
        }
    }

    @Test
    void login_wrongPassword_throws() {
        UserService service = new UserService(userRepository);
        String username = "alex";
        String hashed = "$2a$10$hash";
        String email = "alex@mail.com";

        User u = new User(username, email, hashed);

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
