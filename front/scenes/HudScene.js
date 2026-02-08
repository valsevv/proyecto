import Minimap from '../ui/Minimap.js';

const TEAM_COLORS = [0x00ff00, 0xff4444];

/**
 * HudScene — overlay for UI elements (minimap, etc.).
 * Runs in parallel with MainScene; not affected by camera scroll.
 */
export default class HudScene extends Phaser.Scene {
    constructor() {
        super('HudScene');
    }

    create() {
        const radius = 80;
        const margin = 20;
        this.minimap = new Minimap(
            this,
            800 - radius - margin,
            600 - radius - margin,
            radius
        );
    }

    update() {
        const mainScene = this.scene.get('MainScene');
        if (!mainScene) return;

        const allDrones = mainScene.getAllDrones();
        if (!allDrones.length) return;

        const tracked = allDrones.map(({ drone, playerIndex }) => ({
            x: drone.sprite.x,
            y: drone.sprite.y,
            color: mainScene.selectedDrone === drone
                ? 0xffff00
                : TEAM_COLORS[playerIndex]
        }));

        this.minimap.update(mainScene.cameras.main, tracked);
    }
}
