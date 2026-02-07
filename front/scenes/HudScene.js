import Minimap from '../ui/Minimap.js';

/**
 * HudScene - Escena superpuesta para elementos de interfaz.
 * Se ejecuta en paralelo con MainScene y no se ve afectada por el scroll de la cámara.
 */
export default class HudScene extends Phaser.Scene {
    constructor() {
        super('HudScene');
    }

    create() {
        // Minimap estilo radar en la esquina inferior derecha
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
        if (!mainScene || !mainScene.drones.length) return;

        // Rastrear todos los drones en el minimap
        const tracked = mainScene.drones.map(drone => ({
            x: drone.sprite.x,
            y: drone.sprite.y,
            color: mainScene.selectedDrone === drone ? 0xffff00 : 0x00ff00
        }));

        this.minimap.update(mainScene.cameras.main, tracked);
    }
}
