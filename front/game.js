import MainScene from './scenes/MainScene.js';
import HudScene from './scenes/HudScene.js';

const config = {
    type: Phaser.AUTO,
    width: 1200,
    height: 600,
    scene: [MainScene, HudScene]
};

new Phaser.Game(config);
