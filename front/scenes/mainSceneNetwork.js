import Network from '../network/NetworkManager.js';
import { showPlayerLeftOverlay } from './mainSceneOverlays.js';
import { showGameForfeitOverlay } from './mainSceneOverlays.js';

export function attachNetworkHandlers(scene, options = {}) {
    const modeMove = options.modeMove ?? 'move';

    const showGameEndedOverlay = (winnerPlayerIndex, isDraw = false) => {
        if (scene.gameFinished) {
            return;
        }

        scene.gameFinished = true;
        scene.isMyTurn = false;
        scene.stopTurnCountdown?.();
        scene.emitTurnTimerUpdated?.();
        scene.clearTargetHighlights();
        scene.clearSelections();
        scene.events.emit('turnChanged', { isMyTurn: false });

        const winnerText = isDraw
            ? 'La partida terminó en empate'
            : (winnerPlayerIndex === Network.playerIndex ? '¡Ganaste la partida!' : 'Has sido derrotado');

        const camera = scene.cameras.main;
        const panelWidth = 620;
        const panelHeight = 220;
        const panelX = camera.midPoint.x;
        const panelY = camera.midPoint.y;

        scene.add.rectangle(panelX, panelY, panelWidth, panelHeight, 0x04111f, 0.92)
            .setStrokeStyle(4, 0xffdd57, 1)
            .setScrollFactor(0)
            .setDepth(1000);

        scene.add.text(panelX, panelY, `Finaliza partidas para ambos jugadores\n${winnerText}\nVolviendo al menu principal...`, {
            fontSize: '30px',
            fill: '#ffdd57',
            align: 'center',
            fontStyle: 'bold',
            lineSpacing: 10
        }).setOrigin(0.5).setScrollFactor(0).setDepth(1001);

        scene.time.delayedCall(2500, () => {
            window.location.href = '/menu';
        });
        scene.updateVision();
    };

    Network.on('turnStart', (msg) => {
        if (msg.gameFinished) {
            showGameEndedOverlay(msg.winnerPlayerIndex, Boolean(msg.isDraw));
            return;
        }
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

        scene.resetTurnCountdown?.(scene.isMyTurn);

        scene.updateVision();
        scene.checkDrawByNoDrones();
    });

    Network.on('moveDrone', (msg) => {
        if (msg.gameFinished) {
            showGameEndedOverlay(msg.winnerPlayerIndex, Boolean(msg.isDraw));
            return;
        }

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
        const attackerPlayerIndex = Number.isInteger(msg.attackerPlayer)
            ? msg.attackerPlayer
            : attackerDrone?.playerIndex;
        const isLocalAttacker = attackerPlayerIndex === Network.playerIndex || attackerDrone?.isLocal === true;
        const attackerSide = scene.playerSides[msg.attackerPlayer];
        const isNavalAttacker = attackerSide === 'Naval' || attackerDrone?.droneType === 'Naval';
        const isAereoAttacker = attackerSide === 'Aereo' || attackerDrone?.droneType === 'Aereo';
        let combatMessageShown = false;

        const emitCombatMessageOnce = (message) => {
            if (combatMessageShown || !message || !isLocalAttacker) return;
            combatMessageShown = true;
            scene.events.emit('combatMessage', message);
            scene.showCombatMessage?.(message);
        };

        // Miss feedback should appear even if animation events are skipped/interrupted.
        if (!hit) {
            emitCombatMessageOnce('El ataque fallo');
        }

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

        const applyAttackOutcome = () => {
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
                    scene.destroyCarrier?.(targetCarrier, true);
                } else {
                    scene.updateCarrierHealthBar?.(targetCarrier);
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

            if (isLocalAttacker) {
                let combatMessage = 'El ataque fallo';

                if (hit) {
                    const damage = Number.isFinite(msg.damage) ? msg.damage : 0;
                    if (msg.targetCarrierDestroyed) {
                        combatMessage = `Impacto! Portadrones destruido (${damage})`;
                    } else if (targetDrone?.destroyed || (typeof msg.remainingHealth === 'number' && msg.remainingHealth <= 0)) {
                        combatMessage = `Impacto! Dron destruido (${damage})`;
                    } else {
                        combatMessage = `Impacto confirmado (${damage})`;
                    }
                }

                emitCombatMessageOnce(combatMessage);
            }

            if (attackerDrone && msg.attackerPlayer === Network.playerIndex) {
                attackerDrone.hasAttacked = true;
                if (typeof msg.attackerAmmo === 'number') {
                    attackerDrone.missiles = msg.attackerAmmo;
                } else if (typeof attackerDrone.consumeMissile === 'function') {
                    attackerDrone.consumeMissile();
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

            if (msg.gameFinished) {
                showGameEndedOverlay(msg.winnerPlayerIndex, Boolean(msg.isDraw));
                return;
            }

            scene.updateVision();
            scene.checkDrawByNoDrones();
        };

        const hasAnimatedAttack = !!(
            attackerDrone &&
            (targetDrone || (typeof msg.lineX === 'number' && typeof msg.lineY === 'number')) &&
            (isAereoAttacker || isNavalAttacker)
        );

        if (!hasAnimatedAttack) {
            applyAttackOutcome();
            return;
        }

        let outcomeApplied = false;
        const expectedKind = isAereoAttacker ? 'bomb' : 'missile';

        const onImpact = (payload = {}) => {
            if (outcomeApplied) return;
            if (payload.kind !== expectedKind) return;
            outcomeApplied = true;
            scene.events.off('attackAnimImpact', onImpact);
            if (fallbackTimer) fallbackTimer.remove(false);
            applyAttackOutcome();
        };

        const fallbackTimer = scene.time.delayedCall(2300, () => {
            if (outcomeApplied) return;
            outcomeApplied = true;
            scene.events.off('attackAnimImpact', onImpact);
            applyAttackOutcome();
        });

        scene.events.on('attackAnimImpact', onImpact);
    });

    Network.on('playerLeft', () => {
        if (scene.gameFinished) {
            return;
        }
        showPlayerLeftOverlay(scene);
    });

    Network.on('error', (msg) => {
        console.warn('[game] server error:', msg.message);
    });

    Network.on('gameSaved', () => {
        alert('Partida guardada correctamente');
    });

    Network.on('gameForfeited', (msg) => {
        if (scene.gameFinished) {
            return;
        }
        scene.gameFinished = true;
        scene.isMyTurn = false;
        scene.stopTurnCountdown?.();
        scene.emitTurnTimerUpdated?.();
        scene.clearTargetHighlights();
        scene.clearSelections();
        scene.events.emit('turnChanged', { isMyTurn: false });

        const isLocalForfeiter = msg.forfeitingPlayerIndex === Network.playerIndex;
        showGameForfeitOverlay(scene, isLocalForfeiter);
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
