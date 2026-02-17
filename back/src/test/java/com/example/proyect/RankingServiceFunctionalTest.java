package com.example.proyect;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.auth.service.RankingService;
import com.example.proyect.persistence.classes.Ranking;
import com.example.proyect.persistence.repos.RankingRepository;

@SpringBootTest
@Transactional
class RankingServiceFunctionalTest {

    @Autowired
    private RankingService rankingService;

    @Autowired
    private RankingRepository rankingRepository;
    
    @Test
    void shouldCreateRankingForUser() {
        Long userId = 1L;
        Ranking ranking = rankingService.createForUser(userId, 100);

        assertNotNull(ranking.getId());
        assertEquals(100, ranking.getPoints());

        Ranking fromDb = rankingRepository.findByuserId(userId).orElse(null);

        assertNotNull(fromDb);
        assertEquals(100, fromDb.getPoints());
    }

    @Test
    void shouldAddPointsCorrectly() {

        Long userId = 2L;

        rankingService.createForUser(userId, 50);
        rankingService.addPoints(userId, 25, false);

        Ranking updated = rankingRepository.findByuserId(userId).orElseThrow();

        assertEquals(75, updated.getPoints());
    }

    @Test
    void shouldReturnTopOrderedByPoints() {

        rankingService.createForUser(10L, 100);
        rankingService.createForUser(20L, 200);
        rankingService.createForUser(30L, 150);

        List<Ranking> top = rankingService.getTop(3);

        assertEquals(200, top.get(0).getPoints());
        assertEquals(150, top.get(1).getPoints());
        assertEquals(100, top.get(2).getPoints());
    }
}
