package com.example.proyect.persistencia;

import jakarta.persistence.*;

@Entity
@Table(
        name = "players",
        uniqueConstraints = @UniqueConstraint(name = "cons_players_username", columnNames = "username")
)
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    // opcional: si todavía no querés login real, lo dejás null
    @Column(nullable = false, length = 200)
    private String password;

    @Column(nullable = false)
    private int ganadas = 0;

    @Column(nullable = false)
    private int perdidas = 0;

    @Column(nullable = false)
    private int puntaje = 0;

    // getters/setters
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getGanadas() { return ganadas; }
    public void setGanadas(int ganadas) { this.ganadas = ganadas; }

    public int getPerdidas() { return perdidas; }
    public void setPerdidas(int perdidas) { this.perdidas = perdidas; }

    public int getPuntaje() { return puntaje; }
    public void setPuntaje(int puntaje) { this.puntaje = puntaje; }
}
