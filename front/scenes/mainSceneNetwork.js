import Network from '../network/NetworkManager.js';
import { showPlayerLeftOverlay } from './mainSceneOverlays.js';

export function attachNetworkHandlers(scene, options = {}) {
    const modeMove = options.modeMove ?? 'move';

    Network.on('turnStart', (msg) => {
        if (scene.gameFinished) {
            return;
        }
        scene.isMyTurn = (msg.activePlayer === Network.playerIndex);
        scene.actionMode = modeMove;
        scene.clearTargetHighlights();
        scene.clearSelections();
        scene.deployPanelCarrier = null;
        scene.events.emit('deployPanelClose');

        if (scene.isMyTurn) {
            for (const drone of scene.myDrones) {
                drone.hasAttacked = false;
            }
        }

        scene.actionsPerTurn = msg.actionsPerTurn ?? scene.actionsPerTurn;
        scene.events.emit('actionsUpdated', {
            actionsRemaining: msg.actionsRemaining,
            actionsPerTurn: scene.actionsPerTurn
        });

        scene.events.emit('turnChanged', {
            isMyTurn: scene.isMyTurn
        });

        scene.updateVision();
        scene.checkDrawByNoDrones();
    });

    Network.on('moveDrone', (msg) => {
        const drone = scene.drones[msg.playerIndex]?.[msg.droneIndex];
        if (!drone) {
            return;
        }

        if (!drone.deployed && typeof msg.x === 'number' && typeof msg.y === 'number') {
            drone.deployed = true;
        }

        if (typeof msg.x === 'number' && typeof msg.y === 'number') {
            drone.moveTo(msg.x, msg.y);
        }

        if (typeof msg.remainingFuel === 'number') {
            drone.setFuel(msg.remainingFuel);
        }

        const destroyedByFuel = msg.destroyedByFuel || msg.remainingFuel === 0;
        if (destroyedByFuel && drone.isAlive()) {
            if (scene.selectedDrone === drone) {
                scene.selectedDrone.deselect();
                scene.selectedDrone.sprite.clearTint();
                scene.selectedDrone = null;
                scene.clearTargetHighlights();
                scene.actionMode = modeMove;
                scene.events.emit('selectionChanged', { type: null });
            }
            scene.queueDroneDestroyAfterAction(drone, () => drone.sinkAndDestroy());
        }

        if (msg.playerIndex === Network.playerIndex && typeof msg.x === 'number' && typeof msg.y === 'number') {
            scene.hexHighlight.clear();
            const nextActions = Math.max(0, (Network.actionsRemaining ?? 0) - 1);
            Network.actionsRemaining = nextActions;
            scene.events.emit('actionsUpdated', {
                actionsRemaining: nextActions,
                actionsPerTurn: scene.actionsPerTurn
            });
        }

        scene.events.emit('fuelUpdated');

        scene.updateVision();
        scene.checkDrawByNoDrones();
    });

    Network.on('carrierMoved', (msg) => {
        const carrier = scene.carriers[msg.playerIndex];
        if (!carrier || typeof msg.x !== 'number' || typeof msg.y !== 'number') return;
        scene.moveCarrier(carrier, msg.x, msg.y);

        if (typeof msg.actionsRemaining === 'number') {
            Network.actionsRemaining = msg.actionsRemaining;
            scene.events.emit('actionsUpdated', {
                actionsRemaining: msg.actionsRemaining,
                actionsPerTurn: scene.actionsPerTurn
            });
        }
    });

    Network.on('attackResult', (msg) => {
        const targetDrone = scene.drones[msg.targetPlayer]?.[msg.targetDrone];
        const hit = msg.hit !== false;
        const attackerDrone = scene.drones[msg.attackerPlayer]?.[msg.attackerDrone];
        const targetCarrier = scene.carriers?.[msg.targetPlayer];
        const attackerSide = scene.playerSides[msg.attackerPlayer];
        const isNavalAttacker = attackerSide === 'Naval' || attackerDrone?.droneType === 'Naval';
        const isAereoAttacker = attackerSide === 'Aereo' || attackerDrone?.droneType === 'Aereo';

        if (attackerDrone && typeof attackerDrone.clearAttackLock === 'function') {
            attackerDrone.clearAttackLock();
        }

        if (attackerDrone && (targetDrone || (typeof msg.lineX === 'number' && typeof msg.lineY === 'number'))) {
            if (isAereoAttacker) {
                const impactTarget = targetDrone?.sprite || { x: msg.lineX, y: msg.lineY };
                attackerDrone.aereoDronAttack(
                    impactTarget.x,
                    impactTarget.y,
                    msg.attackerX,
                    msg.attackerY,
                    targetDrone ?? null
                );
            } else if (isNavalAttacker) {
                const targetPos = targetDrone?.sprite || { x: msg.lineX, y: msg.lineY };
                attackerDrone.launchMissile(targetPos.x, targetPos.y, targetDrone ?? null);
            } else if (targetDrone) {
                scene.playMissileShot(attackerDrone, targetDrone, hit, msg.lineX, msg.lineY);
            }
        }

        if (targetDrone && hit) {
            targetDrone.takeDamage(msg.damage, msg.remainingHealth);
        }

        if (targetCarrier) {
            if (typeof msg.targetCarrierHealth === 'number') {
                targetCarrier.health = msg.targetCarrierHealth;
            }
            if (typeof msg.targetCarrierDestroyed === 'boolean') {
                targetCarrier.destroyed = msg.targetCarrierDestroyed;
            } else if (typeof targetCarrier.health === 'number') {
                targetCarrier.destroyed = targetCarrier.health <= 0;
            }

            if (targetCarrier.destroyed) {
                targetCarrier.sprite.setVisible(false);
                targetCarrier.ring.setVisible(false);
                if (scene.selectedCarrier === targetCarrier) {
                    scene.selectedCarrier = null;
                }
            }
        }

        if (attackerDrone && msg.attackerDestroyed && attackerDrone.isAlive()) {
            if (scene.selectedDrone === attackerDrone) {
                scene.selectedDrone.deselect();
                scene.selectedDrone = null;
                scene.clearTargetHighlights();
                scene.actionMode = modeMove;
                scene.events.emit('selectionChanged', { type: null });
            }
            scene.queueDroneDestroyAfterAction(attackerDrone, () => attackerDrone.destroy());
        } else if (attackerDrone && msg.attackerRemainingHealth !== undefined) {
            if (attackerDrone.health !== msg.attackerRemainingHealth) {
                attackerDrone.health = msg.attackerRemainingHealth;
                attackerDrone.updateHealthBar();
            }
        }

        if (!hit && msg.attackerPlayer === Network.playerIndex) {
            scene.showCombatMessage('El ataque fallo');
        }
        if (attackerDrone && msg.attackerPlayer === Network.playerIndex) {
            attackerDrone.hasAttacked = true;
            if (typeof msg.attackerAmmo === 'number') {
                attackerDrone.missiles = msg.attackerAmmo;
            } else {
                if (typeof attackerDrone.consumeMissile === 'function') {
                    attackerDrone.consumeMissile();
                }
            }
            const nextActions = typeof msg.actionsRemaining === 'number'
                ? msg.actionsRemaining
                : Math.max(0, (Network.actionsRemaining ?? 0) - 1);
            Network.actionsRemaining = nextActions;
            scene.events.emit('actionsUpdated', {
                actionsRemaining: nextActions,
                actionsPerTurn: scene.actionsPerTurn
            });
        }
        scene.clearTargetHighlights();
        scene.actionMode = modeMove;
        scene.events.emit('attackModeEnded');

        scene.updateVision();
        scene.checkDrawByNoDrones();
    });

    Network.on('playerLeft', () => {
        showPlayerLeftOverlay(scene);
    });

    Network.on('error', (msg) => {
        console.warn('[game] server error:', msg.message);
    });

    Network.on('gameSaved', () => {
        alert('Partida guardada correctamente');
        window.location.href = '/menu';
    });

    Network.on('droneRecalled', (msg) => {
        const { playerIndex, droneIndex, fuel, maxFuel, missiles, actionsRemaining } = msg;
        const drone = scene.drones[playerIndex]?.[droneIndex];
        if (!drone) return;

        drone.deployed = false;
        if (typeof fuel === 'number') drone.fuel = fuel;
        if (typeof maxFuel === 'number') drone.maxFuel = maxFuel;
        if (typeof missiles === 'number') drone.missiles = missiles;

        drone.setLocalVisibility(false);
        drone.deselect();

        if (playerIndex === Network.playerIndex && scene.selectedDrone === drone) {
            scene.clearSelections();
            scene.events.emit('selectionChanged', { type: null });
        }

        if (playerIndex === Network.playerIndex && typeof actionsRemaining === 'number') {
            Network.actionsRemaining = actionsRemaining;
            scene.events.emit('actionsUpdated', {
                actionsRemaining,
                actionsPerTurn: scene.actionsPerTurn
            });
        }

        scene.updateVision();
        scene.events.emit('fuelUpdated');
    });
}
