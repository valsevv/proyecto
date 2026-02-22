package com.example.proyect.persistence.repos;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.proyect.persistence.classes.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

    // Buscar partidas donde el usuario sea player1 o player2 (paginado)
    Page<Game> findByPlayer1IdOrPlayer2Id(
            Long player1Id,
            Long player2Id,
            Pageable pageable
    );

    // Contar partidas del usuario
    Long countByPlayer1IdOrPlayer2Id(
            Long player1Id,
            Long player2Id
    );

    Boolean existsByPlayer1IdAndPlayer2Id(
                Long player1Id,
                Long player2Id
    );
    @Query(value = """
            SELECT *
            FROM game g
            WHERE (g.player1_id = :userId OR g.player2_id = :userId)
              AND g.state ->> 'status' = :status
            ORDER BY g.started_at DESC
            """, nativeQuery = true)
    java.util.List<Game> findByUserIdAndStateStatus(
            @Param("userId") Long userId,
            @Param("status") String status
    );
}
