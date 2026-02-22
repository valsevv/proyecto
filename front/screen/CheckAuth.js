//Cada pantalla llamada a este metodo para ver si el usuario sigue autenticado

async function checkAuth() {
    try {
        const response = await fetch(`${API_BASE}/users/me`, {
            method: 'GET',
            credentials: 'include'
        });
        
        if (!response.ok) {
            alert('Please login first');
            window.location.href = '/login';
            return false;
        }
        
        return true;
    } catch (error) {
        console.error('Error de autenticacion:', error);
        alert('Error de autenticacion, Por favor logearse nuevamente.');
        window.location.href = '/login';
        return false;
    }
}