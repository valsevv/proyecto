//Test Funcional del servicio UserService. Prueba el servicio y tambien el repositorio

package com.example.proyect.UserTest;

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
    //Se prueba crear un usuario 
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
    
        //Si quisieramos usar el Repositorio para traer ese usuario creado
        // Optional<User> optionalUser = userRepository.findByUsername("alexis");

        // if (optionalUser.isPresent()) {
        //     User user2 = optionalUser.get();
        //     System.out.println("[DEBUG]-> El usuario existe : " + user2.getUsername() );
        //     System.out.println("[DEBUG]--> Su Email es : " + user2.getEmail() );
        //     System.out.println("[DEBUG]---> Su puntaje es : " + user2.getScore() );
        //     System.out.println("[DEBUG]----> Password hasheado es : " + user2.getPasswordHash() );
        // }
    }

    @Test
    void shouldNotAllowDuplicateUsername() {

        userService.register("duplicate", "dup@mail.com", "123456789");

        assertThrows(IllegalStateException.class, () ->
                userService.register("duplicate", "other@mail.com", "456456456")
        );
    }

    @Test
    void shouldNotAllowDuplicateEmail() {

        userService.register("user1", "same@mail.com", "123456789");

        assertThrows(IllegalStateException.class, () ->
                userService.register("user2", "same@mail.com", "456456456")
        );
    }

    @Test
    void shouldLoginSuccessfully() {

        userService.register("loginUser", "login@mail.com", "mypassword");

        User logged = userService.login("loginUser", "mypassword");
    
        // Si quisiera debugear y ver en el Debug Console mas info del usuario
        // System.out.println("[DEBUG]-> Usuario logeado : " + logged.getUsername() );
        // System.out.println("[DEBUG]--> Email  : " + logged.getEmail() );
        // System.out.println("[DEBUG]---> ultima conexion : " + logged.getLastConnection() );
        
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
