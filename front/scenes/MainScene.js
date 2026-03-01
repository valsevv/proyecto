import HexGrid from '../utils/HexGrid.js';
import Drone from '../gameobjects/Drone.js';
import Network from '../network/NetworkManager.js'
import { WORLD_WIDTH, WORLD_HEIGHT } from '../shared/constants.js';

const TEAM_COLORS = [0x00ff00, 0xff4444]; // green = player 0, red = player 1
const MAX_MOVE_DISTANCE = 6; // hexes per turn
const MAX_CARRIER_MOVE_DISTANCE = 3; // hexes per turn (parametrizable)
const MAX_MANUAL_ATTACK_DISTANCE = 15; // max range in hexes for manual missile aiming (parametrizable)

// Vision ranges (in hexes). Aereo must see 2x Naval.
// Adjust NAVAL_VISION_RANGE to tune both.
const NAVAL_VISION_RANGE = 6;
const AEREO_VISION_RANGE = NAVAL_VISION_RANGE * 2;
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

        /** { playerIndex: 'Naval' | 'Aereo' } */
        this.playerSides = {};
        this.localSide = 'Aereo';

        // Vision ranges (in hexes). Defaults are overridden by server gameState when available.
        this.navalVisionRange = NAVAL_VISION_RANGE;
        this.aereoVisionRange = AEREO_VISION_RANGE;

        // Turn state
        this.isMyTurn = false;
        this.actionMode = MODE_MOVE;
        this.actionsPerTurn = 10;
    }


    preload() {
        this.load.image('mar', 'assets/mar.png');
        // Cargar los 5 assets de dron_misil
        this.load.image('dron_misil_0', 'assets/dron_misil/dron_misil_0.png'); // estático
        this.load.image('dron_misil_1', 'assets/dron_misil/dron_misil_1.png'); // izquierda
        this.load.image('dron_misil_2', 'assets/dron_misil/dron_misil_2.png'); // derecha
        this.load.image('dron_misil_3', 'assets/dron_misil/dron_misil_3.png'); // abajo
        this.load.image('dron_misil_4', 'assets/dron_misil/dron_misil_4.png'); // arriba
        // Cargar los 5 assets de dron_bomba
        this.load.image('dron_bomba_0', 'assets/dron_bomba/dron_bomba_0.png'); // estático
        this.load.image('dron_bomba_1', 'assets/dron_bomba/dron_bomba_1.png'); // izquierda
        this.load.image('dron_bomba_2', 'assets/dron_bomba/dron_bomba_2.png'); // derecha
        this.load.image('dron_bomba_3', 'assets/dron_bomba/dron_bomba_3.png'); // abajo
        this.load.image('dron_bomba_4', 'assets/dron_bomba/dron_bomba_4.png'); // arriba
        this.load.image('porta_drones_volador', 'assets/porta_drones_volador.png');
        this.load.image('porta_drones_mar', 'assets/porta_drones_mar.png');
        // Asset de la bomba para el dron bomba
        this.load.image('bomba', 'assets/dron_bomba/bomba.png');
        // Asset del cohete para el dron misil (directional variants)
        this.load.image('misil', 'assets/dron_misil/misil.png'); // default/static
        this.load.image('misil_1', 'assets/dron_misil/misil_1.png'); // izquierda
        this.load.image('misil_2', 'assets/dron_misil/misil_2.png'); // derecha
        this.load.image('misil_3', 'assets/dron_misil/misil_3.png'); // abajo
        this.load.image('misil_4', 'assets/dron_misil/misil_4.png'); // arriba

        // Explosion (used by the side impact view for bomb/missile impacts)
        this.load.image('explosion', 'assets/explosion.png');
        this.load.audio('explosion', 'assets/explosion.flac');
        this.load.audio('missile_launch', 'assets/dron_misil/missile_launch.wav');
    }


    create(data) {
        console.log('[MainScene] === CREATE CALLED ===');
        console.log('[MainScene] Scene data received:', data);
        console.log('[MainScene] Has gameState?:', !!data?.gameState);
        
        // Tile the background to cover the whole world
        const bg = this.add.tileSprite(0, 0, WORLD_WIDTH, WORLD_HEIGHT, 'mar');
        bg.setOrigin(0, 0);
        // Ajustar tamaño de los assets de dron_misil a 128x128 si es necesario (Phaser lo puede escalar en Drone.js)

        // Draw hex grid over the full world
        this.hexGrid = new HexGrid(this, 35, WORLD_WIDTH, WORLD_HEIGHT);
        this.hexGrid.draw();

        // Graphics for hex hover highlight
        this.hexHighlight = this.add.graphics();
        this.missileGuideGraphics = this.add.graphics();
        this.manualTargetHex = null;
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
                // Naval = missile: allow manual aiming to any hex.
                // Aereo = bomb: requires clicking a target drone (no blind shot on empty space).
                if (this.selectedDrone.droneType === 'Naval') {
                    this.setManualAttackLine(nearest);
                    this.executeManualAttack();
                }
                return;
            }

            if (this.actionMode !== MODE_MOVE) return;
            if (!this.selectedDrone && !this.selectedCarrier) return;

            if (this.selectedCarrier) {
                if (this.selectedCarrier.isMoving) return;
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

                // Check if target position is occupied (ignore the carrier itself)
                if (this.isPositionOccupied(nearest.x, nearest.y, 15, this.selectedCarrier)) {
                    console.warn('Target position is occupied - carrier cannot move there');
                    return;
                }

                this.moveCarrier(this.selectedCarrier, nearest.x, nearest.y);
                return;
            }

            if (this.selectedDrone.isBusy && this.selectedDrone.isBusy()) return;

            const distance = this.hexGrid.getHexDistance(
                this.selectedDrone.sprite.x, this.selectedDrone.sprite.y,
                nearest.x, nearest.y
            );

            if (distance < 1) return;

            if (distance > MAX_MOVE_DISTANCE) {
                console.warn(`Move too far: ${distance} hexes (max ${MAX_MOVE_DISTANCE})`);
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
        console.log('[MainScene] === LAUNCHING HUD SCENE ===');
        this.scene.launch('HudScene');
        console.log('[MainScene] === HUD SCENE LAUNCHED (booting...) ===');
        
        // Small delay to ensure HudScene finishes its create() method
        // and sets up event listeners before we emit events
        this.time.delayedCall(50, () => {
            this.initializeGameState(data);
        });
    }

    updateFogOfWar() {
        if (!this.fogRT || !this.hexGrid) return;

        // Fill the whole world with fog
        this.fogRT.clear();
        this.fogRT.fill(0x000000, this.fogAlpha);

        const visionRange = this.getVisionRangeForSide(this.localSide);
        if (!visionRange || visionRange <= 0) {
            return;
        }

        // Approximate vision radius in pixels (matches HexGrid.getHexDistance scaling)
        const hexWidth = Math.sqrt(3) * this.hexGrid.size;
        const radiusPx = visionRange * hexWidth;

        this.fogEraser.clear();
        this.fogEraser.fillStyle(0xffffff, 1);
        this.fogEraser.fillCircle(0, 0, radiusPx);

        const sources = [];
        const myDrones = this.drones[Network.playerIndex] || [];
        for (const d of myDrones) {
            if (d?.isAlive() && d.sprite) sources.push(d.sprite);
        }
        const myCarrier = this.carriers?.[Network.playerIndex];
        if (myCarrier?.sprite) sources.push(myCarrier.sprite);

        for (const s of sources) {
            this.fogRT.erase(this.fogEraser, s.x, s.y);
        }
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

            this.updateVision();
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

            // Movement can affect what is visible.
            this.updateVision();
        });

        Network.on('attackResult', (msg) => {
            console.log('[MainScene] === ATTACK RESULT ===', msg);
            const targetDrone = this.drones[msg.targetPlayer]?.[msg.targetDrone];
            const hit = msg.hit !== false;
            const attackerDrone = this.drones[msg.attackerPlayer]?.[msg.attackerDrone];
            const attackerSide = this.playerSides[msg.attackerPlayer];
            const isNavalAttacker = attackerSide === 'Naval' || attackerDrone?.droneType === 'Naval';
            const isAereoAttacker = attackerSide === 'Aereo' || attackerDrone?.droneType === 'Aereo';

            console.log('[MainScene] Attacker:', { isAereoAttacker, isNavalAttacker, droneType: attackerDrone?.droneType });
            console.log('[MainScene] Target drone found?', !!targetDrone);

            if (attackerDrone && typeof attackerDrone.clearAttackLock === 'function') {
                attackerDrone.clearAttackLock();
            }

            if (attackerDrone && (targetDrone || (typeof msg.lineX === 'number' && typeof msg.lineY === 'number'))) {
                if (isAereoAttacker) {
                    // Animación especial para dron bomba
                    if (targetDrone) {
                        console.log('[MainScene] Playing Aereo (bomb) attack animation');
                        attackerDrone.aereoDronAttack(
                            targetDrone.sprite.x,
                            targetDrone.sprite.y,
                            msg.attackerX,
                            msg.attackerY,
                            targetDrone
                        );
                    } else {
                        console.warn('[MainScene] Aereo attack but targetDrone not found!');
                    }
                } else if (isNavalAttacker) {
                    // Animación especial para dron misil
                    console.log('[MainScene] Playing Naval (missile) attack animation');
                    const targetPos = targetDrone?.sprite || { x: msg.lineX, y: msg.lineY };
                    attackerDrone.launchMissile(targetPos.x, targetPos.y, targetDrone ?? null);
                } else if (targetDrone) {
                    console.log('[MainScene] Playing default attack animation');
                    this.playMissileShot(attackerDrone, targetDrone, hit, msg.lineX, msg.lineY);
                }
            }

            if (targetDrone && hit) {
                targetDrone.takeDamage(msg.damage, msg.remainingHealth);
            }
            // Mark attacker as having attacked
            if (attackerDrone && msg.attackerPlayer === Network.playerIndex) {
                attackerDrone.hasAttacked = true;
                if (attackerDrone.droneType === 'Naval') {
                    attackerDrone.consumeMissile();
                }
                const nextActions = typeof msg.actionsRemaining === 'number'
                    ? msg.actionsRemaining
                    : Math.max(0, (Network.actionsRemaining ?? 0) - 1);
                Network.actionsRemaining = nextActions;
                this.events.emit('actionsUpdated', {
                    actionsRemaining: nextActions,
                    actionsPerTurn: this.actionsPerTurn
                });
            }
            this.clearTargetHighlights();
            this.actionMode = MODE_MOVE;
            this.events.emit('attackModeEnded');

            // Attacks can destroy units and change visibility.
            this.updateVision();
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

        if (typeof state?.navalVisionRange === 'number') {
            this.navalVisionRange = state.navalVisionRange;
        }
        if (typeof state?.aerialVisionRange === 'number') {
            this.aereoVisionRange = state.aerialVisionRange;
        }
        
        for (const player of state.players) {
            console.log('[MainScene] Processing player', player.playerIndex);
            console.log('[MainScene] Player side:', player.side);
            console.log('[MainScene] Player drones:', player.drones?.length);
            
            const isLocal = (player.playerIndex === Network.playerIndex);
            const color = TEAM_COLORS[player.playerIndex];
            this.drones[player.playerIndex] = [];
            const side = player.side || 'Aereo';
            this.playerSides[player.playerIndex] = side;
            if (isLocal) this.localSide = side;
            this.createCarrierForPlayer(player.playerIndex, side);

            for (const d of player.drones) {
                const hex = this.hexGrid.getNearestCenter(d.x, d.y);
                const droneType = d.droneType ?? side ?? 'Aereo';
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

        this.updateVision();
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
        if (carrier.isMoving) return;
        carrier.isMoving = true;
        this.tweens.add({
            targets: [carrier.sprite, carrier.ring],
            x,
            y,
            duration: 650,
            ease: 'Power2',
            onComplete: () => {
                carrier.isMoving = false;
                this.updateVision();
            }
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
                if (this.selectedDrone.droneType === 'Naval') {
                    this.setManualAttackLine({ x: drone.sprite.x, y: drone.sprite.y });
                }
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
        if (this.selectedDrone.isBusy && this.selectedDrone.isBusy()) return;
        if (this.selectedDrone.droneType === 'Naval' && !this.selectedDrone.canUseMissileAttack()) return;

        this.actionMode = MODE_ATTACK;
        this.hexHighlight.clear();
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
            if (!drone.isAlive() || !this.isDroneVisibleToLocal(drone)) continue;

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

    getVisionRangeForSide(side) {
        return side === 'Aereo' ? this.aereoVisionRange : this.navalVisionRange;
    }

    /** Returns true if the given drone should be visible to the local player. */
    isDroneVisibleToLocal(drone) {
        if (!drone || !drone.isAlive()) return false;
        if (drone.playerIndex === Network.playerIndex) return true;

        const visionRange = this.getVisionRangeForSide(this.localSide);
        if (!visionRange || visionRange <= 0) return false;

        const sources = [];
        const myDrones = this.drones[Network.playerIndex] || [];
        for (const d of myDrones) {
            if (d?.isAlive() && d.sprite) sources.push(d.sprite);
        }

        const myCarrier = this.carriers?.[Network.playerIndex];
        if (myCarrier?.sprite) sources.push(myCarrier.sprite);

        for (const sourceSprite of sources) {
            const distance = this.hexGrid.getHexDistance(
                sourceSprite.x,
                sourceSprite.y,
                drone.sprite.x,
                drone.sprite.y
            );

            if (distance <= visionRange) return true;
        }

        return false;
    }

    /**
     * Check if a world position is occupied by any drone or carrier.
     * Uses a tolerance for near-hexagon positions.
     * @param {number} x - world x coordinate
     * @param {number} y - world y coordinate
     * @param {number} tolerance - distance threshold (pixels)
     * @param {object} ignoreUnit - drone or carrier to ignore in the check (e.g., the unit being moved)
     */
    isPositionOccupied(x, y, tolerance = 15, ignoreUnit = null) {
        // Check all drones
        for (const pi in this.drones) {
            for (const drone of this.drones[pi]) {
                if (!drone?.isAlive() || !drone.sprite) continue;
                if (ignoreUnit && ignoreUnit === drone) continue; // Skip the moving unit
                const dx = drone.sprite.x - x;
                const dy = drone.sprite.y - y;
                const dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < tolerance) {
                    return true;
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
        if (!this.hexGrid) return;
        const localIndex = Network.playerIndex;
        if (typeof localIndex !== 'number') return;

        for (const pi in this.drones) {
            const playerIndex = parseInt(pi);
            for (const drone of this.drones[pi]) {
                if (!drone?.isAlive()) continue;

                if (playerIndex === localIndex) {
                    drone.setLocalVisibility(true);
                    continue;
                }

                const visible = this.isDroneVisibleToLocal(drone);
                drone.setLocalVisibility(visible);
                if (!visible) {
                    drone.setTargetable(false);
                }
            }
        }

        this.updateFogOfWar();
    }

    executeAttack(targetDrone) {
        if (!this.selectedDrone || !this.isMyTurn) return;
        if (this.selectedDrone.hasAttacked) return; // Already attacked this turn
        if (this.selectedDrone.isBusy && this.selectedDrone.isBusy()) return;
        if (this.selectedDrone.droneType === 'Naval' && !this.selectedDrone.canUseMissileAttack()) return;

        const attackerIndex = this.myDrones.indexOf(this.selectedDrone);
        if (attackerIndex < 0) return;

        if (!targetDrone?.sprite) return;

        // Bomb drone must respect its normal range on the client side (server will also validate).
        if (this.selectedDrone.droneType === 'Aereo') {
            const distance = this.hexGrid.getHexDistance(
                this.selectedDrone.sprite.x,
                this.selectedDrone.sprite.y,
                targetDrone.sprite.x,
                targetDrone.sprite.y
            );
            if (distance > (this.selectedDrone.attackRange ?? 0)) return;
        }

        const lineTarget = this.selectedDrone.droneType === 'Naval'
            ? (this.manualTargetHex || targetDrone.sprite)
            : targetDrone.sprite;

        if (typeof this.selectedDrone.queueAttackLock === 'function') {
            this.selectedDrone.queueAttackLock();
        }
        Network.requestAttack(
            attackerIndex,
            targetDrone.playerIndex,
            targetDrone.droneIndex,
            lineTarget.x,
            lineTarget.y
        );
    }

    executeManualAttack() {
        if (!this.manualTargetHex || !this.selectedDrone || !this.isMyTurn) return;
        if (this.selectedDrone.hasAttacked) return;
        if (this.selectedDrone.isBusy && this.selectedDrone.isBusy()) return;
        // Only missile drones can do blind/manual shots.
        if (this.selectedDrone.droneType !== 'Naval') return;
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

    setManualAttackLine(hexCenter) {
        if (!this.selectedDrone || !hexCenter) return;
        const startX = this.selectedDrone.sprite.x;
        const startY = this.selectedDrone.sprite.y;

        const dx = hexCenter.x - startX;
        const dy = hexCenter.y - startY;
        const magnitude = Math.sqrt(dx * dx + dy * dy);
        if (magnitude === 0) return;

        const maxDistancePixels = this.hexGrid.size * Math.sqrt(3) * MAX_MANUAL_ATTACK_DISTANCE;
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
                    const playerIndex = parseInt(pi);
                    if (playerIndex === Network.playerIndex || this.isDroneVisibleToLocal(drone)) {
                        all.push({ drone, playerIndex });
                    }
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
