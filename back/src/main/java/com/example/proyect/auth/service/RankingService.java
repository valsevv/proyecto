package com.example.proyect.auth.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.auth.RankingTopDTO;
import com.example.proyect.persistence.classes.Ranking;
import com.example.proyect.persistence.classes.User;
import com.example.proyect.persistence.repos.RankingRepository;
import com.example.proyect.persistence.repos.UserRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    private final RankingRepository rankingRepository;
    private final UserRepository userRepository;

    public RankingService(RankingRepository rankingRepository,
                          UserRepository userRepository) {
        this.rankingRepository = rankingRepository;
        this.userRepository = userRepository;
    }

  //vseverio esta clase guarda y consulta el historial de puntajes por usuario
    @Transactional
    public Ranking createSnapshot(Long userId) { //crea registro de rakning para usuario, si existe
        log.info("Creating ranking snapshot for user: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("No existe usuario con id=" + userId));

        Ranking ranking = new Ranking();
        ranking.setUserId(userId);
        ranking.setPoints(user.getScore());
        ranking.setReachedAt(OffsetDateTime.now());

        return rankingRepository.save(ranking);
    }


    @Transactional(readOnly = true)
    public List<Ranking> getHistoryByUserId(Long userId) { //devuelve todos los snapshots de ranking del usuario
        return rankingRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Ranking getBestByUserId(Long userId) { //obtiene mejor puntaje historico del usuario
        return rankingRepository.findByUserId(userId).stream()
                .max((r1, r2) -> r1.getPoints().compareTo(r2.getPoints()))
                .orElse(null);
    }

    public List<RankingTopDTO> getTop(int limit) { //trae top N global del ranking con paginacion simple
        return rankingRepository.findTopUniquePlayersWithUsername(PageRequest.of(0, limit));
    }

    @Transactional
    public void deleteByUserId(Long userId) { //borra registros de ranking de usuario
        List<Ranking> rankings = rankingRepository.findByUserId(userId);
        rankingRepository.deleteAll(rankings);
    }
}
