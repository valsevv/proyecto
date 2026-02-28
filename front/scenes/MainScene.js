import HexGrid from '../utils/HexGrid.js';
import Drone from '../gameobjects/Drone.js';
import Network from '../network/NetworkManager.js'
import { WORLD_WIDTH, WORLD_HEIGHT } from '../shared/constants.js';

const TEAM_COLORS = [0x00ff00, 0xff4444]; // green = player 0, red = player 1
const MAX_MOVE_DISTANCE = 6; // hexes per turn
const MAX_CARRIER_MOVE_DISTANCE = 3; // hexes per turn (parametrizable)
const MAX_MANUAL_ATTACK_DISTANCE = 10; // max range in hexes for manual missile aiming (parametrizable)
const CARRIER_POSITIONS = {
    0: { x: 300, y: 900 },
    1: { x: 2100, y: 900 }
};

// Action modes
const MODE_MOVE = 'move';
const MODE_ATTACK = 'attack';

export default class MainScene extends Phaser.Scene {
    constructor() {
        super('MainScene');
        this.selectedDrone = null;
        this.selectedCarrier = null;
        /** { playerIndex: [Drone, Drone, Drone] } */
        this.drones = {};
        /** Shortcut to the local player's drones */
        this.myDrones = [];
        /** { playerIndex: { sprite, ring, playerIndex, isLocal, maxMoveDistance } } */
        this.carriers = {};
        this.isDragging = false;

        // Turn state
        this.isMyTurn = false;
        this.actionMode = MODE_MOVE;
        this.actionsPerTurn = 10;
    }


    preload() {
        this.load.image('mar', 'assets/mar.png');
        this.load.image('dron_misil', 'assets/dron_misil.png');
        this.load.image('dron_bomba', 'assets/dron_bomba.png');
        this.load.image('porta_drones_volador', 'assets/porta_drones_volador.png');
        this.load.image('porta_drones_mar', 'assets/porta_drones_mar.png');
    }


    create(data) {
        console.log('[MainScene] === CREATE CALLED ===');
        console.log('[MainScene] Scene data received:', data);
        console.log('[MainScene] Has gameState?:', !!data?.gameState);
        
        // Tile the background to cover the whole world
        const bg = this.add.tileSprite(0, 0, WORLD_WIDTH, WORLD_HEIGHT, 'mar');
        bg.setOrigin(0, 0);

        // Draw hex grid over the full world
        this.hexGrid = new HexGrid(this, 35, WORLD_WIDTH, WORLD_HEIGHT);
        this.hexGrid.draw();

        // Graphics for hex hover highlight
        this.hexHighlight = this.add.graphics();
        this.missileGuideGraphics = this.add.graphics();
        this.manualTargetHex = null;
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

            if (this.actionMode === MODE_ATTACK && this.selectedDrone && !pointer.isDown && this.isMyTurn) {
                const nearest = this.hexGrid.getNearestCenter(pointer.worldX, pointer.worldY);
                this.setManualAttackLine(nearest);
            }

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

            const nearest = this.hexGrid.getNearestCenter(pointer.worldX, pointer.worldY);

            if (this.actionMode === MODE_ATTACK && this.selectedDrone) {
                this.setManualAttackLine(nearest);
                this.executeManualAttack();
                return;
            }

            if (this.actionMode !== MODE_MOVE) return;
            if (!this.selectedDrone && !this.selectedCarrier) return;

            if (this.selectedCarrier) {
                const distance = this.hexGrid.getHexDistance(
                    this.selectedCarrier.sprite.x,
                    this.selectedCarrier.sprite.y,
                    nearest.x,
                    nearest.y
                );

                if (distance < 1) return;

                if (distance > this.selectedCarrier.maxMoveDistance) {
                    console.warn(`Carrier move too far: ${distance} hexes (max ${this.selectedCarrier.maxMoveDistance})`);
                    return;
                }

                this.moveCarrier(this.selectedCarrier, nearest.x, nearest.y);
                return;
            }

            const distance = this.hexGrid.getHexDistance(
                this.selectedDrone.sprite.x, this.selectedDrone.sprite.y,
                nearest.x, nearest.y
            );

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
        
        // Launch the HUD scene FIRST so it can receive events
        console.log('[MainScene] === LAUNCHING HUD SCENE ===');
        this.scene.launch('HudScene');
        console.log('[MainScene] === HUD SCENE LAUNCHED (booting...) ===');
        
        // Small delay to ensure HudScene finishes its create() method
        // and sets up event listeners before we emit events
        this.time.delayedCall(50, () => {
            this.initializeGameState(data);
        });
    }
    
