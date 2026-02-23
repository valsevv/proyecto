const API_BASE = 'http://localhost:8080/api';


document.getElementById('loadGamesBtn').addEventListener('click', async () => {
    try {
        const response = await fetch(`${API_BASE}/api/games/saved`, {
            method: 'GET',
            credentials: 'include'
        });
        
        if (response.ok) {
            const games = await response.json();
            if (games.length === 0) {
                gamesList.innerHTML = "<p>No tienes partidas guardadas.</p>";
            return;
            }

            games.forEach(game => {
                const card = document.createElement("div");
                card.className = "game-card";

                card.innerHTML = `
                    <div class="game-info">
                        <strong>ID:</strong> ${game.id}<br>
                        <strong>Jugador 1:</strong> ${game.player1Username}<br>
                        <strong>Jugador 2:</strong> ${game.player2Username}<br>
                        <strong>Fecha:</strong> ${new Date(game.startedAt).toLocaleString()}
                    </div>
                    <div class="game-actions">
                        <button class="menu-btn" onclick="loadGame(${game.id})">
                            Continuar
                        </button>
                        <button class="menu-btn" onclick="deleteGame(${game.id})">
                            Eliminar
                        </button>
                    </div>
                `;

                gamesList.appendChild(card);
            });

        } else {
             alert('Get saved games failed. Please try again.');
             window.location.href = '/login';
        }
    } catch (error) {
         gamesList.innerHTML = "<p>Error cargando partidas.</p>";
    }
});
   


document.getElementById('loadGamesBtn').addEventListener('click', async () => {
    try {
        const response = await fetch(`${API_BASE}/api/games/game/${id}`, {
            method: 'GET',
            credentials: 'include'
        });

        if (response.ok) {
            const game = await response.json();
            //            
       } else {
             alert('Error al cargar la partida');
             window.location.href = '/login';
        }
    } catch (error) {
         gamesList.innerHTML = "<p>Error al cargar la partida.</p>";
    }
});

      

async function deleteGame(id) {
    if (!confirm("¿Eliminar esta partida?")) return;

    await fetch(`/api/games/${id}`, { method: "DELETE" });
    location.reload();
}