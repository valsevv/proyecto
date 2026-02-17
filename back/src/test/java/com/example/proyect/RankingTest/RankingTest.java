package com.example.proyect.RankingTest;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

import com.example.proyect.persistence.classes.Ranking;

class RankingTest {

    @Test
    void shouldInitializeDefaultValuesOnPrePersist() {
        Ranking ranking = new Ranking();
        ranking.setUserId(1L);
        ranking.setPoints(null);
        ranking.setReachedAt(null);

        ranking.prePersist();

        assertEquals(0, ranking.getPoints());
        assertNotNull(ranking.getReachedAt());
    }

    @Test
    void shouldNotOverrideExistingValuesOnPrePersist() {
        Ranking ranking = new Ranking();
        ranking.setUserId(1L);
        ranking.setPoints(100);

        OffsetDateTime customDate = OffsetDateTime.now().minusDays(1);
        ranking.setReachedAt(customDate);

        ranking.prePersist();

        assertEquals(100, ranking.getPoints());
        assertEquals(customDate, ranking.getReachedAt());
    }

    @Test
    void shouldAllowSettingUserId() {
        Ranking ranking = new Ranking();
        ranking.setUserId(5L);

        assertEquals(5L, ranking.getUserId());
    }

    @Test
    void shouldAllowSettingPoints() {
        Ranking ranking = new Ranking();
        ranking.setPoints(250);

        assertEquals(250, ranking.getPoints());
    }
}
