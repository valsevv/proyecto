package com.example.proyect.auth.service;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.persistence.classes.Game;
import com.example.proyect.persistence.repos.GameRepository;


@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public List<Game> getAllGamesOfUser(Long userId) {
        Objects.requireNonNull(userId, "user no puede ser null");
        return gameRepository
                .findByPlayer1IdOrPlayer2Id(userId, userId, Pageable.unpaged())
                .toList();
    }
}