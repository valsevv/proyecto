package com.example.proyect.auth;

public class RankingTopDTO {

    private Long userId;
    private String username;
    private int wins;
    private int losses;
    private int points;

    public RankingTopDTO(Long userId, String username, int wins, int losses, int points) {
        this.userId = userId;
        this.username = username;
        this.wins = wins;
        this.losses = losses;
        this.points = points;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public int getPoints() { return points; }
}