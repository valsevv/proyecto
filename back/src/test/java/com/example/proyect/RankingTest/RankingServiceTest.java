package com.example.proyect.RankingTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.example.proyect.auth.service.RankingService;
import com.example.proyect.persistence.classes.Ranking;
import com.example.proyect.persistence.repos.RankingRepository;
import com.example.proyect.persistence.repos.UserRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    RankingRepository rankingRepository;

    @Mock
    UserRepository userRepository;

    // ---------------- CREATE SNAPSHOT ----------------

    @Test
    void createSnapshot_success() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        when(userRepository.existsById(1L)).thenReturn(true);
        when(rankingRepository.save(any(Ranking.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Ranking result = service.createSnapshot(1L, 100);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getPoints()).isEqualTo(100);
        assertThat(result.getReachedAt()).isNotNull();

        verify(rankingRepository).save(any(Ranking.class));
    }

    @Test
    void createSnapshot_userNotFound_throws() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        when(userRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.createSnapshot(1L, 100))
                .isInstanceOf(EntityNotFoundException.class);

        verify(rankingRepository, never()).save(any());
    }

    // ---------------- HISTORY ----------------

    @Test
    void getHistoryByUserId_returnsList() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        Ranking r = new Ranking();
        r.setUserId(1L);

        when(rankingRepository.findByUserId(1L))
                .thenReturn(List.of(r));

        List<Ranking> result = service.getHistoryByUserId(1L);

        assertThat(result).hasSize(1);
        verify(rankingRepository).findByUserId(1L);
    }

    // ---------------- BEST ----------------

    @Test
    void getBestByUserId_returnsHighestPoints() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        Ranking r1 = new Ranking();
        r1.setPoints(50);

        Ranking r2 = new Ranking();
        r2.setPoints(100);

        when(rankingRepository.findByUserId(1L))
                .thenReturn(List.of(r1, r2));

        Ranking result = service.getBestByUserId(1L);

        assertThat(result.getPoints()).isEqualTo(100);
    }

    // ---------------- TOP ----------------

    @Test
    void getTop_returnsList() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        Ranking r1 = new Ranking();
        r1.setPoints(200);

        when(rankingRepository
                .findAllByOrderByPointsDescReachedAtAscIdAsc(any(Pageable.class)))
                .thenReturn(List.of(r1));

        List<Ranking> result = service.getTop(5);

        assertThat(result).hasSize(1);
        verify(rankingRepository)
                .findAllByOrderByPointsDescReachedAtAscIdAsc(any(Pageable.class));
    }

    // ---------------- DELETE ----------------

    @Test
    void deleteByUserId_deletesAllUserHistory() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        Ranking r1 = new Ranking();
        Ranking r2 = new Ranking();

        when(rankingRepository.findByUserId(1L))
                .thenReturn(List.of(r1, r2));

        service.deleteByUserId(1L);

        verify(rankingRepository).deleteAll(List.of(r1, r2));
    }
}
