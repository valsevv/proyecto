package com.example.proyect;

import java.util.List;
import java.util.Optional;

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

    @Test
    void createForUser_success() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        when(rankingRepository.existsByuserId(1L)).thenReturn(false);
        when(rankingRepository.save(any(Ranking.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Ranking result = service.createForUser(1L, 100);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getPoints()).isEqualTo(100);

        verify(rankingRepository).existsByuserId(1L);
        verify(rankingRepository).save(any(Ranking.class));
    }

    @Test
    void createForUser_alreadyExists_throws() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        when(rankingRepository.existsByuserId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.createForUser(1L, 100))
                .isInstanceOf(IllegalStateException.class);

        verify(rankingRepository).existsByuserId(1L);
        verify(rankingRepository, never()).save(any());
    }

    // ---------------- GET ----------------

    @Test
    void getByUserId_found() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        Ranking r = new Ranking();
        r.setUserId(1L);

        when(rankingRepository.findByuserId(1L)).thenReturn(Optional.of(r));

        Ranking result = service.getByUserId(1L);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(1L);
    }

    @Test
    void getByUserId_notFound_returnsNull() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        when(rankingRepository.findByuserId(1L)).thenReturn(Optional.empty());

        Ranking result = service.getByUserId(1L);

        assertThat(result).isNull();
    }

    // ---------------- ADD POINTS ----------------

    @Test
    void addPoints_existingUser_updatesPoints() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        Ranking r = new Ranking();
        r.setUserId(1L);
        r.setPoints(50);

        when(rankingRepository.findByuserId(1L)).thenReturn(Optional.of(r));
        when(rankingRepository.save(any(Ranking.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Ranking result = service.addPoints(1L, 25, false);

        assertThat(result.getPoints()).isEqualTo(75);
        verify(rankingRepository).save(r);
    }

    // ---------------- SET POINTS ----------------

    @Test
    void setPoints_success() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        Ranking r = new Ranking();
        r.setUserId(1L);
        r.setPoints(10);

        when(rankingRepository.findByuserId(1L)).thenReturn(Optional.of(r));
        when(rankingRepository.save(any(Ranking.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Ranking result = service.setPoints(1L, 200);

        assertThat(result.getPoints()).isEqualTo(200);
        verify(rankingRepository).save(r);
    }

    @Test
    void setPoints_notFound_throws() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        when(rankingRepository.findByuserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPoints(1L, 100))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------------- TOP ----------------

    @Test
    void getTop_returnsList() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        Ranking r1 = new Ranking();
        r1.setPoints(100);

        when(rankingRepository.findTopOrderDefault(any()))
                .thenReturn(List.of(r1));

        List<Ranking> result = service.getTop(5);

        assertThat(result).hasSize(1);
        verify(rankingRepository).findTopOrderDefault(any());
    }

    // ---------------- DELETE ----------------

    @Test
    void deleteByUserId_found_returnsTrue() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        Ranking r = new Ranking();
        r.setUserId(1L);

        when(rankingRepository.findByuserId(1L)).thenReturn(Optional.of(r));

        boolean result = service.deleteByUserId(1L);

        assertThat(result).isTrue();
        verify(rankingRepository).delete(r);
    }

    @Test
    void deleteByUserId_notFound_returnsFalse() {
        RankingService service = new RankingService(rankingRepository, userRepository);

        when(rankingRepository.findByuserId(1L)).thenReturn(Optional.empty());

        boolean result = service.deleteByUserId(1L);

        assertThat(result).isFalse();
        verify(rankingRepository, never()).delete(any());
    }
}
