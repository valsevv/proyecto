import HexGrid from '../utils/HexGrid.js';
import Drone from '../gameobjects/Drone.js';
import Network from '../network/NetworkManager.js'
import { attachNetworkHandlers } from './mainSceneNetwork.js';
import {
    getVisionRangeForSide,
    getCarrierVisionRangeForSide,
    isDroneVisibleToLocal,
    isCarrierVisibleToLocal,
    updateFogOfWar,
    updateVision
} from './mainSceneVision.js';
import { WORLD_WIDTH, WORLD_HEIGHT } from '../shared/constants.js';
import { getGameConfig } from '../shared/gameConfig.js';

const TEAM_COLORS = [0x00ff00, 0xff4444]; // green = player 0, red = player 1
const DEFAULT_MAX_MOVE_DISTANCE = 2;
const CARRIER_EDGE_MARGIN = 320;
const CARRIER_SPAWN_Y_MARGIN = 240;
const INITIAL_CAMERA_ZOOM = 0.8;
const MIN_CAMERA_ZOOM = 0.6;
const MAX_CAMERA_ZOOM = 1.3;
const CAMERA_ZOOM_STEP = 0.0005;
const AERIAL_CARRIER_FLOAT_DISTANCE_PX = 8;
const AERIAL_CARRIER_FLOAT_DURATION_MS = 1400;
const NAVAL_CARRIER_BOB_DISTANCE_PX = 2;
const NAVAL_CARRIER_BOB_DURATION_MS = 1800;
const NAVAL_CARRIER_ROCK_ANGLE_DEG = 1.2;
const NAVAL_CARRIER_ROCK_DURATION_MS = 2100;

// Action modes
const MODE_MOVE = 'move';
const MODE_ATTACK = 'attack';
const NAVAL_CARRIER_SPRITES = [
    'porta_drones_mar_0',
    'porta_drones_mar_1',
    'porta_drones_mar_2',
    'porta_drones_mar_3',
    'porta_drones_mar_4'
];



const AEREAL_CARRIER_SPRITES = [
    'porta_drones_aereo_0',
    'porta_drones_aereo_1',
    'porta_drones_aereo_2',
    'porta_drones_aereo_3',
    'porta_drones_aereo_4'
];

export default class MainScene extends Phaser.Scene {
    constructor() {
        super('MainScene');
        this.selectedDrone = null;
        this.selectedCarrier = null;
        this.gameFinished = false;
        /** { playerIndex: [Drone, Drone, Drone] } */
        this.drones = {};
        /** Shortcut to the local player's drones */
        this.myDrones = [];
        /** { playerIndex: { sprite, ring, playerIndex, isLocal, maxMoveDistance } } */
        this.carriers = {};
        this.isDragging = false;

        /** { playerIndex: 'Naval' | 'Aereo' } */
        this.playerSides = {};
        this.localSide = 'Aereo';
        const runtimeConfig = getGameConfig();

        // Carrier for which the deploy panel is currently open
        this.deployPanelCarrier = null;

        // Vision ranges (in hexes). Defaults are overridden by server gameState when available.
        this.navalVisionRange = runtimeConfig.navalVisionRange;
        this.aereoVisionRange = runtimeConfig.aerialVisionRange;
        this.navalCarrierVisionRange = runtimeConfig.navalCarrierVisionRange ?? this.navalVisionRange;
        this.aereoCarrierVisionRange = runtimeConfig.aerialCarrierVisionRange ?? this.aereoVisionRange;
        this.manualAttackMaxDistance = runtimeConfig.missileMaxDistance;

        // Turn state
        this.isMyTurn = false;
        this.actionMode = MODE_MOVE;
        this.actionsPerTurn = runtimeConfig.actionsPerTurn;

        this.defaultAerialDroneMovementRange = runtimeConfig.aerialDroneMovementRange;
        this.defaultNavalDroneMovementRange = runtimeConfig.navalDroneMovementRange;
        this.defaultAerialCarrierMovementRange = runtimeConfig.aerialCarrierMovementRange;
        this.defaultNavalCarrierMovementRange = runtimeConfig.navalCarrierMovementRange;

        // Turn timer (seconds). HUD listens to `turnTimerUpdated` events.
        this.turnDurationSeconds = Math.max(1, runtimeConfig.turnDurationSeconds ?? 40);
        this.turnSecondsRemaining = this.turnDurationSeconds;
        this.turnTimerEvent = null;
    }


    preload() {
        this.load.image('mar', 'assets/mar.png');
        this.load.image('vista_lateral_fondo', 'assets/vista_lateral_fondo.png');
        // Cargar los 5 assets de dron_misil
        this.load.image('dron_misil_0', 'assets/dron_misil/dron_misil_0.png'); // estático
        this.load.image('dron_misil_1', 'assets/dron_misil/dron_misil_1.png'); // izquierda
        this.load.image('dron_misil_2', 'assets/dron_misil/dron_misil_2.png'); // derecha
        this.load.image('dron_misil_3', 'assets/dron_misil/dron_misil_3.png'); // abajo
        this.load.image('dron_misil_4', 'assets/dron_misil/dron_misil_4.png'); // arriba
        this.load.image('dron_misil_destroyed_1', 'assets/dron_misil/dron_missil_destroyed_1.png'); // destruido mirando izquierda
        this.load.image('dron_misil_destroyed_2', 'assets/dron_misil/dron_missil_destroyed_2.png'); // destruido mirando derecha
        // Cargar los 5 assets de dron_bomba
        this.load.image('dron_bomba_0', 'assets/dron_bomba/dron_bomba_0.png'); // estático
        this.load.image('dron_bomba_1', 'assets/dron_bomba/dron_bomba_1.png'); // izquierda
        this.load.image('dron_bomba_2', 'assets/dron_bomba/dron_bomba_2.png'); // derecha
        this.load.image('dron_bomba_3', 'assets/dron_bomba/dron_bomba_3.png'); // abajo
        this.load.image('dron_bomba_4', 'assets/dron_bomba/dron_bomba_4.png'); // arriba
        this.load.image('dron_bomba_destroyed_1', 'assets/dron_bomba/dron_bomba_destroyed_1.png'); // destruido mirando izquierda
        this.load.image('dron_bomba_destroyed_2', 'assets/dron_bomba/dron_bomba_destroyed_2.png'); // destruido mirando derecha
        this.load.image('porta_drones_aereo_0', 'assets/porta_drones_aereo/porta_drones_aereo_0.png');// estático
        this.load.image('porta_drones_aereo_1', 'assets/porta_drones_aereo/porta_drones_aereo_1.png');// izquierda
        this.load.image('porta_drones_aereo_2', 'assets/porta_drones_aereo/porta_drones_aereo_2.png');// derecha
        this.load.image('porta_drones_aereo_3', 'assets/porta_drones_aereo/porta_drones_aereo_3.png');//arriba
        this.load.image('porta_drones_aereo_4', 'assets/porta_drones_aereo/porta_drones_aereo_4.png'); //abajo

        this.load.image('porta_drones_mar_0', 'assets/porta_drones_mar/porta_drones_mar_0.png');// estático
        this.load.image('porta_drones_mar_1', 'assets/porta_drones_mar/porta_drones_mar_1.png');// izquierda
        this.load.image('porta_drones_mar_2', 'assets/porta_drones_mar/porta_drones_mar_2.png');// derecha
        this.load.image('porta_drones_mar_3', 'assets/porta_drones_mar/porta_drones_mar_3.png');//arriba
        this.load.image('porta_drones_mar_4', 'assets/porta_drones_mar/porta_drones_mar_4.png'); //abajo
        // Asset de la bomba para el dron bomba
        this.load.image('bomba', 'assets/dron_bomba/bomba.png');
        // Asset del cohete para el dron misil (directional variants)
        this.load.image('misil_1', 'assets/dron_misil/misil_1.png'); // izquierda
        this.load.image('misil_2', 'assets/dron_misil/misil_2.png'); // derecha
        this.load.image('misil_3', 'assets/dron_misil/misil_3.png'); // abajo
        this.load.image('misil_4', 'assets/dron_misil/misil_4.png'); // arriba

        // Explosion (used by the side impact view for bomb/missile impacts)
        this.load.image('explosion', 'assets/explosion.png');
        this.load.audio('explosion', 'assets/explosion.flac');
        this.load.audio('missile_launch', 'assets/dron_misil/missile_alarm_2.ogg');

        // Background music & selection sounds
        this.load.audio('battle_theme', 'assets/battleTheme.mp3');
        this.load.audio('porta_drones_sound', 'assets/porta_drones_sound.mp3');
        this.load.audio('dron_sound', 'assets/dron_sound.wav');
    }


