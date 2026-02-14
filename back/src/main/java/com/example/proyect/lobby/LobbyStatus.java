package com.example.proyect.lobby;

// esta clase define el estado del lobby antes de crear la partida
public enum LobbyStatus {

    WAITING, //0 o 1 jugadores
    READY,   // ya hay 2 jugadores, se puede comenzar
    STARTED  // ya se creó la partida
}
