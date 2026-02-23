package com.example.proyect.lobby.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.proyect.lobby.Lobby;
import com.example.proyect.lobby.LobbyStatus;

@Service
public class LobbyService {

    private final Map<String, Lobby> lobbies = new ConcurrentHashMap<>();
    private final Map<Long, String> userToLobby = new ConcurrentHashMap<>(); // Track which lobby each user is in

    /**
     * Creates a new lobby with the given creator.
     * @return The created Lobby
     */
    public Lobby createLobby(String creatorUsername, Long userId) {
        // Remove user from any existing lobby first
        String existingLobbyId = userToLobby.get(userId);
        if (existingLobbyId != null) {
            leaveLobby(userId);
        }

        String lobbyId = UUID.randomUUID().toString();
        Lobby lobby = new Lobby(lobbyId, creatorUsername);
        lobby.addPlayer(userId);
        
        lobbies.put(lobbyId, lobby);
        userToLobby.put(userId, lobbyId);
        
        return lobby;
    }

    /**
     * Get all lobbies that are not yet started (WAITING or READY).
     */
    public List<Lobby> getAllLobbies() {
        return lobbies.values().stream()
                .filter(lobby -> lobby.getStatus() != LobbyStatus.STARTED)
                .collect(Collectors.toList());
    }

    /**
     * Join an existing lobby.
     * @return The lobby if successful
     * @throws IllegalStateException if lobby is full or doesn't exist
     */
    public Lobby joinLobby(String lobbyId, Long userId) {
        Lobby lobby = lobbies.get(lobbyId);
        if (lobby == null) {
            throw new IllegalArgumentException("Lobby no existe");
        }
        if (lobby.isFull()) {
            throw new IllegalStateException("Lobby lleno");
        }
        if (lobby.getStatus() == LobbyStatus.STARTED) {
            throw new IllegalStateException("Lobby ya comenzó");
        }

        // Remove user from any existing lobby first
        String existingLobbyId = userToLobby.get(userId);
        if (existingLobbyId != null && !existingLobbyId.equals(lobbyId)) {
            leaveLobby(userId);
        }

        lobby.addPlayer(userId);
        userToLobby.put(userId, lobbyId);
        
        return lobby;
    }

    /**
     * Remove a lobby (called when game starts).
     */
    public void removeLobby(String lobbyId) {
        Lobby lobby = lobbies.remove(lobbyId);
        if (lobby != null) {
            // Clean up user mappings
            for (Long userId : lobby.getPlayerIds()) {
                userToLobby.remove(userId);
            }
        }
    }

    /**
     * Find which lobby a player is in.
     */
    public Optional<Lobby> getLobbyByUserId(Long userId) {
        String lobbyId = userToLobby.get(userId);
        if (lobbyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(lobbies.get(lobbyId));
    }

    /**
     * Get a lobby by its ID.
     */
    public Optional<Lobby> getLobbyById(String lobbyId) {
        return Optional.ofNullable(lobbies.get(lobbyId));
    }

    /**
     * Leave a lobby (cleanup).
     */
    public void leaveLobby(Long userId) {
        String lobbyId = userToLobby.remove(userId);
        if (lobbyId != null) {
            Lobby lobby = lobbies.get(lobbyId);
            if (lobby != null) {
                // If lobby is empty after user leaves, remove it
                // Note: Current Lobby class doesn't have removePlayer, so we'll just clean the mapping
                // The lobby will be cleaned up when it's empty or times out
                if (lobby.getPlayerIds().size() == 1 && lobby.getPlayerIds().contains(userId)) {
                    lobbies.remove(lobbyId);
                }
            }
        }
    }
}
