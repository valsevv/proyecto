package com.example.proyect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.User;
import com.example.proyect.persistence.repos.UserRepository;

@SpringBootTest
@Transactional // esto hace rollback, si queremos sacarlo comentarlo hay que correrlos de a 1
class UserServiceFunctionalTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldRegisterUserSuccessfully() {

        User user = userService.register("alexis", "alexis@mail.com", "123456");

        assertNotNull(user.getId());
        assertEquals("alexis", user.getUsername());
        assertEquals("alexis@mail.com", user.getEmail());
        assertNotNull(user.getPasswordHash());
        assertEquals(0, user.getWins());
        assertEquals(0, user.getLosses());
        assertEquals(0, user.getScore());

        assertTrue(userRepository.existsByUsername("alexis"));
        assertTrue(userRepository.existsByEmail("alexis@mail.com"));
    }

    @Test
    void shouldNotAllowDuplicateUsername() {

        userService.register("duplicate", "dup@mail.com", "123");

        assertThrows(IllegalStateException.class, () ->
                userService.register("duplicate", "other@mail.com", "456")
        );
    }

    @Test
    void shouldNotAllowDuplicateEmail() {

        userService.register("user1", "same@mail.com", "123");

        assertThrows(IllegalStateException.class, () ->
                userService.register("user2", "same@mail.com", "456")
        );
    }

    @Test
    void shouldLoginSuccessfully() {

        userService.register("loginUser", "login@mail.com", "mypassword");

        User logged = userService.login("loginUser", "mypassword");

        assertNotNull(logged.getLastConnection());
    }

    @Test
    void shouldFailLoginWithWrongPassword() {

        userService.register("user1", "user1@mail.com", "correct");

        assertThrows(IllegalArgumentException.class, () ->
                userService.login("user1", "wrong")
        );
    }

    @Test
    void shouldFailLoginIfUserDoesNotExist() {

        assertThrows(IllegalArgumentException.class, () ->
                userService.login("ghost", "123")
        );
    }
}
