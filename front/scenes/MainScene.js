import HexGrid from '../utils/HexGrid.js';
import Drone from '../gameobjects/Drone.js';
import Network from '../network/NetworkManager.js'
import { WORLD_WIDTH, WORLD_HEIGHT } from '../shared/constants.js';

const TEAM_COLORS = [0x00ff00, 0xff4444]; // green = player 0, red = player 1
const MAX_MOVE_DISTANCE = 6; // hexes per turn

// Action modes
const MODE_MOVE = 'move';
const MODE_ATTACK = 'attack';

export default class MainScene extends Phaser.Scene {
    constructor() {
        super('MainScene');
        this.selectedDrone = null;
        /** { playerIndex: [Drone, Drone, Drone] } */
        this.drones = {};
        /** Shortcut to the local player's drones */
        this.myDrones = [];
        this.isDragging = false;

        // Turn state
        this.isMyTurn = false;
        this.actionMode = MODE_MOVE;
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

        // Graphics for hex hover highlight
        this.hexHighlight = this.add.graphics();
        this.lastHighlightedHex = null;

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
            // Handle hex highlight for movement
            this.updateHexHighlight(pointer);

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

        // Click on empty space to move (if in move mode)
        this.input.on('pointerup', (pointer) => {
            if (this.isDragging) return;
            if (!this.isMyTurn) return;
            if (!this.selectedDrone) return;
            if (this.actionMode !== MODE_MOVE) return;
            if (this.selectedDrone.hasMoved) return;

            const nearest = this.hexGrid.getNearestCenter(pointer.worldX, pointer.worldY);
            
            // Check movement distance
            const distance = this.hexGrid.getHexDistance(
                this.selectedDrone.sprite.x, this.selectedDrone.sprite.y,
                nearest.x, nearest.y
            );
            
            // Skip if clicking on same spot (e.g., when selecting a drone)
            if (distance < 1) return;
            
            if (distance > MAX_MOVE_DISTANCE) {
                console.warn(`Move too far: ${distance} hexes (max ${MAX_MOVE_DISTANCE})`);
                return;
            }

            const droneIndex = this.myDrones.indexOf(this.selectedDrone);
            if (droneIndex >= 0) {
                Network.requestMove(droneIndex, nearest.x, nearest.y);
            }
        });

        // Wire up Network events
        this.setupNetwork();

        // Launch the HUD scene in parallel
        this.scene.launch('HudScene');
    }

    setupNetwork() {
        Network.on('welcome', () => {
            this.events.emit('statusChanged', 'Esperando al oponente...');
        });

        Network.on('gameStart', (msg) => {
            this.createDronesFromState(msg.state);
            this.events.emit('gameStarted');
        });

        Network.on('turnStart', (msg) => {
            this.isMyTurn = (msg.activePlayer === Network.playerIndex);
            this.actionMode = MODE_MOVE;
            this.clearTargetHighlights();

            // Reset per-drone action state for new turn
            if (this.isMyTurn) {
                for (const drone of this.myDrones) {
                    drone.hasMoved = false;
                    drone.hasAttacked = false;
                }
            }

            // Notify HUD
            this.events.emit('turnChanged', {
                isMyTurn: this.isMyTurn
            });
        });

        Network.on('moveDrone', (msg) => {
            const drone = this.drones[msg.playerIndex]?.[msg.droneIndex];
            if (drone) {
                drone.moveTo(msg.x, msg.y);
                // Mark drone as moved if it's ours
                if (msg.playerIndex === Network.playerIndex) {
                    drone.hasMoved = true;
                    this.hexHighlight.clear();
                }
            }
        });

        Network.on('attackResult', (msg) => {
            const targetDrone = this.drones[msg.targetPlayer]?.[msg.targetDrone];
            if (targetDrone) {
                targetDrone.takeDamage(msg.damage, msg.remainingHealth);
            }
            // Mark attacker as having attacked
            const attackerDrone = this.drones[msg.attackerPlayer]?.[msg.attackerDrone];
            if (attackerDrone && msg.attackerPlayer === Network.playerIndex) {
                attackerDrone.hasAttacked = true;
            }
            this.clearTargetHighlights();
            this.actionMode = MODE_MOVE;
            this.events.emit('attackModeEnded');
        });

        Network.on('playerLeft', () => {
            this.add.text(400, 300, 'Oponente desconectado\nActualiza para jugar de nuevo', {
                fontSize: '22px', fill: '#ff4444', align: 'center'
            }).setOrigin(0.5).setScrollFactor(0).setDepth(1000);
        });

        Network.on('error', (msg) => {
            console.warn('[game] server error:', msg.message);
        });

        // Connect → join
        Network.connect().then(() => Network.join());
    }

