// LobbyBrowser.js - Handles lobby list and creation

const API_BASE = 'http://localhost:8080/api';
let refreshInterval = null;

// Fetch and display lobbies
async function fetchLobbies() {
    if (!await checkAuth()) return;

    try {
        const response = await fetch(`${API_BASE}/lobby/list`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to fetch lobbies');
        }

        const lobbies = await response.json();
        displayLobbies(lobbies);
    } catch (error) {
        console.error('Error fetching lobbies:', error);
        document.getElementById('lobbyList').innerHTML = 
            '<p class="error">Error al cargar los lobbies. Inténtalo de nuevo.</p>';
    }
}

// Display lobbies in the UI
function displayLobbies(lobbies) {
    const lobbyList = document.getElementById('lobbyList');
    
    if (lobbies.length === 0) {
        lobbyList.innerHTML = '<p class="no-lobbies">No hay lobbies disponibles. Crea uno!</p>';
        return;
    }

    let html = '<table class="lobby-table"><thead><tr>';
    html += '<th>Creador</th>';
    html += '<th>Jugadores</th>';
    html += '<th>Estado</th>';
    html += '<th>Acción</th>';
    html += '</tr></thead><tbody>';

    lobbies.forEach(lobby => {
        const canJoin = !lobby.isFull && lobby.status === 'WAITING';
        let statusText = lobby.isFull ? 'Llena' : lobby.status;
        
        // Special styling for load-game lobbies (invitations to resume)
        const isInvitation = lobby.isLoadGame;
        if (isInvitation) {
            statusText = '🔄 Partida Guardada';
        }
        
        html += `<tr class="${isInvitation ? 'load-game-invite' : ''}">`;
        html += `<td>${lobby.creatorUsername}</td>`;
        html += `<td>${lobby.playerCount}/${lobby.maxPlayers}</td>`;
        html += `<td class="status-${lobby.status.toLowerCase()}">${statusText}</td>`;
        html += '<td>';
        
        if (canJoin) {
            const buttonText = isInvitation ? 'Continuar Partida' : 'Unirse';
            const buttonClass = isInvitation ? 'btn-join btn-resume' : 'btn-join';
            html += `<button class="${buttonClass}" onclick="joinLobby('${lobby.lobbyId}')">${buttonText}</button>`;
        } else {
            html += '<span class="unavailable">No Disponible</span>';
        }
        
        html += '</td></tr>';
    });

    html += '</tbody></table>';
    lobbyList.innerHTML = html;
}

// Create a new lobby
async function createLobby() {
    if (!await checkAuth()) return;

    try {
        const response = await fetch(`${API_BASE}/lobby/create`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include'
        });

        const result = await response.json();

        if (result.success) {
            console.log('Lobby created:', result.lobbyId);
            // Store lobby ID and redirect to waiting page
            sessionStorage.setItem('currentLobbyId', result.lobbyId);
            window.location.href = '/lobby-waiting';
        } else {
            alert('Failed to create lobby: ' + (result.error || 'Unknown error'));
        }
    } catch (error) {
        console.error('Error creating lobby:', error);
        alert('Error creating lobby. Please try again.');
    }
}

// Join an existing lobby
async function joinLobby(lobbyId) {
    if (!await checkAuth()) return;

    try {
        const response = await fetch(`${API_BASE}/lobby/join/${lobbyId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include'
        });

        const result = await response.json();

        if (result.success) {
            console.log('Joined lobby:', lobbyId);
            // Store lobby ID and redirect to game
            sessionStorage.setItem('currentLobbyId', lobbyId);
            
            // If lobby is ready (2 players), go directly to game
            if (result.isReady) {
                window.location.href = '/game';
            } else {
                // Otherwise wait for second player
                window.location.href = '/lobby-waiting';
            }
        } else {
            alert('Failed to join lobby: ' + (result.error || 'Unknown error'));
        }
    } catch (error) {
        console.error('Error joining lobby:', error);
        alert('Error joining lobby. Please try again.');
    }
}

// Initialize page
document.addEventListener('DOMContentLoaded', () => {
    checkAuth();
    
    // Set up button handlers
    document.getElementById('createLobbyBtn').addEventListener('click', createLobby);
    document.getElementById('refreshBtn').addEventListener('click', fetchLobbies);
    document.getElementById('backBtn').addEventListener('click', () => {
        window.location.href = '/menu';
    });

    // Initial fetch
    fetchLobbies();

    // Auto-refresh every 15 seconds 
    refreshInterval = setInterval(fetchLobbies, 15000);
});

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    if (refreshInterval) {
        clearInterval(refreshInterval);
    }
});
