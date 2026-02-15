package com.example.proyect.persistence.repos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.proyect.persistence.classes.Ranking;

public interface RankingRepository extends JpaRepository<Ranking, Long> {

    Optional<Ranking> findByuserId(Long userId);

    boolean existsByuserId(Long userId);

    @Query("""
           SELECT rank
             FROM Ranking rank
         ORDER BY rank.points DESC, rank.reachedAt ASC, rank.id ASC
           """)
    List<Ranking> findTopOrderDefault(org.springframework.data.domain.Pageable pageable);
}