package com.example.proyect.persistence.repos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.proyect.persistence.classes.User;
// Interaccion con la base de datos mediante este repo de JPA, como definimos en la clase User JPA conoce las entidades
public interface UserRepository extends JpaRepository<User, Long> {
    //buscar un usuario por username, si no esta retorna NULL si no retorna el User
    Optional<User> findByUsername(String username);

    // True si existe el usuario en la base
    boolean existsByUsername(String username);

    //buscar un usuario por email, si no esta retorna NULL si no retorna el User <<este es opcional, seguramente no lo usemos>>
    Optional<User> findByEmail(String email);

   // True si existe el usuario con ese emial en la base
    boolean existsByEmail(String email);
}