package com.example.proyect.RankingTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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

        User u1 = userService.register("alexis1", "alexis1@mail.com", "12345678");
        User u2 = userService.register("alexis2", "alexis2@mail.com", "12345689");
        User u3 = userService.register("alexis3", "alexis3@mail.com", "12345689");

        rankingService.createSnapshot(u1.getUserId(), 100);
        rankingService.createSnapshot(u2.getUserId(), 300);
        rankingService.createSnapshot(u3.getUserId(), 200);

        List<Ranking> top = rankingService.getTop(3);

        assertEquals(300, top.get(0).getPoints());
        assertEquals(200, top.get(1).getPoints());
        assertEquals(100, top.get(2).getPoints());
    }
}
