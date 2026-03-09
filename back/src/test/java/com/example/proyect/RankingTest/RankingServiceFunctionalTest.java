package com.example.proyect.RankingTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.proyect.auth.RankingTopDTO;
import com.example.proyect.auth.service.RankingService;
import com.example.proyect.auth.service.UserService;
import com.example.proyect.persistence.classes.Ranking;
import com.example.proyect.persistence.classes.User;
import com.example.proyect.persistence.repos.UserRepository;


@SpringBootTest
@Transactional
class RankingServiceFunctionalTest {

    @Autowired
    private RankingService rankingService;


    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldCreateSnapshot() {

        User user = userService.register("alexis", "alexis@mail.com", "123456");

        Ranking ranking = rankingService.createSnapshot(user.getUserId());

        assertNotNull(ranking.getId());
        assertEquals(user.getScore(), ranking.getPoints());
    }

    @Test
    void shouldReturnBestScore() { 

        long timestamp = System.currentTimeMillis();
        User user = userService.register("best_" + timestamp, "best" + timestamp + "@mail.com", "123456");

        user.registerWin(); // 11
        userRepository.save(user);
        rankingService.createSnapshot(user.getUserId());

        user.registerWin(); // 22
        userRepository.save(user);
        rankingService.createSnapshot(user.getUserId());

        user.registerWin(); // 33
        userRepository.save(user);
        rankingService.createSnapshot(user.getUserId());

        Ranking best = rankingService.getBestByUserId(user.getUserId());

        assertEquals(33, best.getPoints());
    }

    @Test
    void shouldReturnTopOrdered() {
        long timestamp = System.currentTimeMillis();
        User u1 = userService.register("test_top1_" + timestamp, "test1" + timestamp + "@mail.com", "12345678");
        User u2 = userService.register("test_top2_" + timestamp, "test2" + timestamp + "@mail.com", "12345689");
        User u3 = userService.register("test_top3_" + timestamp, "test3" + timestamp + "@mail.com", "12345689");

        u1.registerWin(); // 10
        userRepository.save(u1);

        u2.registerWin();
        u2.registerWin();
        u2.registerWin(); // 30
        userRepository.save(u2);

        u3.registerWin();
        u3.registerWin(); // 20
        userRepository.save(u3);

        rankingService.createSnapshot(u1.getUserId());
        rankingService.createSnapshot(u2.getUserId());
        rankingService.createSnapshot(u3.getUserId());

        List<RankingTopDTO> top = rankingService.getTop(100); // Get more to ensure we find our users

        // Filter to only our test users
        List<RankingTopDTO> ourUsers = top.stream()
            .filter(dto -> dto.getUserId().equals(u1.getUserId()) || 
                          dto.getUserId().equals(u2.getUserId()) || 
                          dto.getUserId().equals(u3.getUserId()))
            .toList();

        assertEquals(3, ourUsers.size());
        assertEquals(u2.getUserId(), ourUsers.get(0).getUserId()); 
        assertEquals(u3.getUserId(), ourUsers.get(1).getUserId()); 
        assertEquals(u1.getUserId(), ourUsers.get(2).getUserId());
    }

    @Test
    void shouldReturnSingleTopRowPerUser() {
        long timestamp = System.currentTimeMillis();
        User u1 = userService.register("test_unique_top1_" + timestamp, "unique1" + timestamp + "@mail.com", "12345678");
        User u2 = userService.register("test_unique_top2_" + timestamp, "unique2" + timestamp + "@mail.com", "12345689");

        rankingService.createSnapshot(u1.getUserId());
        rankingService.createSnapshot(u1.getUserId());
        rankingService.createSnapshot(u1.getUserId());
        rankingService.createSnapshot(u2.getUserId());
        rankingService.createSnapshot(u2.getUserId());

        List<RankingTopDTO> top = rankingService.getTop(100);

        long countU1 = top.stream().filter(dto -> dto.getUserId().equals(u1.getUserId())).count();
        long countU2 = top.stream().filter(dto -> dto.getUserId().equals(u2.getUserId())).count();

        assertEquals(1L, countU1);
        assertEquals(1L, countU2);
        assertTrue(top.stream().anyMatch(dto -> dto.getUserId().equals(u1.getUserId())));
        assertTrue(top.stream().anyMatch(dto -> dto.getUserId().equals(u2.getUserId())));
    }
}