    initializeGameState(data) {
        console.log('[MainScene] === INITIALIZING GAME STATE ===');
        
        // If we have initial game state from LobbyScene, create drones immediately
        console.log('[MainScene] === CHECKING FOR INITIAL GAME STATE ===');
        console.log('[MainScene] data:', data);
        console.log('[MainScene] data.gameState:', data?.gameState);
        
        if (data && data.gameState) {
            console.log('[MainScene] === CREATING DRONES FROM INITIAL STATE ===');
            console.log('[MainScene] Game state:', data.gameState);
            this.createDronesFromState(data.gameState);
            
            console.log('[MainScene] === EMITTING gameStarted EVENT ===');
            this.events.emit('gameStarted');
            console.log('[MainScene] === DRONES CREATED AND gameStarted EMITTED ===');
            
            // CRITICAL: Manually trigger turnStart since the server's turnStart message
            // arrives BEFORE this scene is ready to receive it
            console.log('[MainScene] === MANUALLY TRIGGERING INITIAL TURN ===');
            const initialTurn = data.gameState.currentTurn;
            const initialActions = data.gameState.actionsRemaining;
            console.log('[MainScene] Initial turn player:', initialTurn);
            console.log('[MainScene] Initial actions:', initialActions);
            
            this.isMyTurn = (initialTurn === Network.playerIndex);
            this.actionMode = MODE_MOVE;
            this.clearTargetHighlights();
            
            // Reset per-drone attack state for new turn
            if (this.isMyTurn) {
                for (const drone of this.myDrones) {
                    drone.hasAttacked = false;
                }
            }

            this.actionsPerTurn = data.gameState.actionsPerTurn ?? 10;
            this.events.emit('actionsUpdated', {
                actionsRemaining: initialActions,
                actionsPerTurn: this.actionsPerTurn
            });

            // Notify HUD
            console.log('[MainScene] === EMITTING turnChanged EVENT ===');
            this.events.emit('turnChanged', {
                isMyTurn: this.isMyTurn
            });
            console.log('[MainScene] === INITIAL TURN STATE SET - isMyTurn:', this.isMyTurn, '===');
        } else {
            console.error('[MainScene] === NO INITIAL GAME STATE PROVIDED! ===');
            console.error('[MainScene] This is a bug - game should not start without state');
        }

        console.log('[MainScene] === MAINSCENE CREATE COMPLETE ===');
    }

