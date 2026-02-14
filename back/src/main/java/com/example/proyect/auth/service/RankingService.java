package com.example.proyect.auth.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.persistence.classes.Ranking;
import com.example.proyect.persistence.classes.User;
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
     * Crea un Ranking para un usuario si no existe (1:1).
     */
    @Transactional
    public Ranking createForUser(Long userId, int initialPoints) {
        if (rankingRepository.existsByUser_Id(userId)) {
            throw new IllegalStateException("Ya existe un ranking para user_id=" + userId);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("No existe User con id=" + userId));

        Ranking r = new Ranking();
        r.setUser(user);
        r.setPoints(initialPoints);
        r.setReachedAt(OffsetDateTime.now());

        try {
            return rankingRepository.save(r);
        } catch (DataIntegrityViolationException e) {
            // Maneja carrera por UNIQUE(user_id)
            throw new IllegalStateException("No se pudo crear el ranking (unicidad): " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene el Ranking por userId (o null si no existe).
     */
    @Transactional(readOnly = true)
    public Ranking getByUserId(Long userId) {
        return rankingRepository.findByUser_Id(userId).orElse(null);
    }

    /**
     * Suma puntos al ranking del usuario. Si no existe, puede crearlo.
     */
    @Transactional
    public Ranking addPoints(Long userId, int points, boolean createIfMissing) {
        Ranking ranking = rankingRepository.findByUser_Id(userId).orElse(null);

        if (ranking == null) {
            if (!createIfMissing) {
                throw new IllegalStateException("No existe ranking para user_id=" + userId);
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("No existe User con id=" + userId));
            ranking = new Ranking();
            ranking.setUser(user);
            ranking.setPoints(0);
            ranking.setReachedAt(OffsetDateTime.now());
        }

        int newPoints = (ranking.getPoints() == null ? 0 : ranking.getPoints()) + points;
        ranking.setPoints(newPoints);
        ranking.setReachedAt(OffsetDateTime.now());

        return rankingRepository.save(ranking);
    }

    /**
     * Setea puntos exactos (debe existir).
     */
    @Transactional
    public Ranking setPoints(Long userId, int points) {
        Ranking r = rankingRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("No existe ranking para user_id=" + userId));
        r.setPoints(points);
        r.setReachedAt(OffsetDateTime.now());
        return rankingRepository.save(r);
    }

    /**
     * Top N por puntos (desempata por reachedAt asc, luego id).
     */
    @Transactional(readOnly = true)
    public List<Ranking> getTop(int limit) {
        if (limit <= 0) limit = 10;
        return rankingRepository.findTopOrderDefault(PageRequest.of(0, limit));
    }

    /**
     * Elimina el ranking de un usuario si existe.
     */
    @Transactional
    public boolean deleteByUserId(Long userId) {
        return rankingRepository.findByUser_Id(userId)
                .map(r -> {
                    rankingRepository.delete(r);
                    return true;
                })
                .orElse(false);
    }
}
