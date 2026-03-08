import LobbyScene from './scenes/LobbyScene.js';
import MainScene from './scenes/MainScene.js';
import HudScene from './scenes/HudScene.js';
import { VIEW_WIDTH, VIEW_HEIGHT } from './shared/constants.js';

const config = {
    type: Phaser.AUTO,
    parent: 'game-container',
    width: VIEW_WIDTH,
    height: VIEW_HEIGHT,
    backgroundColor: '#000000',
    scale: {
        // Keep a stable virtual viewport and scale it to each device screen.
        mode: Phaser.Scale.FIT,
        autoCenter: Phaser.Scale.CENTER_BOTH
    },
    scene: [LobbyScene, MainScene, HudScene]
};

new Phaser.Game(config);
