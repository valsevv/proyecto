const music = document.getElementById('bgMusic');
const musicToggleBtn = document.getElementById('musicToggleBtn');
let musicEnabled = true;

function updateMusicButton() {
    musicToggleBtn.textContent = musicEnabled ? '🔊' : '🔇';
    const label = musicEnabled ? 'Desactivar música' : 'Activar música';
    musicToggleBtn.title = label;
    musicToggleBtn.setAttribute('aria-label', label);
}

document.body.addEventListener('click', () => {
    music.volume = 0.4;
    music.loop = true;

    if (musicEnabled) {
        music.play().catch(() => {
            musicEnabled = false;
            updateMusicButton();
        });
    }
}, { once: true });

musicToggleBtn.addEventListener('click', async () => {
    musicEnabled = !musicEnabled;

    if (musicEnabled) {
        try {
            await music.play();
        } catch (error) {
            console.warn('No se pudo reproducir la música:', error);
            musicEnabled = false;
        }
    } else {
        music.pause();
    }

    updateMusicButton();
});

// Logout 
document.getElementById('logoutBtn').addEventListener('click', async () => {
    try {
        const response = await fetch(`/api/auth/logout`, {
            method: 'POST',
            credentials: 'include'
        });

        if (response.ok) {
            // Clear any remaining session data
            sessionStorage.clear();
            // Redirect to login
            window.location.href = '/login';
        } else {
            alert('Logout failed. Please try again.');
        }
    } catch (error) {
        console.error('Logout error:', error);
        alert('Error during logout.');
    }
});


// Load Game 
document.getElementById('loadGamesBtn').addEventListener('click', async () => {
    window.location.href = '/saved-games';

});



document.getElementById('rankingBtn').addEventListener('click', async () => {
    window.location.href = '/ranking';

});

document.getElementById('creditsBtn').addEventListener('click', async () => {
    window.location.href = '/credits';
});