    setupNetwork() {
        // Remove auto-connect logic - that's handled by LobbyScene
        // Note: Don't register 'gameStart' handler here as it conflicts with LobbyScene's handler
        // The initial game state is received via scene data from LobbyScene
        
        Network.on('turnStart', (msg) => {
            this.isMyTurn = (msg.activePlayer === Network.playerIndex);
            this.actionMode = MODE_MOVE;
            this.clearTargetHighlights();
            this.clearSelections();

            // Reset per-drone attack state for new turn
            if (this.isMyTurn) {
                for (const drone of this.myDrones) {
                    drone.hasAttacked = false;
                }
            }

            this.actionsPerTurn = msg.actionsPerTurn ?? this.actionsPerTurn;
            this.events.emit('actionsUpdated', {
                actionsRemaining: msg.actionsRemaining,
                actionsPerTurn: this.actionsPerTurn
            });

            // Notify HUD
            this.events.emit('turnChanged', {
                isMyTurn: this.isMyTurn
            });
        });

        Network.on('moveDrone', (msg) => {
            const drone = this.drones[msg.playerIndex]?.[msg.droneIndex];
            if (!drone) {
                return;
            }

            if (typeof msg.x === 'number' && typeof msg.y === 'number') {
                drone.moveTo(msg.x, msg.y);
            }

            if (typeof msg.remainingFuel === 'number') {
                drone.setFuel(msg.remainingFuel);
            }

            const destroyedByFuel = msg.destroyedByFuel || msg.remainingFuel === 0;
            if (destroyedByFuel && drone.isAlive()) {
                if (this.selectedDrone === drone) {
                    this.selectedDrone.deselect();
                    this.selectedDrone.sprite.clearTint();
                    this.selectedDrone = null;
                    this.clearTargetHighlights();
                    this.actionMode = MODE_MOVE;
                    this.events.emit('selectionChanged', { type: null });
                }
                drone.sinkAndDestroy();
            }

            if (msg.playerIndex === Network.playerIndex && typeof msg.x === 'number' && typeof msg.y === 'number') {
                this.hexHighlight.clear();
                const nextActions = Math.max(0, (Network.actionsRemaining ?? 0) - 1);
                Network.actionsRemaining = nextActions;
                this.events.emit('actionsUpdated', {
                    actionsRemaining: nextActions,
                    actionsPerTurn: this.actionsPerTurn
                });
            }

            this.events.emit('fuelUpdated');
        });

        Network.on('attackResult', (msg) => {
            const targetDrone = this.drones[msg.targetPlayer]?.[msg.targetDrone];
            const hit = msg.hit !== false;
            const attackerDrone = this.drones[msg.attackerPlayer]?.[msg.attackerDrone];

            if (attackerDrone && targetDrone) {
                this.playMissileShot(attackerDrone, targetDrone, hit, msg.lineX, msg.lineY);
            }

            if (targetDrone && hit) {
                targetDrone.takeDamage(msg.damage, msg.remainingHealth);
            }
            // Mark attacker as having attacked
            if (attackerDrone && msg.attackerPlayer === Network.playerIndex) {
                attackerDrone.hasAttacked = true;
                if (attackerDrone.droneType === 'Aereo') {
                    attackerDrone.consumeMissile();
                }
                const nextActions = Math.max(0, (Network.actionsRemaining ?? 0) - 1);
                Network.actionsRemaining = nextActions;
                this.events.emit('actionsUpdated', {
                    actionsRemaining: nextActions,
                    actionsPerTurn: this.actionsPerTurn
                });
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

        // Handle game saved - redirect to menu
        Network.on('gameSaved', (msg) => {
            console.log('[MainScene] === GAME SAVED ===');
            console.log('[MainScene] Game ID:', msg.gameId);
            alert('Partida guardada correctamente');
            window.location.href = '/menu';
        });
        
        // Note: Connection and join are handled by LobbyScene
    }

    createCarrierForPlayer(playerIndex, side) {
        const basePosition = CARRIER_POSITIONS[playerIndex] || CARRIER_POSITIONS[0];
        const spriteKey = side === 'Naval' ? 'porta_drones_mar' : 'porta_drones_volador';

        if (this.carriers[playerIndex]) {
            this.carriers[playerIndex].ring.destroy();
            this.carriers[playerIndex].sprite.destroy();
        }

        const isLocal = playerIndex === Network.playerIndex;
        const sprite = this.add.image(basePosition.x, basePosition.y, spriteKey);
        sprite.setScale(0.45);
        sprite.setDepth(4);

        const ring = this.add.circle(basePosition.x, basePosition.y, 48);
        ring.setDepth(5);
        ring.setStrokeStyle(3, 0xfff176);
        ring.setFillStyle();
        ring.setVisible(false);

        const carrier = {
            sprite,
            ring,
            playerIndex,
            isLocal,
            maxMoveDistance: MAX_CARRIER_MOVE_DISTANCE,
            type: 'carrier'
        };

        if (isLocal) {
            sprite.setInteractive({ useHandCursor: true, pixelPerfect: true, alphaTolerance: 1 });
            sprite.on('pointerdown', (pointer) => {
                pointer.event.stopPropagation();
                this.onCarrierClicked(carrier);
            });
        }

        this.carriers[playerIndex] = carrier;
    }

    createDronesFromState(state) {
        console.log('[MainScene] === createDronesFromState CALLED ===');
        console.log('[MainScene] State:', state);
        console.log('[MainScene] Players in state:', state?.players);
        console.log('[MainScene] Current turn:', state?.currentTurn);
        console.log('[MainScene] My player index:', Network.playerIndex);
        
        for (const player of state.players) {
            console.log('[MainScene] Processing player', player.playerIndex);
            console.log('[MainScene] Player side:', player.side);
            console.log('[MainScene] Player drones:', player.drones?.length);
            
            const isLocal = (player.playerIndex === Network.playerIndex);
            const color = TEAM_COLORS[player.playerIndex];
            this.drones[player.playerIndex] = [];
            this.createCarrierForPlayer(player.playerIndex, player.side || 'Aereo');

            for (const d of player.drones) {
                const hex = this.hexGrid.getNearestCenter(d.x, d.y);
                const droneType = d.droneType || 'Aereo'; // Default to Aereo if not specified
                console.log('[MainScene] Creating drone at', hex.x, hex.y, 'type:', droneType);
                
                const drone = new Drone(this, hex.x, hex.y, color, isLocal, {
                    health: d.health,
                    maxHealth: d.maxHealth,
                    attackDamage: d.attackDamage,
                    attackRange: d.attackRange,
                    droneType: droneType,
                    missiles: d.missiles,
                    fuel: d.fuel,
                    maxFuel: d.maxFuel
                });
                drone.playerIndex = player.playerIndex;
                drone.droneIndex = this.drones[player.playerIndex].length;
                this.drones[player.playerIndex].push(drone);
            }

            if (isLocal) {
                console.log('[MainScene] These are MY drones');
                this.myDrones = this.drones[player.playerIndex];
                const first = this.myDrones[0];
                this.cameras.main.centerOn(first.sprite.x, first.sprite.y);
                console.log('[MainScene] Camera centered on my first drone');
            }
        }
        
        console.log('[MainScene] === DRONE CREATION COMPLETE ===');
        console.log('[MainScene] Total players:', Object.keys(this.drones).length);
        console.log('[MainScene] My drones:', this.myDrones?.length);
    }


    onCarrierClicked(carrier) {
        if (!carrier?.isLocal) return;
        this.selectCarrier(carrier);
    }

    selectCarrier(carrier) {
        if (!carrier?.isLocal) return;

        if (this.selectedDrone) {
            this.selectedDrone.deselect();
            this.selectedDrone.sprite.clearTint();
            this.selectedDrone = null;
        }
        if (this.selectedCarrier && this.selectedCarrier !== carrier) {
            this.selectedCarrier.ring.setVisible(false);
            this.selectedCarrier.sprite.clearTint();
            this.stopCarrierPulse(this.selectedCarrier);
        }

        if (this.selectedCarrier === carrier) {
            this.selectedCarrier.ring.setVisible(false);
            this.selectedCarrier.sprite.clearTint();
            this.stopCarrierPulse(this.selectedCarrier);
            this.selectedCarrier = null;
            this.events.emit('selectionChanged', { type: null });
            return;
        }

        this.selectedCarrier = carrier;
        carrier.ring.setVisible(true);
        carrier.ring.setPosition(carrier.sprite.x, carrier.sprite.y);
        carrier.sprite.setTint(0xfff176);
        this.startCarrierPulse(carrier);
        this.actionMode = MODE_MOVE;
        this.clearTargetHighlights();
        this.events.emit('selectionChanged', {
            type: 'carrier',
            playerIndex: carrier.playerIndex,
            maxMoveDistance: carrier.maxMoveDistance
        });
    }

    moveCarrier(carrier, x, y) {
        this.tweens.add({
            targets: [carrier.sprite, carrier.ring],
            x,
            y,
            duration: 650,
            ease: 'Power2'
        });
    }

    startCarrierPulse(carrier) {
        this.stopCarrierPulse(carrier);
        carrier.selectionTween = this.tweens.add({
            targets: carrier.ring,
            alpha: { from: 0.35, to: 1 },
            duration: 600,
            yoyo: true,
            repeat: -1,
            ease: 'Sine.easeInOut'
        });
    }

    stopCarrierPulse(carrier) {
        if (carrier?.selectionTween) {
            carrier.selectionTween.stop();
            carrier.selectionTween = null;
        }
        if (carrier?.ring) {
            carrier.ring.setAlpha(1);
        }
    }

    /** Called when any drone is clicked */
    onDroneClicked(drone) {
        if (this.actionMode === MODE_ATTACK) {
            // Allow direct enemy click in attack mode as a valid manual shot direction.
            if (!drone.isLocal && drone.isAlive() && this.selectedDrone && this.isMyTurn) {
                this.setManualAttackLine({ x: drone.sprite.x, y: drone.sprite.y });
                this.executeAttack(drone);
            }
            return;
        }

        // Otherwise, select/deselect own drones
        if (drone.isLocal) {
            this.selectDrone(drone);
        }
    }

    selectDrone(drone) {
        if (!this.myDrones.includes(drone)) return;

        if (this.selectedDrone) {
            this.selectedDrone.deselect();
            this.selectedDrone.sprite.clearTint();
        }
        if (this.selectedCarrier) {
            this.selectedCarrier.ring.setVisible(false);
            this.selectedCarrier.sprite.clearTint();
            this.stopCarrierPulse(this.selectedCarrier);
            this.selectedCarrier = null;
        }

        if (this.selectedDrone === drone) {
            this.selectedDrone = null;
            this.actionMode = MODE_MOVE;
            this.clearTargetHighlights();
            this.hexHighlight.clear();
            this.events.emit('selectionChanged', { type: null });
            return;
        }

        this.selectedDrone = drone;
        drone.select();
        drone.sprite.setTint(0xfff176);

        const droneNumber = this.myDrones.indexOf(drone) + 1;
        this.events.emit('selectionChanged', {
            type: 'drone',
            droneNumber,
            maxMoveDistance: MAX_MOVE_DISTANCE
        });

        // Reset to move mode when selecting a new drone
        this.actionMode = MODE_MOVE;
        this.clearTargetHighlights();
    }

    /** Switch to attack mode - called from HUD */
    enterAttackMode() {
        if (!this.isMyTurn || !this.selectedDrone) return;
        if (this.selectedDrone.hasAttacked) return; // Already attacked this turn
        if (this.selectedDrone.droneType === 'Aereo' && !this.selectedDrone.canUseMissileAttack()) return;

        this.actionMode = MODE_ATTACK;
        this.hexHighlight.clear();
        this.manualTargetHex = null;

        const pointer = this.input.activePointer;
        if (pointer) {
            const nearest = this.hexGrid.getNearestCenter(pointer.worldX, pointer.worldY);
            this.setManualAttackLine(nearest);
        }

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
        this.drawMissileGuides();
    }

    clearTargetHighlights() {
        for (const pi in this.drones) {
            for (const drone of this.drones[pi]) {
                drone.setTargetable(false);
            }
        }
        this.manualTargetHex = null;
        this.missileGuideGraphics.clear();
    }

    executeAttack(targetDrone) {
        if (!this.selectedDrone || !this.isMyTurn) return;
        if (this.selectedDrone.hasAttacked) return; // Already attacked this turn
        if (this.selectedDrone.droneType === 'Aereo' && !this.selectedDrone.canUseMissileAttack()) return;

        const attackerIndex = this.myDrones.indexOf(this.selectedDrone);
        if (attackerIndex < 0) return;

        const lineTarget = this.manualTargetHex || targetDrone?.sprite;
        if (!lineTarget) return;

        Network.requestAttack(
            attackerIndex,
            targetDrone.playerIndex,
            targetDrone.droneIndex,
            lineTarget.x,
            lineTarget.y
        );
    }

    executeManualAttack() {
        if (!this.manualTargetHex) return;

        const targetDrone = this.findBestTargetForManualLine();
        if (!targetDrone) return;

        this.executeAttack(targetDrone);
    }

    setManualAttackLine(hexCenter) {
        if (!this.selectedDrone || !hexCenter) return;
        const startX = this.selectedDrone.sprite.x;
        const startY = this.selectedDrone.sprite.y;

        const dx = hexCenter.x - startX;
        const dy = hexCenter.y - startY;
        const magnitude = Math.sqrt(dx * dx + dy * dy);
        if (magnitude === 0) return;

        const maxDistancePixels = this.hexGrid.hexSize * Math.sqrt(3) * MAX_MANUAL_ATTACK_DISTANCE;
        const clampedDistance = Math.min(magnitude, maxDistancePixels);
        const factor = clampedDistance / magnitude;

        this.manualTargetHex = {
            x: startX + (dx * factor),
            y: startY + (dy * factor)
        };

        this.drawMissileGuides();
    }

    findBestTargetForManualLine() {
        const enemyIndex = Network.playerIndex === 0 ? 1 : 0;
        const enemies = (this.drones[enemyIndex] || []).filter((drone) => drone.isAlive());
        if (!enemies.length || !this.manualTargetHex || !this.selectedDrone) return null;

        let closestEnemy = null;
        let closestDistance = Number.POSITIVE_INFINITY;

        for (const enemy of enemies) {
            const dx = this.manualTargetHex.x - enemy.sprite.x;
            const dy = this.manualTargetHex.y - enemy.sprite.y;
            const distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < closestDistance) {
                closestDistance = distance;
                closestEnemy = enemy;
            }
        }

        return closestEnemy;
    }

    drawMissileGuides() {
        this.missileGuideGraphics.clear();
        if (!this.selectedDrone || this.actionMode !== MODE_ATTACK) return;

        const startX = this.selectedDrone.sprite.x;
        const startY = this.selectedDrone.sprite.y;

        if (this.manualTargetHex) {
            this.missileGuideGraphics.lineStyle(3, 0x29b6f6, 0.95);
            this.missileGuideGraphics.beginPath();
            this.missileGuideGraphics.moveTo(startX, startY);
            this.missileGuideGraphics.lineTo(this.manualTargetHex.x, this.manualTargetHex.y);
            this.missileGuideGraphics.strokePath();

            this.hexGrid.drawFilledHex(this.missileGuideGraphics, this.manualTargetHex.x, this.manualTargetHex.y, 0x29b6f6, 0.28);
        }
    }

    playMissileShot(attackerDrone, targetDrone, hit, lineX, lineY) {
        const missile = this.add.circle(attackerDrone.sprite.x, attackerDrone.sprite.y, 4, 0xff9800, 1);
        missile.setDepth(30);

        const trail = this.add.graphics();
        trail.setDepth(29);
        trail.lineStyle(2, 0xffb74d, 0.7);

        const endX = typeof lineX === 'number' ? lineX : targetDrone.sprite.x;
        const endY = typeof lineY === 'number' ? lineY : targetDrone.sprite.y;

        this.tweens.add({
            targets: missile,
            x: endX,
            y: endY,
            duration: 320,
            ease: 'Quad.easeIn',
            onUpdate: () => {
                trail.clear();
                trail.lineStyle(2, 0xffb74d, 0.7);
                trail.beginPath();
                trail.moveTo(attackerDrone.sprite.x, attackerDrone.sprite.y);
                trail.lineTo(missile.x, missile.y);
                trail.strokePath();
            },
            onComplete: () => {
                trail.clear();
                trail.destroy();
                missile.destroy();

                const impact = this.add.circle(endX, endY, hit ? 12 : 8, hit ? 0xff5722 : 0x9e9e9e, 0.55);
                impact.setDepth(31);
                this.tweens.add({
                    targets: impact,
                    alpha: 0,
                    scale: 1.6,
                    duration: 180,
                    onComplete: () => impact.destroy()
                });
            }
        });
    }

    clearSelections() {
        if (this.selectedDrone) {
            this.selectedDrone.deselect();
            this.selectedDrone.sprite.clearTint();
            this.selectedDrone = null;
        }

        if (this.selectedCarrier) {
            this.selectedCarrier.ring.setVisible(false);
            this.selectedCarrier.sprite.clearTint();
            this.stopCarrierPulse(this.selectedCarrier);
            this.selectedCarrier = null;
        }

        this.events.emit('selectionChanged', { type: null });
    }

    /** End turn early - called from HUD */
    endTurn() {
        if (!this.isMyTurn) return;

        this.isMyTurn = false;
        this.actionMode = MODE_MOVE;
        this.clearTargetHighlights();
        this.clearSelections();
        this.events.emit('turnChanged', { isMyTurn: false });

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

        // Only show highlight when we have a selected unit, it's our turn, and we're in move mode
        if ((!this.selectedDrone && !this.selectedCarrier) || !this.isMyTurn || this.actionMode !== MODE_MOVE) {
            this.lastHighlightedHex = null;
            return;
        }

        const nearest = this.hexGrid.getNearestCenter(pointer.worldX, pointer.worldY);
        const selectedUnitSprite = this.selectedCarrier ? this.selectedCarrier.sprite : this.selectedDrone.sprite;
        const maxDistance = this.selectedCarrier ? this.selectedCarrier.maxMoveDistance : MAX_MOVE_DISTANCE;

        // Calculate distance from selected unit
        const distance = this.hexGrid.getHexDistance(
            selectedUnitSprite.x, selectedUnitSprite.y,
            nearest.x, nearest.y
        );

        // Skip if on the same hex as the drone
        if (distance < 1) {
            this.lastHighlightedHex = null;
            return;
        }

        // Choose color based on distance
        const color = distance <= maxDistance ? 0x00ff00 : 0xffff00; // green if in range, yellow if too far
        const alpha = 0.3;

        this.hexGrid.drawFilledHex(this.hexHighlight, nearest.x, nearest.y, color, alpha);
        this.lastHighlightedHex = nearest;
    }

    update() {}
}
