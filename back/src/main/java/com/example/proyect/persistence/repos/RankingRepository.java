package com.example.proyect.persistence.repos;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.proyect.auth.RankingTopDTO;
import com.example.proyect.persistence.classes.Ranking;

public interface RankingRepository extends JpaRepository<Ranking, Long> {

    List<Ranking> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    List<Ranking> findAllByOrderByPointsDescReachedAtAscIdAsc(
            Pageable pageable);

    @Query("""
        SELECT new com.example.proyect.auth.RankingTopDTO(
            r.userId,
            u.username,
            u.wins,
            u.losses,
            r.points
        )
        FROM Ranking r
        JOIN User u ON u.userid = r.userId
        WHERE NOT EXISTS (
            SELECT 1
            FROM Ranking r2
            WHERE r2.userId = r.userId
              AND (
                    r2.points > r.points
                 OR (r2.points = r.points AND r2.reachedAt > r.reachedAt)
                 OR (r2.points = r.points AND r2.reachedAt = r.reachedAt AND r2.id > r.id)
              )
        )
        ORDER BY r.points DESC, r.reachedAt ASC, r.id ASC
    """)
    List<RankingTopDTO> findTopUniquePlayersWithUsername(Pageable pageable);
 }
