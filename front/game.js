import MainScene from './scenes/MainScene.js';
import HudScene from './scenes/HudScene.js';

// Tamaño del mundo (más grande que la ventana visible)
export const WORLD_WIDTH = 2400;
export const WORLD_HEIGHT = 1800;

// --- WebSocket connection ---
const socket = new WebSocket('ws://localhost:8080/ws');

socket.onopen = () => {
    console.log('Connected to server');
    socket.send('hello from client');
};

socket.onmessage = (event) => {
    console.log('Server says:', event.data);
};

socket.onclose = () => {
    console.log('Disconnected from server');
};

socket.onerror = (err) => {
    console.error('WebSocket error:', err);
};

export { socket };
// ----------------------------

const config = {
    type: Phaser.AUTO,
    width: 800,
    height: 600,
    scene: [MainScene, HudScene]
};

const game = new Phaser.Game(config);
