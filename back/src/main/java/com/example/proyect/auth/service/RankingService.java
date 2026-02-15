package com.example.proyect.auth.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * Crea un Ranking para un usuario si no existe (1:1).
     */
    @Transactional
    public Ranking createForUser(Long userId, int initialPoints) {
        if (rankingRepository.existsByuserId(userId)) {
            throw new IllegalStateException("Ya existe un ranking para user_id=" + userId);
        }
        Ranking r = new Ranking();
        r.setUserId(userId);
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
        return rankingRepository.findByuserId(userId).orElse(null);
    }

    /**
     * Suma puntos al ranking del usuario. Si no existe, puede crearlo.
     */
    @Transactional
    public Ranking addPoints(Long userId, int points, boolean createIfMissing) {
        Ranking ranking = rankingRepository.findByuserId(userId).orElse(null);

        if (ranking == null) {
            createForUser(userId, 0);
        }

        int newPoints = (ranking.getPoints() == null ? 0 : ranking.getPoints()) + points;
        ranking.setPoints(newPoints);
        ranking.setReachedAt(OffsetDateTime.now());

        return rankingRepository.save(ranking);
    }

    @Transactional
    public Ranking setPoints(Long userId, int points) {
        Ranking r = rankingRepository.findByuserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("No existe ranking para user_id=" + userId));
        r.setPoints(points);
        r.setReachedAt(OffsetDateTime.now());
        return rankingRepository.save(r);
    }

    /**
     * Top N por puntos (desempata por el primero en lograr ese puntaje, luego id).
     */
    @Transactional(readOnly = true)
    public List<Ranking> getTop(int limit) {
        if (limit <= 0) limit = 10; //Por defecto TOP 10
        return rankingRepository.findTopOrderDefault(PageRequest.of(0, limit));
    }

    /**
     * Elimina el usuario del ranking
     */
    @Transactional
    public boolean deleteByUserId(Long userId) {
        return rankingRepository.findByuserId(userId)
                .map(r -> {
                    rankingRepository.delete(r);
                    return true;
                })
                .orElse(false);
    }
}
