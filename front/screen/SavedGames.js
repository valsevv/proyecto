document.addEventListener("DOMContentLoaded", async () => {
    const gamesList = document.getElementById("gamesList");

    document.getElementById('loadGamesBtn').addEventListener('click', async () => {
    try {
        const response = await fetch(`${API_BASE}/auth/saved`, {
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



function loadGame(id) {
    location.href = `/game/${id}`;
}

async function deleteGame(id) {
    if (!confirm("¿Eliminar esta partida?")) return;

    await fetch(`/api/games/${id}`, { method: "DELETE" });
    location.reload();
}