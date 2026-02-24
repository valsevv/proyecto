const API_BASE = 'http://localhost:8080';

const gamesList = document.getElementById('gamesList');

// Load saved games on page load
document.addEventListener('DOMContentLoaded', () => {
    loadSavedGames();
    
    document.getElementById('refreshBtn').addEventListener('click', loadSavedGames);
    document.getElementById('backBtn').addEventListener('click', () => {
        window.location.href = '/menu';
    });
});

async function loadSavedGames() {
    gamesList.innerHTML = '<p class="loading">Cargando partidas...</p>';
    
    try {
        const response = await fetch(`${API_BASE}/api/games/saved`, {
            method: 'GET',
            credentials: 'include'
        });
        
        if (response.ok) {
            const games = await response.json();
            displayGames(games);
        } else if (response.status === 401) {
            window.location.href = '/login';
        } else {
            gamesList.innerHTML = '<p class="error">Error al cargar partidas.</p>';
        }
    } catch (error) {
        console.error('Error loading saved games:', error);
        gamesList.innerHTML = '<p class="error">Error de conexión.</p>';
    }
}

function displayGames(games) {
    if (games.length === 0) {
        gamesList.innerHTML = '<p class="no-lobbies">No tienes partidas guardadas.</p>';
        return;
    }

    let html = '<table class="lobby-table"><thead><tr>';
    html += '<th>Rival</th>';
    html += '<th>Turno</th>';
    html += '<th>Fecha</th>';
    html += '<th>Acciones</th>';
    html += '</tr></thead><tbody>';

    games.forEach(game => {
        const rivalName = game.rival?.username || 'Desconocido';
        const turnInfo = game.currentTurn !== null ? `Turno ${game.currentTurn}` : '-';
        const dateStr = new Date(game.startedAt).toLocaleDateString();
        
        html += '<tr>';
        html += `<td>${rivalName}</td>`;
        html += `<td>${turnInfo}</td>`;
        html += `<td>${dateStr}</td>`;
        html += '<td>';
        html += `<button class="btn-join" onclick="loadGame(${game.gameId})">Continuar</button> `;
        html += `<button class="btn-delete" onclick="deleteGame(${game.gameId})">Eliminar</button>`;
        html += '</td></tr>';
    });

    html += '</tbody></table>';
    gamesList.innerHTML = html;
}

async function loadGame(gameId) {
    try {
        // Create a load-game lobby via REST API
        const response = await fetch(`${API_BASE}/api/lobby/load-game/${gameId}`, {
            method: 'POST',
            credentials: 'include'
        });

        if (response.ok) {
            const result = await response.json();
            if (result.success) {
                // Store lobby ID and redirect to waiting room
                sessionStorage.setItem('currentLobbyId', result.lobbyId);
                sessionStorage.setItem('loadingGameId', gameId);
                window.location.href = '/lobby-waiting';
            } else {
                alert('Error al cargar la partida: ' + (result.error || 'Error desconocido'));
            }
        } else if (response.status === 401) {
            window.location.href = '/login';
        } else {
            const error = await response.json();
            alert('Error al cargar la partida: ' + (error.error || 'Error desconocido'));
        }
    } catch (error) {
        console.error('Error loading game:', error);
        alert('Error de conexión');
    }
}

async function deleteGame(id) {
    if (!confirm('¿Eliminar esta partida?')) return;

    try {
        const response = await fetch(`${API_BASE}/api/games/${id}`, {
            method: 'DELETE',
            credentials: 'include'
        });

        if (response.ok || response.status === 204) {
            loadSavedGames(); // Refresh the list
        } else if (response.status === 401) {
            window.location.href = '/login';
        } else {
            alert('Error al eliminar la partida');
        }
    } catch (error) {
        console.error('Error deleting game:', error);
        alert('Error de conexión');
    }
}