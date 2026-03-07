import LobbyScene from './scenes/LobbyScene.js';
import MainScene from './scenes/MainScene.js';
import HudScene from './scenes/HudScene.js';

const config = {
    type: Phaser.AUTO,
    width: window.innerWidth,
    height: window.innerHeight,
    scale: {
        mode: Phaser.Scale.RESIZE,
        autoCenter: Phaser.Scale.CENTER_BOTH
    },
    scene: [LobbyScene, MainScene, HudScene]
};

new Phaser.Game(config);
