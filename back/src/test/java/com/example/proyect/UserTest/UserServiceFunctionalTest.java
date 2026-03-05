package com.example.proyect.usertest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.auth.exceptions.EmailAlreadyExistsException;
import com.example.proyect.auth.exceptions.InvalidCredentialsException;
import com.example.proyect.auth.exceptions.UserAlreadyExistsException;
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

        assertNotNull(user.getUserId());
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
        userService.register("duplicate_u", "dup_u@mail.com", "123456789");

        assertThrows(
                UserAlreadyExistsException.class,
                () -> userService.register("duplicate_u", "other_u@mail.com", "456456456"));
    }

    @Test
    void shouldNotAllowDuplicateEmail() {
        userService.register("user_same_1", "same_u@mail.com", "123456789");

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.register("user_same_2", "same_u@mail.com", "456456456"));
    }

    @Test
    void shouldLoginSuccessfully() {
        userService.register("loginUser", "login@mail.com", "mypassword");

        User logged = userService.login("loginUser", "mypassword");

        assertNotNull(logged.getLastConnection());
    }

    @Test
    void shouldFailLoginWithWrongPassword() {
        userService.register("wrong_pwd_user", "wrong_pwd_user@mail.com", "correct");

        assertThrows(
                InvalidCredentialsException.class,
                () -> userService.login("wrong_pwd_user", "wrong"));
    }

    @Test
    void shouldFailLoginIfUserDoesNotExist() {
        assertThrows(InvalidCredentialsException.class, () -> userService.login("ghost", "123"));
    }
}
