import MainScene from './scenes/MainScene.js';
import HudScene from './scenes/HudScene.js';
import { VIEW_WIDTH, VIEW_HEIGHT } from './shared/constants.js';

const config = {
    type: Phaser.AUTO,
    width: VIEW_WIDTH,
    height: VIEW_HEIGHT,
    scene: [MainScene, HudScene]
};

new Phaser.Game(config);
