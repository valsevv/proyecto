import MainScene from './scenes/MainScene.js';
import HudScene from './scenes/HudScene.js';
import NetworkManager from './network/NetworkManager.js';

// World dimensions
export const WORLD_WIDTH = 2400;
export const WORLD_HEIGHT = 1800;

export const network = new NetworkManager('wss://camilla-traplike-scribbly.ngrok-free.dev/ws');

const config = {
    type: Phaser.AUTO,
    width: 1200,
    height: 800,
    scene: [MainScene, HudScene]
};

new Phaser.Game(config);