    create(data) {
        // Battle theme — plays alongside the ocean loop started by LobbyScene
        if (!this.sound.get('battle_theme')) {
            this.sound.add('battle_theme', { loop: true, volume: 0.25 }).play();
        }

        // Tile the background to cover the whole world
        const bg = this.add.tileSprite(0, 0, WORLD_WIDTH, WORLD_HEIGHT, 'mar');
        bg.setOrigin(0, 0);
        // Ajustar tamaño de los assets de dron_misil a 128x128 si es necesario (Phaser lo puede escalar en Drone.js)

        // Draw hex grid over the full world
        this.hexGrid = new HexGrid(this, 35, WORLD_WIDTH, WORLD_HEIGHT);
        this.hexGrid.draw();

        // Graphics for hex hover highlight
        this.hexHighlight = this.add.graphics();
        this.moveRangeGraphics = this.add.graphics();
        this.moveRangeGraphics.setDepth(9);
        this.missileGuideGraphics = this.add.graphics();
        this.manualTargetHex = null;
        this.pendingAerialTarget = null;
        this.lastHighlightedHex = null;

        // Fog-of-war overlay (darkens unexplored/out-of-vision areas)
        this.fogAlpha = 0.65;
        this.fogRT = this.add.renderTexture(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        this.fogRT.setOrigin(0, 0);
        // Keep fog above background/grid but below units (drones are depth ~10)
        this.fogRT.setDepth(8);
        this.fogEraser = this.make.graphics({ x: 0, y: 0, add: false });

        // Camera bounds and initial position
        this.cameras.main.setBounds(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        this.cameras.main.centerOn(WORLD_WIDTH / 2, WORLD_HEIGHT / 2);
        this.cameras.main.setZoom(INITIAL_CAMERA_ZOOM);

        // --- Drag to pan camera ---
        this.input.on('pointerdown', (pointer) => {
            this.isDragging = false;
            this.dragStartX = pointer.x;
            this.dragStartY = pointer.y;
        });

        this.input.on('pointermove', (pointer) => {
            // Handle hex highlight for movement
            this.updateHexHighlight(pointer);

            if (this.actionMode === MODE_ATTACK && this.selectedDrone && this.selectedDrone.droneType === 'Naval' && !pointer.isDown && this.isMyTurn) {
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

        // Mouse wheel zoom (keeps world point under cursor stable while zooming)
        this.input.on('wheel', (pointer, _gameObjects, _deltaX, deltaY) => {
            const camera = this.cameras.main;
            const oldZoom = camera.zoom;
            const newZoom = Phaser.Math.Clamp(
                oldZoom - (deltaY * CAMERA_ZOOM_STEP),
                MIN_CAMERA_ZOOM,
                MAX_CAMERA_ZOOM
            );

            if (newZoom === oldZoom) {
                return;
            }

            const worldPointBefore = camera.getWorldPoint(pointer.x, pointer.y);
            camera.setZoom(newZoom);
            const worldPointAfter = camera.getWorldPoint(pointer.x, pointer.y);

            camera.scrollX += worldPointBefore.x - worldPointAfter.x;
            camera.scrollY += worldPointBefore.y - worldPointAfter.y;
        });

        // Click on empty space to move (if in move mode)
        this.input.on('pointerup', (pointer) => {
            // Clear drag state on release so HUD buttons are not blocked by stale isDragging=true.
            if (this.isDragging) {
                this.isDragging = false;
                return;
            }
            if (!this.isMyTurn || this.gameFinished) return;


            const nearest = this.hexGrid.getNearestCenter(pointer.worldX, pointer.worldY);

            if (this.actionMode === MODE_ATTACK && this.selectedDrone) {
                const carrierByAura = this.findEnemyCarrierAtPoint(pointer.worldX, pointer.worldY);
                const targetUnit = carrierByAura
                    ? {
                        targetType: 'carrier',
                        playerIndex: carrierByAura.playerIndex,
                        droneIndex: -1,
                        sprite: carrierByAura.sprite
                    }
                    : this.findEnemyUnitAtHex(nearest);

                if (this.selectedDrone.droneType === 'Naval') {
                    if (targetUnit) {
                        this.setManualAttackLine({ x: targetUnit.sprite.x, y: targetUnit.sprite.y });
                        this.executeAttack(targetUnit);
                    } else {
                        this.setManualAttackLine(nearest);
                        this.executeManualAttack();
                    }
                } else {
                    console.log('[MainScene] Aereo attack path');
                    if (targetUnit) {
                        this.pendingAerialTarget = targetUnit;
                        if (this.manualTargetHex) {
                            this.executeAttack(targetUnit);
                        }
                    } else {
                        this.manualTargetHex = { x: nearest.x, y: nearest.y };
                        if (this.pendingAerialTarget) {
                            this.executeAttack(this.pendingAerialTarget);
                        }
                    }
                }
                return;
            }

            if (this.actionMode !== MODE_MOVE) return;
            if (!this.selectedDrone && !this.selectedCarrier) return;

            if (this.selectedCarrier) {
                if (this.selectedCarrier.isMoving) return;
                if ((Network.actionsRemaining ?? 0) <= 0) {
                    console.warn('No actions remaining to move carrier');
                    return;
                }
                const distance = this.hexGrid.getHexDistance(
                    this.selectedCarrier.sprite.x,
                    this.selectedCarrier.sprite.y,
                    nearest.x,
                    nearest.y
                );

                if (distance < 1) return;

                if (distance > this.selectedCarrier.maxMoveDistance) {
                    console.warn(`Carrier move too far: ${distance} hexes (max ${this.selectedCarrier.maxMoveDistance})`);
                    this.showRuleWarning('Movimiento fuera de rango');
                    return;
                }

                // Check if target position is occupied (ignore the carrier itself)
                if (this.isPositionOccupied(nearest.x, nearest.y, 15, this.selectedCarrier, { checkDrones: false })) {
                    console.warn('Target position is occupied - carrier cannot move there');
                    return;
                }

                Network.requestCarrierMove(nearest.x, nearest.y);
                return;
            }

            if (this.selectedDrone.isBusy && this.selectedDrone.isBusy()) return;

            const distance = this.hexGrid.getHexDistance(
                this.selectedDrone.sprite.x, this.selectedDrone.sprite.y,
                nearest.x, nearest.y
            );

            if (distance < 1) return;

            const maxMoveDistance = this.getDroneMoveDistance(this.selectedDrone);
            if (distance > maxMoveDistance) {
                console.warn(`Move too far: ${distance} hexes (max ${maxMoveDistance})`);
                this.showRuleWarning('Movimiento fuera de rango');
                return;
            }

            // Check if target position is occupied (ignore the drone itself)
            if (this.isPositionOccupied(nearest.x, nearest.y, 15, this.selectedDrone)) {
                console.warn('Target position is occupied');
                return;
            }

            const droneIndex = this.myDrones.indexOf(this.selectedDrone);
            if (droneIndex >= 0) {
                // Lock further moves until we get the server move + animation completes.
                if (typeof this.selectedDrone.queueMoveLock === 'function') {
                    this.selectedDrone.queueMoveLock();
                }
                Network.requestMove(droneIndex, nearest.x, nearest.y);
            }
        });

        // Wire up Network events
        this.setupNetwork();
        
        // Launch the HUD scene FIRST so it can receive events
        this.scene.stop('HudScene');
        this.scene.launch('HudScene', { mode: 'game' });
        
        // Small delay to ensure HudScene finishes its create() method
        // and sets up event listeners before we emit events
        this.time.delayedCall(50, () => {
            this.initializeGameState(data);
        });
    }

    updateFogOfWar() {
        updateFogOfWar(this);
    }
    
    initializeGameState(data) {
        
        // If we have initial game state from LobbyScene, create drones immediately
        
        if (data && data.gameState) {
            this.createDronesFromState(data.gameState);
            
            this.events.emit('gameStarted');
            
            // CRITICAL: Manually trigger turnStart since the server's turnStart message
            // arrives BEFORE this scene is ready to receive it
            const initialTurn = data.gameState.currentTurn;
            const initialActions = data.gameState.actionsRemaining;
            
            this.isMyTurn = (initialTurn === Network.playerIndex);
            this.actionMode = MODE_MOVE;
            this.clearTargetHighlights();
            
            // Reset per-drone attack state for new turn
            if (this.isMyTurn) {
                for (const drone of this.myDrones) {
                    drone.hasAttacked = false;
                }
            }

            this.actionsPerTurn = data.gameState.actionsPerTurn ?? getGameConfig().actionsPerTurn;
            this.events.emit('actionsUpdated', {
                actionsRemaining: initialActions,
                actionsPerTurn: this.actionsPerTurn
            });

            // Notify HUD
            this.events.emit('turnChanged', {
                isMyTurn: this.isMyTurn
            });
            this.resetTurnCountdown(this.isMyTurn);
        } else {
            console.error('[MainScene] === NO INITIAL GAME STATE PROVIDED! ===');
            console.error('[MainScene] This is a bug - game should not start without state');
        }

        console.log('[MainScene] === MAINSCENE CREATE COMPLETE ===');
    }

    setupNetwork() {
        attachNetworkHandlers(this, { modeMove: MODE_MOVE });
    }

    emitTurnTimerUpdated() {
        this.events.emit('turnTimerUpdated', {
            secondsRemaining: this.turnSecondsRemaining,
            durationSeconds: this.turnDurationSeconds,
            isMyTurn: this.isMyTurn
        });
    }

    stopTurnCountdown() {
        if (this.turnTimerEvent) {
            this.turnTimerEvent.remove(false);
            this.turnTimerEvent = null;
        }
    }

    resetTurnCountdown(activate = false) {
        this.stopTurnCountdown();
        this.turnSecondsRemaining = this.turnDurationSeconds;
        this.emitTurnTimerUpdated();

        if (!activate || !this.isMyTurn || this.gameFinished) {
            return;
        }

        this.turnTimerEvent = this.time.addEvent({
            delay: 1000,
            loop: true,
            callback: () => {
                if (!this.isMyTurn || this.gameFinished) {
                    this.stopTurnCountdown();
                    return;
                }

                this.turnSecondsRemaining = Math.max(0, this.turnSecondsRemaining - 1);
                this.emitTurnTimerUpdated();

                if (this.turnSecondsRemaining <= 0) {
                    this.stopTurnCountdown();
                    this.endTurn();
                }
            }
        });
    }

    showCombatMessage(message) {
        if (!message) return;

        if (this.combatMessageTween) {
            this.combatMessageTween.stop();
            this.combatMessageTween = null;
        }
        if (this.combatMessageText) {
            this.combatMessageText.destroy();
            this.combatMessageText = null;
        }

        const camera = this.cameras.main;
        const text = this.add.text(camera.midPoint.x, camera.midPoint.y - 110, message, {
            fontSize: '32px',
            fill: '#ffd54f',
            fontStyle: 'bold',
            stroke: '#000000',
            strokeThickness: 5
        });

        text.setOrigin(0.5);
        text.setScrollFactor(0);
        text.setDepth(1200);

        this.combatMessageText = text;
        this.combatMessageTween = this.tweens.add({
            targets: text,
            alpha: { from: 1, to: 0 },
            y: text.y - 35,
            duration: 1400,
            ease: 'Quad.easeOut',
            onComplete: () => {
                text.destroy();
                if (this.combatMessageText === text) {
                    this.combatMessageText = null;
                }
                this.combatMessageTween = null;
            }
        });
    }

    showRuleWarning(message) {
        if (!message) return;
        this.events.emit('combatMessage', message);
        this.showCombatMessage(message);
    }

    //Se agrega esta cola para destuir al dron luego e la accion que haga, si se mueve o ataca consuma su combustible luego de la accion
    queueDroneDestroyAfterAction(drone, destroyFn) {
        if (!drone || typeof destroyFn !== 'function') return;
        if (drone._pendingDestroyAfterAction) return;
        drone._pendingDestroyAfterAction = true;

        const tryDestroy = () => {
            if (!drone || drone.destroyed) {
                drone._pendingDestroyAfterAction = false;
                return;
            }
            if (!drone.isMoving && !drone.isAttacking) {
                drone._pendingDestroyAfterAction = false;
                destroyFn();
                this.updateVision();
                this.checkDrawByNoDrones();
                return;
            }
            this.time.delayedCall(100, tryDestroy);
        };

        tryDestroy();
    }

    createCarrierForPlayer(playerIndex, side, spawnHint = null) {
        const basePosition = spawnHint && typeof spawnHint.x === 'number' && typeof spawnHint.y === 'number'
            ? { x: spawnHint.x, y: spawnHint.y }
            : this.getCarrierSpawnPosition(playerIndex, side, spawnHint?.y ?? null);
        const spriteKey = side === 'Naval' ? NAVAL_CARRIER_SPRITES[0] : AEREAL_CARRIER_SPRITES[0];

        if (this.carriers[playerIndex]) {
            this.stopCarrierIdleAnimation(this.carriers[playerIndex]);
            this.carriers[playerIndex].ring.destroy();
            this.carriers[playerIndex].targetRing?.destroy();
            this.carriers[playerIndex].healthBarBg?.destroy();
            this.carriers[playerIndex].healthBar?.destroy();
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

        const targetRing = this.add.circle(basePosition.x, basePosition.y, 52);
        targetRing.setDepth(5);
        targetRing.setStrokeStyle(3, 0xff0000);
        targetRing.setFillStyle();
        targetRing.setVisible(false);

        const healthBarOffsetY = 58;
        const healthBarWidth = 56;
        const healthBarBg = this.add.rectangle(basePosition.x, basePosition.y - healthBarOffsetY, healthBarWidth, 6, 0x333333);
        healthBarBg.setDepth(6);
        healthBarBg.setOrigin(0.5);

        const healthBar = this.add.rectangle(basePosition.x - healthBarWidth / 2, basePosition.y - healthBarOffsetY, healthBarWidth, 4, 0x00ff00);
        healthBar.setDepth(7);
        healthBar.setOrigin(0, 0.5);

        const carrierMaxMoveDistance = side === 'Naval'
            ? this.defaultNavalCarrierMovementRange
            : this.defaultAerialCarrierMovementRange;

        const carrier = {
            sprite,
            ring,
            targetRing,
            healthBarBg,
            healthBar,
            healthBarOffsetY,
            healthBarWidth,
            playerIndex,
            isLocal,
            maxMoveDistance: carrierMaxMoveDistance,
            type: 'carrier',
            side,
            direction: 0,
            health: 5,
            maxHealth: 5,
            destroyed: false,
            isTargetable: false,
            anchorX: basePosition.x,
            anchorY: basePosition.y,
            idleBobTween: null,
            idleRockTween: null
        };

        this.updateCarrierHealthBar(carrier);

        sprite.setInteractive({ useHandCursor: true, pixelPerfect: true, alphaTolerance: 1 });
        sprite.on('pointerup', (pointer) => {
            if (this.isDragging) return;
            pointer.event.stopPropagation();
            this.onCarrierClicked(carrier);
        });

        this.carriers[playerIndex] = carrier;
        this.refreshCarrierIdleAnimation(carrier);
    }

    syncCarrierUiPositions(carrier) {
        if (!carrier?.sprite) return;
        const x = carrier.sprite.x;
        const y = carrier.sprite.y;

        carrier.ring?.setPosition(x, y);
        carrier.targetRing?.setPosition(x, y);
        carrier.healthBarBg?.setPosition(x, y - carrier.healthBarOffsetY);
        carrier.healthBar?.setPosition(x - carrier.healthBarWidth / 2, y - carrier.healthBarOffsetY);
    }

    stopCarrierIdleAnimation(carrier, { resetToAnchor = true } = {}) {
        if (!carrier) return;

        if (carrier.idleBobTween) {
            carrier.idleBobTween.stop();
            carrier.idleBobTween = null;
        }
        if (carrier.idleRockTween) {
            carrier.idleRockTween.stop();
            carrier.idleRockTween = null;
        }

        if (!carrier.sprite?.active || !resetToAnchor) return;

        if (typeof carrier.anchorX === 'number' && typeof carrier.anchorY === 'number') {
            carrier.sprite.setPosition(carrier.anchorX, carrier.anchorY);
        }
        carrier.sprite.setAngle(0);
        this.syncCarrierUiPositions(carrier);
    }

    startCarrierIdleAnimation(carrier) {
        if (!carrier?.sprite?.active || carrier.destroyed || carrier.isMoving || !carrier.sprite.visible) return;

        const hasAerialIdle = carrier.side === 'Aereo' && !!carrier.idleBobTween && !carrier.idleRockTween;
        const hasNavalIdle = carrier.side === 'Naval' && !!carrier.idleBobTween && !!carrier.idleRockTween;
        if (hasAerialIdle || hasNavalIdle) return;

        this.stopCarrierIdleAnimation(carrier);

        const bobTargets = [carrier.sprite, carrier.ring, carrier.targetRing, carrier.healthBarBg, carrier.healthBar].filter(Boolean);
        const startDelay = Phaser.Math.Between(0, 600);

        if (carrier.side === 'Aereo') {
            carrier.idleBobTween = this.tweens.add({
                targets: bobTargets,
                y: `-=${AERIAL_CARRIER_FLOAT_DISTANCE_PX}`,
                duration: AERIAL_CARRIER_FLOAT_DURATION_MS,
                ease: 'Sine.easeInOut',
                yoyo: true,
                repeat: -1,
                delay: startDelay,
                onStop: () => {
                    carrier.idleBobTween = null;
                }
            });
            return;
        }

        carrier.idleBobTween = this.tweens.add({
            targets: bobTargets,
            y: `-=${NAVAL_CARRIER_BOB_DISTANCE_PX}`,
            duration: NAVAL_CARRIER_BOB_DURATION_MS,
            ease: 'Sine.easeInOut',
            yoyo: true,
            repeat: -1,
            delay: startDelay,
            onStop: () => {
                carrier.idleBobTween = null;
            }
        });

        carrier.idleRockTween = this.tweens.add({
            targets: carrier.sprite,
            angle: {
                from: -NAVAL_CARRIER_ROCK_ANGLE_DEG,
                to: NAVAL_CARRIER_ROCK_ANGLE_DEG
            },
            duration: NAVAL_CARRIER_ROCK_DURATION_MS,
            ease: 'Sine.easeInOut',
            yoyo: true,
            repeat: -1,
            delay: startDelay,
            onStop: () => {
                carrier.idleRockTween = null;
            }
        });
    }

    refreshCarrierIdleAnimation(carrier) {
        if (!carrier?.sprite?.active) return;
        if (!carrier.destroyed && !carrier.isMoving && carrier.sprite.visible) {
            this.startCarrierIdleAnimation(carrier);
            return;
        }
        this.stopCarrierIdleAnimation(carrier, { resetToAnchor: !carrier.isMoving });
    }


    getCarrierSpawnPosition(playerIndex, side = null, spawnHintY = null) {
        const minY = CARRIER_SPAWN_Y_MARGIN;
        const maxY = Math.max(minY, WORLD_HEIGHT - CARRIER_SPAWN_Y_MARGIN);
        const fallbackY = Phaser.Math.Between(minY, maxY);
        const y = Phaser.Math.Clamp(spawnHintY ?? fallbackY, minY, maxY);

        const leftSpawn = {
            x: CARRIER_EDGE_MARGIN,
            y
        };
        const rightSpawn = {
            x: Math.max(CARRIER_EDGE_MARGIN, WORLD_WIDTH - CARRIER_EDGE_MARGIN),
            y
        };

        if (side === 'Naval') return rightSpawn;
        if (side === 'Aereo') return leftSpawn;
        return playerIndex === 0 ? leftSpawn : rightSpawn;
    }

    setCarrierSpriteDirection(carrier, directionIndex) {
        if (!carrier) return;
        const spriteSet = carrier.side === 'Naval' ? NAVAL_CARRIER_SPRITES : AEREAL_CARRIER_SPRITES;
        if (!Array.isArray(spriteSet) || spriteSet.length === 0) return;
        const clamped = Phaser.Math.Clamp(directionIndex, 0, spriteSet.length - 1);
        if (carrier.direction === clamped) return;
        carrier.direction = clamped;
        carrier.sprite.setTexture(spriteSet[clamped]);
    }

    getCarrierDirectionFromDelta(dx, dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? 2 : 1; // right : left
        }
        return dy > 0 ? 3 : 4; // down : up
    }

    createDronesFromState(state) {
        if (typeof state?.navalVisionRange === 'number') {
            this.navalVisionRange = state.navalVisionRange;
        }
        if (typeof state?.aerialVisionRange === 'number') {
            this.aereoVisionRange = state.aerialVisionRange;
        }
        if (typeof state?.navalCarrierVisionRange === 'number') {
            this.navalCarrierVisionRange = state.navalCarrierVisionRange;
        }
        if (typeof state?.aerialCarrierVisionRange === 'number') {
            this.aereoCarrierVisionRange = state.aerialCarrierVisionRange;
        }
        
        for (const player of state.players) {
            const isLocal = (player.playerIndex === Network.playerIndex);
            const color = TEAM_COLORS[player.playerIndex];
            this.drones[player.playerIndex] = [];
            const side = player.side || 'Aereo';
            this.playerSides[player.playerIndex] = side;
            if (isLocal) this.localSide = side;
            const spawnHint = (typeof player.carrierX === 'number' && typeof player.carrierY === 'number')
                ? { x: player.carrierX, y: player.carrierY }
                : { x: undefined, y: player.drones?.[0]?.y ?? null };
            this.createCarrierForPlayer(player.playerIndex, side, spawnHint);
            if (this.carriers[player.playerIndex]) {
                const sideCarrierFallback = side === 'Naval'
                    ? getGameConfig().navalCarrierMaxHp
                    : getGameConfig().aerialCarrierMaxHp;
                this.carriers[player.playerIndex].maxHealth = player.carrierMaxHealth ?? sideCarrierFallback;
                this.carriers[player.playerIndex].health = player.carrierHealth ?? this.carriers[player.playerIndex].maxHealth;
                this.carriers[player.playerIndex].destroyed = player.carrierDestroyed === true || this.carriers[player.playerIndex].health <= 0;
                if (this.carriers[player.playerIndex].destroyed) {
                    this.carriers[player.playerIndex].sprite.setVisible(false);
                    this.carriers[player.playerIndex].ring.setVisible(false);
                    this.carriers[player.playerIndex].targetRing?.setVisible(false);
                }
                this.updateCarrierHealthBar(this.carriers[player.playerIndex]);
            }

            for (const d of player.drones) {
                const hex = this.hexGrid.getNearestCenter(d.x, d.y);
                const droneType = d.droneType ?? side ?? 'Aereo';
                
                const drone = new Drone(this, hex.x, hex.y, color, isLocal, {
                    health: d.health,
                    maxHealth: d.maxHealth,
                    attackDamage: d.attackDamage,
                    attackRange: d.attackRange,
                    droneType: droneType,
                    missiles: d.missiles,
                    fuel: d.fuel,
                    maxFuel: d.maxFuel,
                    movementRange: d.movementRange,
                    playerIndex: player.playerIndex
                });
                drone.playerIndex = player.playerIndex;
                drone.droneIndex = this.drones[player.playerIndex].length;

                // Restore deployed state from saved game.
                // Drones that were already on the battlefield must be shown immediately,
                // but only if they are still alive (health > 0).
                if (d.deployed === true) {
                    drone.deployed = true;
                    if (drone.isAlive()) {
                        // Make Phaser objects visible (they start hidden in the Drone constructor)
                        if (drone.sprite) drone.sprite.setVisible(true);
                        if (drone.healthBarBg) drone.healthBarBg.setVisible(true);
                        if (drone.healthBar) drone.healthBar.setVisible(true);
                        if (typeof drone.startFloatingAnimation === 'function') {
                            drone.startFloatingAnimation();
                        }
                    } else {
                        // Mark as destroyed so it is excluded from all game logic
                        drone.destroyed = true;
                    }
                }

                this.drones[player.playerIndex].push(drone);
            }

            if (isLocal) {
                this.myDrones = this.drones[player.playerIndex];
                // Center on first deployed drone, or on the carrier if none are deployed yet
                const firstDeployed = this.myDrones.find(d => d.deployed && d.isAlive());
                const focusSprite = firstDeployed?.sprite ?? this.carriers[player.playerIndex]?.sprite;
                if (focusSprite) {
                    this.cameras.main.centerOn(focusSprite.x, focusSprite.y);
                }
            }
        }

        this.updateVision();
    }


    onCarrierClicked(carrier) {
        if (!carrier || carrier.destroyed) return;

        if (!carrier.isLocal) {
            if (this.actionMode !== MODE_ATTACK || !this.selectedDrone || !this.isMyTurn) return;

            if (this.selectedDrone.droneType === 'Naval') {
                this.setManualAttackLine({ x: carrier.sprite.x, y: carrier.sprite.y });
                this.executeAttack({
                    targetType: 'carrier',
                    playerIndex: carrier.playerIndex,
                    droneIndex: -1,
                    sprite: carrier.sprite
                });
                return;
            }

            const targetHex = this.hexGrid?.getNearestCenter(carrier.sprite.x, carrier.sprite.y) || {
                x: carrier.sprite.x,
                y: carrier.sprite.y
            };
            this.manualTargetHex = targetHex;
            this.pendingAerialTarget = {
                targetType: 'carrier',
                playerIndex: carrier.playerIndex,
                droneIndex: -1,
                sprite: carrier.sprite
            };
            this.executeAttack(this.pendingAerialTarget);
            return;
        }

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
            this._stopSelectionSound();
            this.selectedCarrier.ring.setVisible(false);
            this.selectedCarrier.sprite.clearTint();
            this.stopCarrierPulse(this.selectedCarrier);
            this.selectedCarrier = null;
            this.clearMoveRangeIndicator();
            this.deployPanelCarrier = null;
            this.events.emit('deployPanelClose');
            this.events.emit('selectionChanged', { type: null });
            return;
        }

        this.selectedCarrier = carrier;
        this.clearMoveRangeIndicator();
        carrier.ring.setVisible(true);
        carrier.ring.setPosition(carrier.sprite.x, carrier.sprite.y);
        carrier.sprite.setTint(0xfff176);
        this.startCarrierPulse(carrier);
        this._playSelectionSound('porta_drones_sound', 0.5);
        this.actionMode = MODE_MOVE;
        this.clearTargetHighlights();
        this.events.emit('selectionChanged', {
            type: 'carrier',
            playerIndex: carrier.playerIndex,
            maxMoveDistance: carrier.maxMoveDistance
        });
    }

    moveCarrier(carrier, x, y) {
        if (carrier.isMoving || this.gameFinished) return;
        this.stopCarrierIdleAnimation(carrier);
        carrier.isMoving = true;
        carrier.anchorX = carrier.sprite.x;
        carrier.anchorY = carrier.sprite.y;
        const dx = x - carrier.sprite.x;
        const dy = y - carrier.sprite.y;
        this.setCarrierSpriteDirection(carrier, this.getCarrierDirectionFromDelta(dx, dy));

        const movedTargets = [carrier.sprite, carrier.ring, carrier.targetRing, carrier.healthBarBg]
            .filter(Boolean);

        this.tweens.add({
            targets: movedTargets,
            x,
            y,
            duration: 4000,
            ease: 'Power2',
            onUpdate: () => {
                if (carrier.healthBar) {
                    carrier.healthBar.y = y - carrier.healthBarOffsetY;
                    carrier.healthBar.x = x - carrier.healthBarWidth / 2;
                }
            },
            onComplete: () => {
                carrier.isMoving = false;
                carrier.anchorX = x;
                carrier.anchorY = y;
                this.setCarrierSpriteDirection(carrier, 0);
                this.updateCarrierHealthBar(carrier);
                this.refreshCarrierIdleAnimation(carrier);
                this.updateVision();
            }
        });
    }

    updateCarrierHealthBar(carrier) {
        if (!carrier?.healthBar || !carrier?.healthBarBg) return;
        const maxHealth = Math.max(1, carrier.maxHealth ?? 1);
        const health = Math.max(0, carrier.health ?? 0);
        const ratio = Phaser.Math.Clamp(health / maxHealth, 0, 1);

        carrier.healthBar.width = carrier.healthBarWidth * ratio;

        if (ratio > 0.6) {
            carrier.healthBar.setFillStyle(0x00ff00);
        } else if (ratio > 0.3) {
            carrier.healthBar.setFillStyle(0xffff00);
        } else {
            carrier.healthBar.setFillStyle(0xff0000);
        }

        carrier.healthBarBg.setPosition(carrier.sprite.x, carrier.sprite.y - carrier.healthBarOffsetY);
        carrier.healthBar.setPosition(carrier.sprite.x - carrier.healthBarWidth / 2, carrier.sprite.y - carrier.healthBarOffsetY);

        const visible = !carrier.destroyed && carrier.sprite.visible;
        carrier.healthBarBg.setVisible(visible);
        carrier.healthBar.setVisible(visible);
        this.refreshCarrierIdleAnimation(carrier);
    }

    destroyUndeployedDronesForPlayer(playerIndex) {
        const drones = this.drones?.[playerIndex] ?? [];
        for (const drone of drones) {
            if (!drone?.isAlive() || drone.deployed) continue;
            drone.destroy();
        }
    }

    destroyCarrier(carrier, playExplosion = true) {
        if (!carrier || carrier._destructionHandled) return;

        this.stopCarrierIdleAnimation(carrier);

        carrier.destroyed = true;
        carrier._destructionHandled = true;

        if (this.selectedCarrier === carrier) {
            this.selectedCarrier = null;
            this.deployPanelCarrier = null;
            this.events.emit('deployPanelClose');
            this.events.emit('selectionChanged', { type: null });
        }

        const x = carrier.sprite?.x;
        const y = carrier.sprite?.y;

        if (playExplosion && typeof x === 'number' && typeof y === 'number' && this.textures?.exists('explosion')) {
            const boom = this.add.image(x, y, 'explosion');
            boom.setDepth(30);
            boom.setDisplaySize(220, 220);
            boom.setAlpha(0.95);
            this.sound?.play?.('explosion', { volume: 0.55 });
            this.tweens.add({
                targets: boom,
                alpha: 0,
                scaleX: 1.25,
                scaleY: 1.25,
                duration: 450,
                ease: 'Cubic.easeOut',
                onComplete: () => boom.destroy()
            });
        }

        carrier.sprite?.setVisible(false);
        carrier.ring?.setVisible(false);
        carrier.targetRing?.setVisible(false);
        carrier.healthBarBg?.setVisible(false);
        carrier.healthBar?.setVisible(false);

        this.destroyUndeployedDronesForPlayer(carrier.playerIndex);
        this.updateCarrierHealthBar?.(carrier);
        this.updateVision?.();
        this.checkDrawByNoDrones?.();
    }

    checkDrawByNoDrones() {
        if (this.gameFinished) {
            return;
        }

        const aliveCount = (playerIndex) => {
            const drones = this.drones[playerIndex] || [];
            return drones.filter((drone) => drone?.isAlive()).length;
        };

        const player0Alive = aliveCount(0);
        const player1Alive = aliveCount(1);

        if (player0Alive > 0 || player1Alive > 0) {
            return;
        }

        const carrier0Alive = Boolean(this.carriers?.[0] && !this.carriers[0].destroyed);
        const carrier1Alive = Boolean(this.carriers?.[1] && !this.carriers[1].destroyed);
        if (!carrier0Alive || !carrier1Alive) {
            return;
        }

        this.gameFinished = true;
        this.isMyTurn = false;
        this.stopTurnCountdown();
        this.turnSecondsRemaining = this.turnDurationSeconds;
        this.emitTurnTimerUpdated();
        this.clearTargetHighlights();
        this.clearSelections();
        this.events.emit('turnChanged', { isMyTurn: false });

        const message = `La partida terminó en empate
Volvé al lobby para iniciar otra partida`;

        this.add.text(400, 300, message, {
            fontSize: '22px', fill: '#ffdd57', align: 'center'
        }).setOrigin(0.5).setScrollFactor(0).setDepth(1000);
    }

    findEnemyCarrierAtPoint(worldX, worldY) {
        const enemyIndex = Network.playerIndex === 0 ? 1 : 0;
        const carrier = this.carriers?.[enemyIndex];
        if (!carrier?.sprite || carrier.destroyed || !this.isCarrierVisibleToLocal(carrier)) return null;

        if (this.selectedDrone?.droneType === 'Aereo') {
            const attackDistance = this.hexGrid.getHexDistance(
                this.selectedDrone.sprite.x,
                this.selectedDrone.sprite.y,
                carrier.sprite.x,
                carrier.sprite.y
            );
            if (attackDistance > (this.selectedDrone.attackRange ?? 0)) return null;
        }

        const dx = worldX - carrier.sprite.x;
        const dy = worldY - carrier.sprite.y;
        const distancePx = Math.sqrt(dx * dx + dy * dy);
        const ringRadius = carrier.targetRing?.radius ?? 52;
        const spriteRadius = Math.min(carrier.sprite.displayWidth, carrier.sprite.displayHeight) * 0.42;
        const effectiveHitRadius = Math.max(ringRadius, spriteRadius, 68);
        return distancePx <= effectiveHitRadius ? carrier : null;
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

    /**
     * Called from HudScene when the player clicks 'Desplegar' on a drone.
     * Finds a valid deploy position near the carrier and sends a move request.
     */
    deployDroneFromPanel(droneIndex) {
        if (!this.isMyTurn) return;
        if ((Network.actionsRemaining ?? 0) <= 0) {
            console.warn('[Deploy] No actions remaining');
            return;
        }

        const drone = this.myDrones[droneIndex];
        if (!drone || drone.deployed || !drone.isAlive()) return;

        const carrier = this.deployPanelCarrier || this.selectedCarrier;
        if (!carrier) return;

        const pos = this.getDeployPosition(carrier);
        if (!pos) {
            console.warn('[Deploy] No free position found near carrier');
            return;
        }

        if (typeof drone.queueMoveLock === 'function') {
            drone.queueMoveLock();
        }

        Network.requestMove(droneIndex, pos.x, pos.y);

        // Close the panel; player can re-click carrier to deploy next drone
        this.deployPanelCarrier = null;
        this.events.emit('deployPanelClose');
    }

    /**
     * Finds the nearest free hex position 2 hexes above or below the given carrier.
     * Returns null if no valid position is found.
     */
    getDeployPosition(carrier) {
        const cx = carrier.sprite.x;
        const cy = carrier.sprite.y;
        // Height between adjacent hex rows (pointy-top hexes, size = this.hexGrid.size)
        const rowH = 2 * this.hexGrid.size * 0.75;

        // Priority: 2 above, 2 below, then 1 / 3 rows in each direction
        const offsets = [-2, 2, -3, 3, -1, 1, -4, 4];
        for (const offset of offsets) {
            const candY = cy + offset * rowH;
            const snapped = this.hexGrid.getNearestCenter(cx, candY);
            if (!snapped) continue;

            // Must not land on the carrier's own hex
            const distToCarrier = Math.sqrt((snapped.x - cx) ** 2 + (snapped.y - cy) ** 2);
            if (distToCarrier < 30) continue;

            if (!this.isPositionOccupied(snapped.x, snapped.y, 15)) {
                return snapped;
            }
        }
        return null;
    }

    /** Called when any drone is clicked */
    onDroneClicked(drone, pointer = null) {
        if (this.actionMode === MODE_ATTACK) {
            // Allow direct enemy click in attack mode as a valid manual shot direction.
            if (!drone.isLocal && drone.isAlive() && this.selectedDrone && this.isMyTurn) {
                const carrierByAura = pointer
                    ? this.findEnemyCarrierAtPoint(pointer.worldX, pointer.worldY)
                    : null;
                if (carrierByAura && carrierByAura.playerIndex === drone.playerIndex) {
                    this.onCarrierClicked(carrierByAura);
                    return;
                }

                if (this.selectedDrone.droneType === 'Naval') {
                    this.setManualAttackLine({ x: drone.sprite.x, y: drone.sprite.y });
                    this.executeAttack(drone);
                    return;
                }
                const targetHex = this.hexGrid?.getNearestCenter(drone.sprite.x, drone.sprite.y) || {
                    x: drone.sprite.x,
                    y: drone.sprite.y
                };
                this.manualTargetHex = targetHex;
                this.pendingAerialTarget = {
                    targetType: 'drone',
                    playerIndex: drone.playerIndex,
                    droneIndex: drone.droneIndex,
                    sprite: drone.sprite
                };
                this.executeAttack(this.pendingAerialTarget);
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
            this._stopSelectionSound();
            this.selectedDrone = null;
            this.actionMode = MODE_MOVE;
            this.clearTargetHighlights();
            this.hexHighlight.clear();
            this.clearMoveRangeIndicator();
            this.events.emit('selectionChanged', { type: null });
            return;
        }

        this.selectedDrone = drone;
        drone.select();
        drone.sprite.setTint(0xfff176);
        this._playSelectionSound('dron_sound', 0.4);

        const droneNumber = this.myDrones.indexOf(drone) + 1;
        this.events.emit('selectionChanged', {
            type: 'drone',
            droneNumber,
            maxMoveDistance: this.getDroneMoveDistance(drone)
        });

        // Reset to move mode when selecting a new drone
        this.actionMode = MODE_MOVE;
        this.clearTargetHighlights();
    }

    /** Switch to attack mode - called from HUD */
    enterAttackMode() {
        if (!this.isMyTurn || !this.selectedDrone) return;
        if (this.selectedDrone.hasAttacked) return; // Already attacked this turn
        if (this.selectedDrone.isBusy && this.selectedDrone.isBusy()) return;
        const ammo = this.selectedDrone.missiles ?? 0;
        if (ammo <= 0) return;
        if (this.selectedDrone.droneType === 'Naval' && !this.selectedDrone.canUseMissileAttack()) return;

        this.actionMode = MODE_ATTACK;
        this.hexHighlight.clear();
        this.clearMoveRangeIndicator();
        this.manualTargetHex = null;

        if (this.selectedDrone.droneType === 'Naval') {
            const pointer = this.input.activePointer;
            if (pointer) {
                const nearest = this.hexGrid.getNearestCenter(pointer.worldX, pointer.worldY);
                this.setManualAttackLine(nearest);
            }
        } else {
            this.missileGuideGraphics.clear();
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
            if (!drone.isAlive() || !drone.deployed || !this.isDroneVisibleToLocal(drone)) continue;

            // Bomb drone: only allow clicking targets within weapon range.
            if (this.selectedDrone?.droneType === 'Aereo') {
                const distance = this.hexGrid.getHexDistance(
                    this.selectedDrone.sprite.x,
                    this.selectedDrone.sprite.y,
                    drone.sprite.x,
                    drone.sprite.y
                );
                if (distance > (this.selectedDrone.attackRange ?? 0)) continue;
            }

            drone.setTargetable(true);
        }

        const enemyCarrier = this.carriers?.[enemyIndex];
        if (enemyCarrier && !enemyCarrier.destroyed && this.isCarrierVisibleToLocal(enemyCarrier)) {
            if (this.selectedDrone?.droneType === 'Aereo') {
                const distance = this.hexGrid.getHexDistance(
                    this.selectedDrone.sprite.x,
                    this.selectedDrone.sprite.y,
                    enemyCarrier.sprite.x,
                    enemyCarrier.sprite.y
                );
                if (distance <= (this.selectedDrone.attackRange ?? 0)) {
                    this.setCarrierTargetable(enemyCarrier, true);
                }
            } else {
                this.setCarrierTargetable(enemyCarrier, true);
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
        for (const pi in this.carriers) {
            this.setCarrierTargetable(this.carriers[pi], false);
        }
        this.manualTargetHex = null;
        this.pendingAerialTarget = null;
        this.missileGuideGraphics.clear();
    }

    setCarrierTargetable(carrier, isTargetable) {
        if (!carrier?.targetRing) return;
        carrier.isTargetable = isTargetable;
        carrier.targetRing.setPosition(carrier.sprite.x, carrier.sprite.y);
        const shouldShow = isTargetable && !carrier.destroyed && this.isCarrierVisibleToLocal(carrier);
        carrier.targetRing.setVisible(shouldShow);
    }

    getVisionRangeForSide(side) {
        return getVisionRangeForSide(this, side);
    }

    getCarrierVisionRangeForSide(side) {
        return getCarrierVisionRangeForSide(this, side);
    }

    /** Returns true if the given drone should be visible to the local player. */
    isDroneVisibleToLocal(drone) {
        return isDroneVisibleToLocal(this, drone);
    }


    /** Returns true if the given carrier should be visible to the local player. */
    isCarrierVisibleToLocal(carrier) {
        return isCarrierVisibleToLocal(this, carrier);
    }

    /**
     * Check if a world position is occupied by any drone or carrier.
     * Uses a tolerance for near-hexagon positions.
     * @param {number} x - world x coordinate
     * @param {number} y - world y coordinate
     * @param {number} tolerance - distance threshold (pixels)
     * @param {object} ignoreUnit - drone or carrier to ignore in the check (e.g., the unit being moved)
     */
    isPositionOccupied(x, y, tolerance = 15, ignoreUnit = null, options = {}) {
        const { checkDrones = true } = options;
        // Check all drones
        if (checkDrones) {
            for (const pi in this.drones) {
                for (const drone of this.drones[pi]) {
                    if (!drone?.isAlive() || !drone.sprite) continue;
                    if (!drone.deployed) continue; // Undeployed drones are inside the carrier, not on the map
                    if (ignoreUnit && ignoreUnit === drone) continue; // Skip the moving unit
                    const dx = drone.sprite.x - x;
                    const dy = drone.sprite.y - y;
                    const dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < tolerance) {
                        return true;
                    }
                }
            }
        }

        // Check all carriers
        for (const pi in this.carriers) {
            const carrier = this.carriers[pi];
            if (!carrier?.sprite) continue;
            if (ignoreUnit && ignoreUnit === carrier) continue; // Skip the moving unit
            const dx = carrier.sprite.x - x;
            const dy = carrier.sprite.y - y;
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < tolerance) {
                return true;
            }
        }

        return false;
    }

    /**
     * Updates visibility of enemy units based on the local player's vision range.
     * (Aereo = 2x Naval)
     */
    updateVision() {
        updateVision(this);
    }

    executeAttack(targetUnit) {
        if (!this.selectedDrone || !this.isMyTurn) {
            console.log('[MainScene] executeAttack early return: no selectedDrone or not myTurn');
            return;
        }
        
        if (this.selectedDrone.hasAttacked) {
            console.log('[MainScene] executeAttack early return: already attacked');
            return; // Already attacked this turn
        }
        
        if (this.selectedDrone.isBusy && this.selectedDrone.isBusy()) {
            console.log('[MainScene] executeAttack early return: drone is busy');
            return;
        }

        const ammo = this.selectedDrone.missiles ?? 0;
        if (ammo <= 0) {
            console.log('[MainScene] executeAttack early return: no ammo');
            return;
        }
        
        if (this.selectedDrone.droneType === 'Naval' && !this.selectedDrone.canUseMissileAttack()) {
            console.log('[MainScene] executeAttack early return: Naval drone no missiles');
            return;
        }

        const attackerIndex = this.myDrones.indexOf(this.selectedDrone);
        if (attackerIndex < 0) {
            console.log('[MainScene] executeAttack early return: attackerIndex < 0');
            return;
        }

        if (!targetUnit?.sprite) {
            console.log('[MainScene] executeAttack early return: no target or no sprite');
            return;
        }

        if (this.selectedDrone.droneType === "Aereo" && !this.manualTargetHex) {
            console.log('[MainScene] executeAttack early return: no selected aerial destination');
            return;
        }

        // Bomb drone must respect its normal range on the client side (server will also validate).
        if (this.selectedDrone.droneType === 'Aereo') {
            const distance = this.hexGrid.getHexDistance(
                this.selectedDrone.sprite.x,
                this.selectedDrone.sprite.y,
                targetUnit.sprite.x,
                targetUnit.sprite.y
            );
            console.log('[MainScene] Aereo attack distance check:', distance, 'range:', this.selectedDrone.attackRange);
            if (distance > (this.selectedDrone.attackRange ?? 0)) {
                console.log('[MainScene] executeAttack early return: distance too far');
                this.showRuleWarning('Objetivo fuera de rango para atacar');
                return;
            }
        }

        const lineTarget = this.selectedDrone.droneType === 'Naval'
            ? (this.manualTargetHex || targetUnit.sprite)
            : targetUnit.sprite;

        if (typeof this.selectedDrone.queueAttackLock === 'function') {
            this.selectedDrone.queueAttackLock();
        }
        Network.requestAttack(
            attackerIndex,
            targetUnit.playerIndex,
            targetUnit.droneIndex,
            lineTarget.x,
            lineTarget.y,
            this.selectedDrone.droneType === "Aereo" ? this.manualTargetHex.x : null,
            this.selectedDrone.droneType === "Aereo" ? this.manualTargetHex.y : null,
            targetUnit.targetType || "drone"
        );
    }

    executeManualAttack() {
        if (!this.manualTargetHex || !this.selectedDrone || !this.isMyTurn) return;
        if (this.selectedDrone.hasAttacked) return;
        if (this.selectedDrone.isBusy && this.selectedDrone.isBusy()) return;
        // Only missile drones can do blind/manual shots.
        if (this.selectedDrone.droneType !== 'Naval') return;
        const ammo = this.selectedDrone.missiles ?? 0;
        if (ammo <= 0) return;
        if (this.selectedDrone.droneType === 'Naval' && !this.selectedDrone.canUseMissileAttack()) return;

        const attackerIndex = this.myDrones.indexOf(this.selectedDrone);
        if (attackerIndex < 0) return;

        // Allow manual fire to any hex even if no enemy drone is visible.
        const enemyPlayerIndex = Network.playerIndex === 0 ? 1 : 0;

        if (typeof this.selectedDrone.queueAttackLock === 'function') {
            this.selectedDrone.queueAttackLock();
        }
        Network.requestAttack(
            attackerIndex,
            enemyPlayerIndex,
            -1,
            this.manualTargetHex.x,
            this.manualTargetHex.y
        );
    }

    /**
     * Returns the nearest visible enemy drone to the clicked hex, if it is close enough
     * and inside aerial weapon range.
     */
    findEnemyUnitAtHex(hexCenter) {
        if (!hexCenter || !this.selectedDrone) {
            console.log('[MainScene] Early return: no hexCenter or no selected drone');
            return null;
        }

        const enemyIndex = Network.playerIndex === 0 ? 1 : 0; 
    
        const enemies = (this.drones[enemyIndex] || []).filter((drone) => {
            if (!drone?.isAlive() || !drone.deployed) return false;
            if (!this.isDroneVisibleToLocal(drone)) return false;

            if (this.selectedDrone.droneType === 'Aereo') {
                const attackDistance = this.hexGrid.getHexDistance(
                    this.selectedDrone.sprite.x,
                    this.selectedDrone.sprite.y,
                    drone.sprite.x,
                    drone.sprite.y
                );
                return attackDistance <= (this.selectedDrone.attackRange ?? 0);
            }

            return true;
        });

        const enemyCarrier = this.findEnemyCarrierAtHex(hexCenter);
        if (enemyCarrier) {
            return {
                targetType: 'carrier',
                playerIndex: enemyCarrier.playerIndex,
                droneIndex: -1,
                sprite: enemyCarrier.sprite
            };
        }
        if (!enemies.length) return null;

        let closestEnemy = null;
        let closestDistancePx = Number.POSITIVE_INFINITY;
        for (const enemy of enemies) {
            const dx = hexCenter.x - enemy.sprite.x;
            const dy = hexCenter.y - enemy.sprite.y;
            const distancePx = Math.sqrt(dx * dx + dy * dy);

            if (distancePx < closestDistancePx) {
                closestDistancePx = distancePx;
                closestEnemy = enemy;
            }
        }

        // Require the clicked hex to be reasonably close to the target's hex center.
        const maxSnapDistance = this.hexGrid.size * 0.9;
        return closestDistancePx <= maxSnapDistance ? {
            targetType: "drone",
            playerIndex: closestEnemy.playerIndex,
            droneIndex: closestEnemy.droneIndex,
            sprite: closestEnemy.sprite
        } : null;
    }

    findEnemyCarrierAtHex(hexCenter) {
        const enemyIndex = Network.playerIndex === 0 ? 1 : 0;
        const carrier = this.carriers?.[enemyIndex];
        if (!carrier?.sprite || !this.isCarrierVisibleToLocal(carrier)) return null;
        if (carrier.destroyed) return null;

        if (this.selectedDrone?.droneType === 'Aereo') {
            const attackDistance = this.hexGrid.getHexDistance(
                this.selectedDrone.sprite.x,
                this.selectedDrone.sprite.y,
                carrier.sprite.x,
                carrier.sprite.y
            );
            if (attackDistance > (this.selectedDrone.attackRange ?? 0)) return null;
        }

        const dx = hexCenter.x - carrier.sprite.x;
        const dy = hexCenter.y - carrier.sprite.y;
        const distancePx = Math.sqrt(dx * dx + dy * dy);
        const maxSnapDistance = Math.max(this.hexGrid.size * 0.9, carrier.targetRing?.radius ?? 52);
        return distancePx <= maxSnapDistance ? carrier : null;
    }

    setManualAttackLine(hexCenter) {
        if (!this.selectedDrone || !hexCenter) return;
        const startX = this.selectedDrone.sprite.x;
        const startY = this.selectedDrone.sprite.y;

        const dx = hexCenter.x - startX;
        const dy = hexCenter.y - startY;
        const magnitude = Math.sqrt(dx * dx + dy * dy);
        if (magnitude === 0) return;

        const maxDistancePixels = this.hexGrid.size * Math.sqrt(3) * this.manualAttackMaxDistance;
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

        const endX = typeof lineX === 'number' ? lineX : targetDrone?.sprite?.x;
        const endY = typeof lineY === 'number' ? lineY : targetDrone?.sprite?.y;
        if (typeof endX !== 'number' || typeof endY !== 'number') {
            trail.destroy();
            missile.destroy();
            return;
        }

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

        this._stopSelectionSound();
        this.clearMoveRangeIndicator();
        this.deployPanelCarrier = null;
        this.events.emit('deployPanelClose');
        this.events.emit('selectionChanged', { type: null });
    }

    clearMoveRangeIndicator() {
        this.moveRangeGraphics?.clear();
    }

    /** Start a looping selection sound, stopping any previously playing one. */
    _playSelectionSound(key, volume) {
        this._stopSelectionSound();
        if (this.sound.get(key)?.isPlaying) return; // guard against re-entry
        this._selectionSound = this.sound.add(key, { loop: true,  volume });
        this._selectionSound.play();
    }

    /** Stop and discard the current selection loop sound. */
    _stopSelectionSound() {
        if (this._selectionSound) {
            this._selectionSound.stop();
            this._selectionSound.destroy();
            this._selectionSound = null;
        }
    }

    /** Recall the currently selected deployed drone back to its carrier — called from HUD */
    recallSelectedDrone() {
        if (!this.canRecallSelectedDrone()) return;
        const droneIndex = this.myDrones.indexOf(this.selectedDrone);
        if (droneIndex === -1) return;
        Network.requestRecall(droneIndex);
    }

    /**
     * Returns true if the currently selected drone can be recalled:
     * it must be deployed, it's our turn, actions remain, and the drone
     * must be within 2 hexes of the player's carrier.
     */
    canRecallSelectedDrone() {
        if (!this.isMyTurn || !this.selectedDrone) return false;
        if (!this.selectedDrone.deployed) return false;
        if ((Network.actionsRemaining ?? 0) <= 0) return false;
        const carrier = this.carriers[Network.playerIndex];
        if (!carrier?.sprite) return false;
        if (carrier.destroyed) return false;
        const dist = this.hexGrid.getHexDistance(
            this.selectedDrone.sprite.x, this.selectedDrone.sprite.y,
            carrier.sprite.x, carrier.sprite.y
        );
        return dist <= 2;
    }

    /** End turn early - called from HUD */
    endTurn() {
        if (!this.isMyTurn) return;

        this.isMyTurn = false;
        this.stopTurnCountdown();
        this.turnSecondsRemaining = this.turnDurationSeconds;
        this.emitTurnTimerUpdated();
        this.actionMode = MODE_MOVE;
        this.clearTargetHighlights();
        this.clearSelections();
        this.events.emit('turnChanged', { isMyTurn: false });

        Network.endTurn();
    }

    /** Returns a flat list of visible units for the minimap. */
    getVisibleUnitsForMinimap() {
        const all = [];

        // Drones
        for (const pi in this.drones) {
            for (const drone of this.drones[pi]) {
                if (drone.isAlive() && drone.deployed) {
                    const playerIndex = parseInt(pi);
                    if (playerIndex === Network.playerIndex || this.isDroneVisibleToLocal(drone)) {
                        all.push({
                            x: drone.sprite.x,
                            y: drone.sprite.y,
                            playerIndex,
                            isSelected: this.selectedDrone === drone
                        });
                    }
                }
            }
        }

        // Carriers (friendly always visible, enemy only by vision)
        for (const pi in this.carriers) {
            const playerIndex = parseInt(pi);
            const carrier = this.carriers[pi];
            if (!carrier?.sprite) continue;

            if (playerIndex === Network.playerIndex || this.isCarrierVisibleToLocal(carrier)) {
                all.push({
                    x: carrier.sprite.x,
                    y: carrier.sprite.y,
                    playerIndex,
                    isSelected: this.selectedCarrier === carrier
                });
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
            this.clearMoveRangeIndicator();
            return;
        }

        const nearest = this.hexGrid.getNearestCenter(pointer.worldX, pointer.worldY);
        const selectedUnitSprite = this.selectedCarrier ? this.selectedCarrier.sprite : this.selectedDrone.sprite;
        const maxDistance = this.selectedCarrier
            ? this.selectedCarrier.maxMoveDistance
            : this.getDroneMoveDistance(this.selectedDrone);

        // Calculate distance from selected unit
        const distance = this.hexGrid.getHexDistance(
            selectedUnitSprite.x, selectedUnitSprite.y,
            nearest.x, nearest.y
        );

        // Skip if on the same hex as the drone
        if (distance < 1) {
            this.lastHighlightedHex = null;
            this.clearMoveRangeIndicator();
            return;
        }

        // Choose color based on distance
        const color = distance <= maxDistance ? 0x00ff00 : 0xffff00; // green if in range, yellow if too far
        const alpha = 0.3;

        this.hexGrid.drawFilledHex(this.hexHighlight, nearest.x, nearest.y, color, alpha);

        const lineColor = distance <= maxDistance ? 0x26c6da : 0xffc107;
        this.moveRangeGraphics.clear();
        this.moveRangeGraphics.lineStyle(3, lineColor, 0.9);
        this.moveRangeGraphics.beginPath();
        this.moveRangeGraphics.moveTo(selectedUnitSprite.x, selectedUnitSprite.y);
        this.moveRangeGraphics.lineTo(nearest.x, nearest.y);
        this.moveRangeGraphics.strokePath();

        const hexWidth = Math.sqrt(3) * this.hexGrid.size;
        const maxDistancePixels = maxDistance * hexWidth;
        const dx = nearest.x - selectedUnitSprite.x;
        const dy = nearest.y - selectedUnitSprite.y;
        const magnitude = Math.sqrt(dx * dx + dy * dy);
        if (magnitude > 0) {
            const factor = Math.min(1, maxDistancePixels / magnitude);
            const limitX = selectedUnitSprite.x + dx * factor;
            const limitY = selectedUnitSprite.y + dy * factor;
            this.moveRangeGraphics.fillStyle(0xffffff, 0.95);
            this.moveRangeGraphics.fillCircle(limitX, limitY, 4);
        }

        this.lastHighlightedHex = nearest;
    }

    getDroneMoveDistance(drone) {
        if (!drone) return DEFAULT_MAX_MOVE_DISTANCE;
        if (typeof drone.movementRange === 'number' && drone.movementRange > 0) {
            return drone.movementRange;
        }
        if (drone.droneType === 'Naval') return this.defaultNavalDroneMovementRange;
        return this.defaultAerialDroneMovementRange;
    }

    update() {}
}
