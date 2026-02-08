import HexGrid from '../utils/HexGrid.js';
import Drone from '../gameobjects/Drone.js';
import { WORLD_WIDTH, WORLD_HEIGHT, network } from '../game.js';

const TEAM_COLORS = [0x00ff00, 0xff4444]; // green = player 0, red = player 1

export default class MainScene extends Phaser.Scene {
    constructor() {
        super('MainScene');
        this.selectedDrone = null;
        /** { playerIndex: [Drone, Drone, Drone] } */
        this.drones = {};
        /** Shortcut to the local player's drones */
        this.myDrones = [];
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

        // Camera bounds and initial position
        this.cameras.main.setBounds(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        this.cameras.main.centerOn(WORLD_WIDTH / 2, WORLD_HEIGHT / 2);

        // Status text (shown until game starts)
        this.statusText = this.add.text(400, 300, 'Conectando...', {
            fontSize: '24px', fill: '#ffffff'
        }).setOrigin(0.5).setScrollFactor(0).setDepth(1000);

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
            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) this.isDragging = true;
            if (this.isDragging) {
                this.cameras.main.scrollX -= dx;
                this.cameras.main.scrollY -= dy;
                this.dragStartX = pointer.x;
                this.dragStartY = pointer.y;
            }
        });

        // Click to move selected drone — sends request to server
        this.input.on('pointerup', (pointer) => {
            if (!this.isDragging && this.selectedDrone) {
                const nearest = this.hexGrid.getNearestCenter(pointer.worldX, pointer.worldY);
                const droneIndex = this.myDrones.indexOf(this.selectedDrone);
                if (droneIndex >= 0) {
                    network.requestMove(droneIndex, nearest.x, nearest.y);
                }
            }
        });

        // Wire up network events
        this.setupNetwork();

        // Launch the HUD scene in parallel
        this.scene.launch('HudScene');
    }

    setupNetwork() {
        network.on('welcome', () => {
            this.statusText.setText('Esperando al oponente...');
        });

        network.on('gameStart', (msg) => {
            this.statusText.destroy();
            this.statusText = null;
            this.createDronesFromState(msg.state);
        });

        network.on('moveDrone', (msg) => {
            const drone = this.drones[msg.playerIndex]?.[msg.droneIndex];
            if (drone) drone.moveTo(msg.x, msg.y);
        });

        network.on('playerLeft', () => {
            // Show overlay message — game is over
            this.add.text(400, 300, 'Oponente desconectado\nActualiza para jugar de nuevo', {
                fontSize: '22px', fill: '#ff4444', align: 'center'
            }).setOrigin(0.5).setScrollFactor(0).setDepth(1000);
        });

        network.on('error', (msg) => {
            console.warn('[game] server error:', msg.message);
        });

        // Connect → join
        network.connect().then(() => network.join());
    }

    createDronesFromState(state) {
        for (const player of state.players) {
            const isLocal = (player.playerIndex === network.playerIndex);
            const color = TEAM_COLORS[player.playerIndex];
            this.drones[player.playerIndex] = [];

            for (const d of player.drones) {
                // Snap server position to nearest hex centre
                const hex = this.hexGrid.getNearestCenter(d.x, d.y);
                const drone = new Drone(this, hex.x, hex.y, color, isLocal);
                this.drones[player.playerIndex].push(drone);
            }

            if (isLocal) {
                this.myDrones = this.drones[player.playerIndex];
                // Centre camera on our first drone
                const first = this.myDrones[0];
                this.cameras.main.centerOn(first.sprite.x, first.sprite.y);
            }
        }
    }


    selectDrone(drone) {
        // Only allow selecting own drones
        if (!this.myDrones.includes(drone)) return;

        if (this.selectedDrone) this.selectedDrone.deselect();

        // Toggle off if same drone clicked
        if (this.selectedDrone === drone) {
            this.selectedDrone = null;
            return;
        }

        this.selectedDrone = drone;
        drone.select();
    }

    /** Returns a flat list of { drone, playerIndex } for the minimap. */
    getAllDrones() {
        const all = [];
        for (const pi in this.drones) {
            for (const drone of this.drones[pi]) {
                all.push({ drone, playerIndex: parseInt(pi) });
            }
        }
        return all;
    }

    update() {}
}
