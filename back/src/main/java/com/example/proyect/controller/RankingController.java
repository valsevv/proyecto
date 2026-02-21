package com.example.proyect.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.proyect.auth.service.RankingService;
import com.example.proyect.persistence.classes.Ranking;

import jakarta.persistence.EntityNotFoundException;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    /**
     * POST /api/ranking/snapshot
     * Crea un nuevo snapshot de ranking para un usuario.
     */
    @PostMapping("/snapshot")
    public ResponseEntity<Ranking> createSnapshot(
            @RequestParam Long userId,
            @RequestParam int points) {
        try {
            Ranking ranking = rankingService.createSnapshot(userId, points);
            return ResponseEntity.status(HttpStatus.CREATED).body(ranking);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * GET /api/ranking/history/{userId}
     * Devuelve todo el historial de ranking de un usuario.
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Ranking>> getHistory(@PathVariable Long userId) {
        List<Ranking> history = rankingService.getHistoryByUserId(userId);
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/ranking/best/{userId}
     * Devuelve el mejor puntaje histórico de un usuario.
     */
    @GetMapping("/best/{userId}")
    public ResponseEntity<Ranking> getBest(@PathVariable Long userId) {
        Ranking best = rankingService.getBestByUserId(userId);
        if (best == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(best);
    }

    /**
     * GET /api/ranking/top
     * Devuelve el Top N global (por defecto 10).
     */
    @GetMapping("/top")
    public ResponseEntity<List<Ranking>> getTop(
            @RequestParam(defaultValue = "10") int limit) {
        List<Ranking> top = rankingService.getTop(limit);
        return ResponseEntity.ok(top);
    }

    /**
     * DELETE /api/ranking/history/{userId}
     * Borra todo el historial de ranking de un usuario.
     */
    @DeleteMapping("/history/{userId}")
    public ResponseEntity<Void> deleteHistory(@PathVariable Long userId) {
        rankingService.deleteByUserId(userId);
        return ResponseEntity.noContent().build();
    }
}
