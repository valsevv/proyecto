package com.example.proyect.auth.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.persistence.classes.Game;
import com.example.proyect.persistence.repos.GameRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    // ---------------- CREATE ----------------

    @Transactional
    public Game createGame(Long player1Id, Long player2Id) {

        Objects.requireNonNull(player1Id, "player1 no puede ser null");
        Objects.requireNonNull(player2Id, "player2 no puede ser null");

        if (player1Id.equals(player2Id)) {
            throw new IllegalArgumentException("Un jugador no puede jugar contra sí mismo");
        }

        Game game = new Game();
        game.setPlayer1Id(player1Id);
        game.setPlayer2Id(player2Id);
        game.setStartedAt(OffsetDateTime.now());

        return gameRepository.save(game);
    }

    // ---------------- GET BY ID ----------------

    public Game getById(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() ->
                        new EntityNotFoundException("No existe partida con id=" + gameId));
    }

    // ---------------- GET ALL OF USER ----------------

    public List<Game> getAllGamesOfUser(Long userId) {
        Objects.requireNonNull(userId, "user no puede ser null");

        return gameRepository
                .findByPlayer1IdOrPlayer2Id(userId, userId, Pageable.unpaged())
                .toList();
    }

    // ---------------- GET PAGINATED ----------------

    public List<Game> getGamesOfUser(Long userId, int page, int size) {

        Objects.requireNonNull(userId, "user no puede ser null");

        Pageable pageable = PageRequest.of(page, size);

        return gameRepository
                .findByPlayer1IdOrPlayer2Id(userId, userId, pageable)
                .toList();
    }

    // ---------------- COUNT ----------------

    public long countGamesOfUser(Long userId) {
        Objects.requireNonNull(userId, "user no puede ser null");

        return gameRepository.countByPlayer1IdOrPlayer2Id(userId, userId);
    }

    // ---------------- DELETE ----------------

    @Transactional
    public void deleteGame(Long gameId) {
        if (!gameRepository.existsById(gameId)) {
            throw new EntityNotFoundException("No existe partida con id=" + gameId);
        }

        gameRepository.deleteById(gameId);
    }
}
