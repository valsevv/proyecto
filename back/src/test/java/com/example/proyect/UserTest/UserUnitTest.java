package com.example.proyect.UserTest;
// Test unitario basico de la clase
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.example.proyect.persistence.classes.User;

class UserUnitTest {

    @Test
    void shouldCreateUserWithValidData() {
        User user = new User("alex", "alex@mail.com", "hash123");

        assertEquals("alex", user.getUsername());
        assertEquals("alex@mail.com", user.getEmail());
        assertEquals("hash123", user.getPasswordHash());
        assertEquals(0, user.getWins());
        assertEquals(0, user.getLosses());
        assertEquals(0, user.getScore());
        assertNotNull(user.getCreatedAt());
    }

    @Test
    void shouldNotAllowBlankUsername() {
        assertThrows(IllegalArgumentException.class, () ->
                new User("", "mail@test.com", "hash")
        );
    }

    @Test
    void shouldNotAllowInvalidEmail() {
        assertThrows(IllegalArgumentException.class, () ->
                new User("alex", "invalidEmail", "hash")
        );
    }

    @Test
    void shouldRegisterWinCorrectly() {
        User user = new User("alex", "alex@mail.com", "hash");

        user.registerWin(10);

        assertEquals(1, user.getWins());
        assertEquals(10, user.getScore());
    }

    @Test
    void shouldNotAddNegativePointsOnWin() {
        User user = new User("alex", "alex@mail.com", "hash");

        user.registerWin(-50);

        assertEquals(1, user.getWins());
        assertEquals(0, user.getScore());
    }

    @Test
    void shouldRegisterLossCorrectly() {
        User user = new User("alex", "alex@mail.com", "hash");

        user.registerWin(20);
        user.registerLoss(5);

        assertEquals(1, user.getWins());
        assertEquals(1, user.getLosses());
        assertEquals(15, user.getScore());
    }

    @Test
    void shouldNotAllowNegativeScore() {
        User user = new User("alex", "alex@mail.com", "hash");

        user.registerLoss(50);

        assertEquals(1, user.getLosses());
        assertEquals(0, user.getScore());
    }

    @Test
    void shouldUpdateLastConnectionOnLogin() {
        User user = new User("alex", "alex@mail.com", "hash");

        user.markLoginNow();

        assertNotNull(user.getLastConnection());
    }
}
