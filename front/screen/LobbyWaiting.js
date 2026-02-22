const API_BASE = 'http://localhost:8080/api';
        let checkInterval = null;

        async function checkLobbyStatus() {
            if (!await checkAuth()) return;

            try {
                const response = await fetch(`${API_BASE}/lobby/my-lobby`, {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    credentials: 'include'
                });

                const result = await response.json();

                if (!result.inLobby) {
                    // No longer in a lobby, go back to browser
                    window.location.href = '/lobby-browser';
                    return;
                }

                // Update UI with lobby info
                displayLobbyInfo(result);

                // If lobby is ready (2 players), redirect to game
                if (result.playerCount >= 2 || result.status === 'READY') {
                    console.log('Lobby ready! Starting game...');
                    sessionStorage.setItem('currentLobbyId', result.lobbyId);
                    window.location.href = '/game';
                }
            } catch (error) {
                console.error('Error checking lobby status:', error);
            }
        }

        function displayLobbyInfo(lobbyInfo) {
            const detailsDiv = document.getElementById('lobbyDetails');
            detailsDiv.innerHTML = `
                <p><strong>Lobby Creator:</strong> ${lobbyInfo.creatorUsername}</p>
                <p><strong>Players:</strong> ${lobbyInfo.playerCount}/2</p>
                <p><strong>Status:</strong> ${lobbyInfo.status}</p>
            `;
        }

        async function leaveLobby() {
            if (!await checkAuth()) return;

            if (!confirm('Are you sure you want to leave this lobby?')) {
                return;
            }

            try {
                const response = await fetch(`${API_BASE}/lobby/leave`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    credentials: 'include'
                });

                const result = await response.json();

                if (result.success) {
                    sessionStorage.removeItem('currentLobbyId');
                    window.location.href = '/lobby-browser';
                } else {
                    alert('Failed to leave lobby: ' + (result.error || 'Unknown error'));
                }
            } catch (error) {
                console.error('Error leaving lobby:', error);
                alert('Error leaving lobby. Please try again.');
            }
        }

        // Initialize
        document.addEventListener('DOMContentLoaded', () => {
            checkAuth();
            
            document.getElementById('leaveLobbyBtn').addEventListener('click', leaveLobby);

            // Initial check
            checkLobbyStatus();

            // Check every 2 seconds
            checkInterval = setInterval(checkLobbyStatus, 2000);
        });

        // Cleanup
        window.addEventListener('beforeunload', () => {
            if (checkInterval) {
                clearInterval(checkInterval);
            }
        });