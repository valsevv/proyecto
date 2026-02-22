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
            '<p class="error">Error loading lobbies. Please try again.</p>';
    }
}

// Display lobbies in the UI
function displayLobbies(lobbies) {
    const lobbyList = document.getElementById('lobbyList');
    
    if (lobbies.length === 0) {
        lobbyList.innerHTML = '<p class="no-lobbies">No lobbies available. Create one!</p>';
        return;
    }

    let html = '<table class="lobby-table"><thead><tr>';
    html += '<th>Creator</th>';
    html += '<th>Players</th>';
    html += '<th>Status</th>';
    html += '<th>Action</th>';
    html += '</tr></thead><tbody>';

    lobbies.forEach(lobby => {
        const canJoin = !lobby.isFull && lobby.status === 'WAITING';
        const statusText = lobby.isFull ? 'Full' : lobby.status;
        
        html += '<tr>';
        html += `<td>${lobby.creatorUsername}</td>`;
        html += `<td>${lobby.playerCount}/${lobby.maxPlayers}</td>`;
        html += `<td class="status-${lobby.status.toLowerCase()}">${statusText}</td>`;
        html += '<td>';
        
        if (canJoin) {
            html += `<button class="btn-join" onclick="joinLobby('${lobby.lobbyId}')">Join</button>`;
        } else {
            html += '<span class="unavailable">Unavailable</span>';
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
