package com.example.proyect.persistence.repos;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.proyect.persistence.classes.Ranking;

public interface RankingRepository extends JpaRepository<Ranking, Long> {

    List<Ranking> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    List<Ranking> findAllByOrderByPointsDescReachedAtAscIdAsc(
            org.springframework.data.domain.Pageable pageable);
}
