package com.example.proyect.RankingTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.auth.RankingTopDTO;
import com.example.proyect.auth.service.RankingService;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.Ranking;
import com.example.proyect.persistence.classes.User;


@SpringBootTest
@Transactional
class RankingServiceFunctionalTest {

    @Autowired
    private RankingService rankingService;


    @Autowired
    private UserService userService;

    @Test
    void shouldCreateSnapshot() {

        User user = userService.register("alexis", "alexis@mail.com", "123456");

        Ranking ranking = rankingService.createSnapshot(user.getUserId(), 100);

        assertNotNull(ranking.getId());
        assertEquals(100, ranking.getPoints());
    }

    @Test
    void shouldReturnBestScore() {

       User user = userService.register("alexis", "alexis@mail.com", "123456");

        rankingService.createSnapshot(user.getUserId(), 50);
        rankingService.createSnapshot(user.getUserId(), 200);
        rankingService.createSnapshot(user.getUserId(), 150);

        Ranking best = rankingService.getBestByUserId(user.getUserId());

        assertEquals(200, best.getPoints());
    }

    @Test
    void shouldReturnTopOrdered() {
        long timestamp = System.currentTimeMillis();
        User u1 = userService.register("test_top1_" + timestamp, "test1" + timestamp + "@mail.com", "12345678");
        User u2 = userService.register("test_top2_" + timestamp, "test2" + timestamp + "@mail.com", "12345689");
        User u3 = userService.register("test_top3_" + timestamp, "test3" + timestamp + "@mail.com", "12345689");

        rankingService.createSnapshot(u1.getUserId(), 100);
        rankingService.createSnapshot(u2.getUserId(), 300);
        rankingService.createSnapshot(u3.getUserId(), 200);

        List<RankingTopDTO> top = rankingService.getTop(100); // Get more to ensure we find our users

        // Filter to only our test users
        List<RankingTopDTO> ourUsers = top.stream()
            .filter(dto -> dto.getUserId().equals(u1.getUserId()) || 
                          dto.getUserId().equals(u2.getUserId()) || 
                          dto.getUserId().equals(u3.getUserId()))
            .toList();

        assertEquals(3, ourUsers.size());
        assertEquals(u2.getUserId(), ourUsers.get(0).getUserId()); // 300 points
        assertEquals(u3.getUserId(), ourUsers.get(1).getUserId()); // 200 points
        assertEquals(u1.getUserId(), ourUsers.get(2).getUserId()); // 100 points
    }
}
