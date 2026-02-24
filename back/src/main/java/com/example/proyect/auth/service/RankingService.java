package com.example.proyect.auth.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.auth.RankingTopDTO;
import com.example.proyect.persistence.classes.Ranking;
import com.example.proyect.persistence.repos.RankingRepository;
import com.example.proyect.persistence.repos.UserRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class RankingService {

    private final RankingRepository rankingRepository;
    private final UserRepository userRepository;

    public RankingService(RankingRepository rankingRepository,
                          UserRepository userRepository) {
        this.rankingRepository = rankingRepository;
        this.userRepository = userRepository;
    }

    /**
     * Crea un nuevo snapshot de ranking para un usuario.
     */
    @Transactional
    public Ranking createSnapshot(Long userId, int points) {

        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("No existe usuario con id=" + userId);
        }

        Ranking ranking = new Ranking();
        ranking.setUserId(userId);
        ranking.setPoints(Math.max(0, points));
        ranking.setReachedAt(OffsetDateTime.now());

        return rankingRepository.save(ranking);
    }

    /**
     * Devuelve todo el historial de ranking de un usuario.
     */
    @Transactional(readOnly = true)
    public List<Ranking> getHistoryByUserId(Long userId) {
        return rankingRepository.findByUserId(userId);
    }

    /**
     * Devuelve el mejor puntaje histórico de un usuario.
     */
    @Transactional(readOnly = true)
    public Ranking getBestByUserId(Long userId) {
        return rankingRepository.findByUserId(userId).stream()
                .max((r1, r2) -> r1.getPoints().compareTo(r2.getPoints()))
                .orElse(null);
    }

    /**
     * Devuelve el Top N global.
     */
    public List<RankingTopDTO> getTop(int limit) {
        return rankingRepository.findTopWithUsername(PageRequest.of(0, limit));
    }

    /**
     * Borra todo el historial de ranking de un usuario.
     */
    @Transactional
    public void deleteByUserId(Long userId) {
        List<Ranking> rankings = rankingRepository.findByUserId(userId);
        rankingRepository.deleteAll(rankings);
    }
}
