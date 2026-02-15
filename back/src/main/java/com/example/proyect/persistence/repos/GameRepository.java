package com.example.proyect.persistence.repos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.proyect.persistence.classes.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

    boolean existsByPlayer1IdAndPlayer2Id(Long player1Id, Long player2Id);

    // --- Todas las partidas donde participa un usuario (sin importar si es p1 o p2) --- 
    Page<Game> findByPlayer1IdOrPlayer2Id(Long player1Id, Long player2Id, Pageable pageable);

    // --- Solo por player1Id o solo por player2Id ---
    Page<Game> findByPlayer1Id(Long player1Id, Pageable pageable);
    Page<Game> findByPlayer2Id(Long player2Id, Pageable pageable);

}