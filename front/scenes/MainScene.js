import HexGrid from '../utils/HexGrid.js';
import Drone from '../gameobjects/Drone.js';
import { WORLD_WIDTH, WORLD_HEIGHT } from '../game.js';

export default class MainScene extends Phaser.Scene {
    constructor() {
        super('MainScene');
        this.selectedDrone = null;
        this.drones = [];
        this.isDragging = false;
    }

    preload() {
        this.load.image('mar', 'assets/mar.png');
    }

    create() {
        // Tile the background to cover the whole world
        const bg = this.add.tileSprite(0, 0, WORLD_WIDTH, WORLD_HEIGHT, 'mar');
        bg.setOrigin(0, 0);

        // Draw hex grid over the full world
        this.hexGrid = new HexGrid(this, 35, WORLD_WIDTH, WORLD_HEIGHT);
        this.hexGrid.draw();

        // Create drones
        this.spawnDrones(5);

        // Camera bounds and initial position
        this.cameras.main.setBounds(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        this.cameras.main.centerOn(WORLD_WIDTH / 2, WORLD_HEIGHT / 2);

        // --- Drag to pan camera ---
        this.input.on('pointerdown', (pointer) => {
            this.isDragging = false;
            this.dragStartX = pointer.x;
            this.dragStartY = pointer.y;
        });

        this.input.on('pointermove', (pointer) => {
            if (!pointer.isDown) return;

            const dx = pointer.x - this.dragStartX;
            const dy = pointer.y - this.dragStartY;

            // Only count as drag if moved more than 5px
            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                this.isDragging = true;
            }

            if (this.isDragging) {
                this.cameras.main.scrollX -= dx;
                this.cameras.main.scrollY -= dy;
                this.dragStartX = pointer.x;
                this.dragStartY = pointer.y;
            }
        });

        // Click to move selected drone (only if not dragging)
        this.input.on('pointerup', (pointer) => {
            if (!this.isDragging && this.selectedDrone) {
                const wx = pointer.worldX;
                const wy = pointer.worldY;
                const nearest = this.hexGrid.getNearestCenter(wx, wy);
                this.selectedDrone.moveTo(nearest.x, nearest.y);
            }
        });

        // Lanzar la escena del HUD en paralelo
        this.scene.launch('HudScene');
    }

    /**
     * Crea N drones en posiciones aleatorias del mundo,
     * ajustados al hexágono más cercano.
     */
    spawnDrones(count) {
        for (let i = 0; i < count; i++) {
            const rx = Phaser.Math.Between(100, WORLD_WIDTH - 100);
            const ry = Phaser.Math.Between(100, WORLD_HEIGHT - 100);
            const hex = this.hexGrid.getNearestCenter(rx, ry);
            const drone = new Drone(this, hex.x, hex.y);
            this.drones.push(drone);
        }
    }

    selectDrone(drone) {
        // Deseleccionar el dron actual si hay uno
        if (this.selectedDrone) {
            this.selectedDrone.deselect();
        }

        // Toggle: si clickeas el mismo dron, lo deselecciona
        if (this.selectedDrone === drone) {
            this.selectedDrone = null;
            return;
        }

        this.selectedDrone = drone;
        drone.select();
    }

    update() {}
}
