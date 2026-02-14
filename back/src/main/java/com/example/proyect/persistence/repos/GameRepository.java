package com.example.proyect.persistence.repos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.proyect.persistence.classes.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

    boolean existsByPlayer1AndPlayer2(Long player1Id, Long player2Id);

    // --- Todas las partidas donde participa un usuario (sin importar si es p1 o p2) --- 
    Page<Game> findByPlayer1OrPlayer2(Long player1Id, Long player2Id, Pageable pageable);

    // --- Solo por player1Id o solo por player2Id ---
    Page<Game> findByPlayer1(Long player1Id, Pageable pageable);
    Page<Game> findByPlayer2(Long player2Id, Pageable pageable);

}