    createDronesFromState(state) {
        for (const player of state.players) {
            const isLocal = (player.playerIndex === Network.playerIndex);
            const color = TEAM_COLORS[player.playerIndex];
            this.drones[player.playerIndex] = [];

            for (const d of player.drones) {
                const hex = this.hexGrid.getNearestCenter(d.x, d.y);
                const drone = new Drone(this, hex.x, hex.y, color, isLocal, {
                    health: d.health,
                    maxHealth: d.maxHealth,
                    attackDamage: d.attackDamage,
                    attackRange: d.attackRange
                });
                drone.playerIndex = player.playerIndex;
                drone.droneIndex = this.drones[player.playerIndex].length;
                this.drones[player.playerIndex].push(drone);
            }

            if (isLocal) {
                this.myDrones = this.drones[player.playerIndex];
                const first = this.myDrones[0];
                this.cameras.main.centerOn(first.sprite.x, first.sprite.y);
            }
        }
    }

    /** Called when any drone is clicked */
    onDroneClicked(drone) {
        // In attack mode, clicking enemy drone = attack
        if (this.actionMode === MODE_ATTACK && !drone.isLocal && drone.isAlive()) {
            this.executeAttack(drone);
            return;
        }

        // Otherwise, select/deselect own drones
        if (drone.isLocal) {
            this.selectDrone(drone);
        }
    }

    selectDrone(drone) {
        if (!this.myDrones.includes(drone)) return;

        if (this.selectedDrone) this.selectedDrone.deselect();

        if (this.selectedDrone === drone) {
            this.selectedDrone = null;
            this.actionMode = MODE_MOVE;
            this.clearTargetHighlights();
            this.hexHighlight.clear();
            return;
        }

        this.selectedDrone = drone;
        drone.select();

        // Reset to move mode when selecting a new drone
        this.actionMode = MODE_MOVE;
        this.clearTargetHighlights();
    }

    /** Switch to attack mode - called from HUD */
    enterAttackMode() {
        if (!this.isMyTurn || !this.selectedDrone) return;
        if (this.selectedDrone.hasAttacked) return; // Already attacked this turn

        this.actionMode = MODE_ATTACK;
        this.hexHighlight.clear();
        this.highlightEnemyTargets();
    }

    /** Cancel attack mode */
    cancelAttackMode() {
        this.actionMode = MODE_MOVE;
        this.clearTargetHighlights();
        this.events.emit('attackModeEnded');
    }

    highlightEnemyTargets() {
        const enemyIndex = Network.playerIndex === 0 ? 1 : 0;
        const enemies = this.drones[enemyIndex] || [];
        for (const drone of enemies) {
            if (drone.isAlive()) {
                drone.setTargetable(true);
            }
        }
    }

    clearTargetHighlights() {
        for (const pi in this.drones) {
            for (const drone of this.drones[pi]) {
                drone.setTargetable(false);
            }
        }
    }

    executeAttack(targetDrone) {
        if (!this.selectedDrone || !this.isMyTurn) return;
        if (this.selectedDrone.hasAttacked) return; // Already attacked this turn

        const attackerIndex = this.myDrones.indexOf(this.selectedDrone);
        if (attackerIndex < 0) return;

        Network.requestAttack(attackerIndex, targetDrone.playerIndex, targetDrone.droneIndex);
    }

    /** End turn early - called from HUD */
    endTurn() {
        if (!this.isMyTurn) return;
        Network.endTurn();
    }

    /** Returns a flat list of { drone, playerIndex } for the minimap. */
    getAllDrones() {
        const all = [];
        for (const pi in this.drones) {
            for (const drone of this.drones[pi]) {
                if (drone.isAlive()) {
                    all.push({ drone, playerIndex: parseInt(pi) });
                }
            }
        }
        return all;
    }

    /** Update the hex highlight based on pointer position */
    updateHexHighlight(pointer) {
        this.hexHighlight.clear();

        // Only show highlight when we have a selected drone, it's our turn, and we're in move mode
        if (!this.selectedDrone || !this.isMyTurn || this.actionMode !== MODE_MOVE) {
            this.lastHighlightedHex = null;
            return;
        }

        // Don't show if drone already moved
        if (this.selectedDrone.hasMoved) {
            this.lastHighlightedHex = null;
            return;
        }

        const nearest = this.hexGrid.getNearestCenter(pointer.worldX, pointer.worldY);
        
        // Calculate distance from selected drone
        const distance = this.hexGrid.getHexDistance(
            this.selectedDrone.sprite.x, this.selectedDrone.sprite.y,
            nearest.x, nearest.y
        );

        // Skip if on the same hex as the drone
        if (distance < 1) {
            this.lastHighlightedHex = null;
            return;
        }

        // Choose color based on distance
        const color = distance <= MAX_MOVE_DISTANCE ? 0x00ff00 : 0xffff00; // green if in range, yellow if too far
        const alpha = 0.3;

        this.hexGrid.drawFilledHex(this.hexHighlight, nearest.x, nearest.y, color, alpha);
        this.lastHighlightedHex = nearest;
    }

    update() {}
}
