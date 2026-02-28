package com.example.proyect.RankingTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.auth.RankingTopDTO;
import com.example.proyect.auth.service.RankingService;
import com.example.proyect.persistence.classes.Ranking;

import jakarta.persistence.EntityNotFoundException;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class RankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RankingService rankingService;

    @Test
    void createSnapshot_shouldReturnCreatedStatus() throws Exception {
        Ranking ranking = new Ranking();
        ranking.setUserId(1L);
        ranking.setPoints(100);
        org.springframework.test.util.ReflectionTestUtils.setField(ranking, "id", 1L);
        ranking.setReachedAt(OffsetDateTime.now());

        org.mockito.Mockito.when(rankingService.createSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(ranking);

        mockMvc.perform(post("/api/ranking/snapshot")
                .param("userId", "1")
                .param("points", "100"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.points").value(100));
    }

    @Test
    void createSnapshot_userNotFound_shouldReturnNotFound() throws Exception {
        org.mockito.Mockito.when(rankingService.createSnapshot(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new EntityNotFoundException("User not found"));

        mockMvc.perform(post("/api/ranking/snapshot")
                .param("userId", "999")
                .param("points", "100"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistory_shouldReturnRankingList() throws Exception {
        Ranking r1 = new Ranking();
        r1.setUserId(1L);
        r1.setPoints(100);
        Ranking r2 = new Ranking();
        r2.setUserId(1L);
        r2.setPoints(150);
        List<Ranking> history = List.of(r1, r2);

        org.mockito.Mockito.when(rankingService.getHistoryByUserId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(history);

        mockMvc.perform(get("/api/ranking/history/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getBest_shouldReturnBestRanking() throws Exception {
        Ranking best = new Ranking();
        best.setUserId(1L);
        best.setPoints(200);
        org.springframework.test.util.ReflectionTestUtils.setField(best, "id", 1L);

        org.mockito.Mockito.when(rankingService.getBestByUserId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(best);

        mockMvc.perform(get("/api/ranking/best/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(200));
    }

    @Test
    void getBest_noRanking_shouldReturnNotFound() throws Exception {
        org.mockito.Mockito.when(rankingService.getBestByUserId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(null);

        mockMvc.perform(get("/api/ranking/best/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTop_shouldReturnTopRankings() throws Exception {
        RankingTopDTO top1 = new RankingTopDTO(1L, "player1", 10, 5, 200);
        RankingTopDTO top2 = new RankingTopDTO(2L, "player2", 8, 7, 150);
        List<RankingTopDTO> topList = List.of(top1, top2);

        org.mockito.Mockito.when(rankingService.getTop(org.mockito.ArgumentMatchers.anyInt())).thenReturn(topList);

        mockMvc.perform(get("/api/ranking/top")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].username").value("player1"))
                .andExpect(jsonPath("$[0].points").value(200));
    }

    @Test
    void getTop_defaultLimit_shouldReturn10() throws Exception {
        org.mockito.Mockito.when(rankingService.getTop(10)).thenReturn(List.of());

        mockMvc.perform(get("/api/ranking/top"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteHistory_shouldReturnNoContent() throws Exception {
        org.mockito.Mockito.doNothing().when(rankingService).deleteByUserId(org.mockito.ArgumentMatchers.anyLong());

        mockMvc.perform(delete("/api/ranking/history/1"))
                .andExpect(status().isNoContent());
    }
}
