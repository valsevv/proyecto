import MainScene from './scenes/MainScene.js';
import HudScene from './scenes/HudScene.js';
import NetworkManager from './network/NetworkManager.js';

// World dimensions (larger than the visible window)
export const WORLD_WIDTH = 2400;
export const WORLD_HEIGHT = 1800;

// Shared network instance — scenes import this
export const network = new NetworkManager('wss://camilla-traplike-scribbly.ngrok-free.dev/ws');

const config = {
    type: Phaser.AUTO,
    width: 800,
    height: 600,
    scene: [MainScene, HudScene]
};

const game = new Phaser.Game(config);
