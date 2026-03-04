package com.example.proyect.auth.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.persistence.classes.Game;
import com.example.proyect.persistence.classes.GameStatus;
import com.example.proyect.persistence.repos.GameRepository;

import jakarta.persistence.EntityNotFoundException;

//vseverio Capa logica de negocio, crea guarda consulta y borra partidas, con IDS de acceso

@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    // ---------------- CREATE ----------------

    @Transactional
    public Game createGame(Long player1Id, Long player2Id) { //crea partida nueva entre 2 usuarios

        Objects.requireNonNull(player1Id, "player1 no puede ser null");
        Objects.requireNonNull(player2Id, "player2 no puede ser null");

        if (player1Id.equals(player2Id)) {
            throw new IllegalArgumentException("Un jugador no puede jugar contra sí mismo");
        }

        Game game = new Game();
        game.setPlayer1Id(player1Id);
        game.setPlayer2Id(player2Id);
        game.setStartedAt(OffsetDateTime.now()); //tiempo de comienzo

        return gameRepository.save(game);
    }

    @Transactional
    public Game saveGame(Long player1Id, Long player2Id, Game gameToSave) { //valida jjugadores, partida y persiste el juego

        Objects.requireNonNull(player1Id, "player1 no puede ser null");
        Objects.requireNonNull(player2Id, "player2 no puede ser null");
        Objects.requireNonNull(gameToSave, "el juego no puede ser null");

        if (player1Id.equals(player2Id)) {
            throw new IllegalArgumentException("Un jugador no puede jugar contra sí mismo");
        }

        gameToSave.setPlayer1Id(player1Id);
        gameToSave.setPlayer2Id(player2Id);
        // Preserve original startedAt if already set
        if (gameToSave.getStartedAt() == null) {
            gameToSave.setStartedAt(OffsetDateTime.now());
        }

        return gameRepository.save(gameToSave);
    }
    

    public Game getById(Long gameId) { //busca partida por ID
        return gameRepository.findById(gameId)
                .orElseThrow(() ->
                        new EntityNotFoundException("No existe partida con id=" + gameId));
    }

    public List<Game> getPausedGamesOfUser(Long userId) { //Devuelve partidas del usuario cuyo estado guardado es Saved
        Objects.requireNonNull(userId, "user no puede ser null");

        return gameRepository.findByUserIdAndStateStatus(userId, GameStatus.SAVED.name());
    }

    public boolean canUserAccessGame(Long userId, Game game) { //verifica si el usuario participa en la partida
        Objects.requireNonNull(userId, "user no puede ser null");
        Objects.requireNonNull(game, "game no puede ser null");

        return userId.equals(game.getPlayer1Id()) || userId.equals(game.getPlayer2Id());
    }


    public List<Game> getAllGamesOfUser(Long userId) { //trae todas las partidas del usuario
        Objects.requireNonNull(userId, "user no puede ser null");

        return gameRepository
                .findByPlayer1IdOrPlayer2Id(userId, userId, Pageable.unpaged())
                .toList();
    }


    public List<Game> getGamesOfUser(Long userId, int page, int size) { //trae todas las partidas del usuario, pagina

        Objects.requireNonNull(userId, "user no puede ser null");

        Pageable pageable = PageRequest.of(page, size);

        return gameRepository
                .findByPlayer1IdOrPlayer2Id(userId, userId, pageable)
                .toList();
    }


    public long countGamesOfUser(Long userId) { //cuenta cantidad de partidas del usuario
        Objects.requireNonNull(userId, "user no puede ser null");

        return gameRepository.countByPlayer1IdOrPlayer2Id(userId, userId);
    }



    @Transactional
    public void deleteGame(Long gameId) { //eliminar partida por ID
        if (!gameRepository.existsById(gameId)) {
            throw new EntityNotFoundException("No existe partida con id=" + gameId);
        }

        gameRepository.deleteById(gameId);
    }



public boolean existsGameBetweenUsers(Long user1Id, Long user2Id) { //comprueba si ya existe una partida entre 2 usuarios

    Objects.requireNonNull(user1Id, "user1 no puede ser null");
    Objects.requireNonNull(user2Id, "user2 no puede ser null");

    if (user1Id.equals(user2Id)) {
        throw new IllegalArgumentException("Un jugador no puede jugar contra sí mismo");
    }

    return gameRepository.existsByPlayer1IdAndPlayer2Id(user1Id, user2Id)
            || gameRepository.existsByPlayer1IdAndPlayer2Id(user2Id, user1Id);
    }



}
