import Minimap from '../ui/Minimap.js';
import SideImpactView from '../ui/SideImpactView.js';
import networkManager from '../network/NetworkManager.js';
import { getGameConfig } from '../shared/gameConfig.js';

const TEAM_COLORS = [0x00ff00, 0xff4444];

/**
 * HudScene — overlay for UI elements (minimap, turn indicator, action buttons).
 * Runs in parallel with MainScene; not affected by camera scroll.
 */
export default class HudScene extends Phaser.Scene {
    constructor() {
        super('HudScene');
        this.hudMode = 'game';
        const runtimeConfig = getGameConfig();
        this.isMyTurn = false;
        this.gameStarted = false;
        this.actionsRemaining = 0;
        this.actionsPerTurn = runtimeConfig.actionsPerTurn;
        this.selectionData = null;
        this.forfeitPending = false;
        this.forfeitRedirectFallback = null;
        /** Phaser objects comprising the deploy panel */
        this.deployPanelElements = [];
    }

    create(data = {}) {
        this.hudMode = data?.mode === 'lobby' ? 'lobby' : 'game';
        this.createBackButton();

        if (this.hudMode === 'lobby') {
            this._onLobbyResize = (gameSize) => this.layoutBackButton(gameSize.width, gameSize.height);
            this.scale.on('resize', this._onLobbyResize);
            this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
                if (this._onLobbyResize) {
                    this.scale.off('resize', this._onLobbyResize);
                }
            });
            return;
        }

        const radius = 80;
        const margin = 20;
        this.minimap = new Minimap(
            this,
            this.scale.width - radius - margin,
            this.scale.height - radius - margin,
            radius
        );

        // Side impact view (bottom-left, opposite minimap). Only visible during attacks.
        const sideViewW = 300;
        const sideViewH = 200;
        // Reserve space for action buttons (40px height) so we don't overlap.
        const reservedBottom = margin + 40 + 10;
        this.sideImpactView = new SideImpactView(
            this,
            margin + sideViewW / 2,
            this.scale.height - reservedBottom - sideViewH / 2,
            sideViewW,
            sideViewH
        );

        // Status/Turn indicator text (centered)
        this.turnText = this.add.text(this.scale.width / 2, 20, 'Conectando...', {
            fontSize: '20px',
            fill: '#ffffff',
            backgroundColor: '#000000db',
            padding: { x: 12, y: 6 }
        }).setOrigin(0.5, 0);

        this.forfeitStatusText = this.add.text(this.scale.width / 2, 62, '', {
            fontSize: '16px',
            fill: '#ffd166',
            backgroundColor: '#2b1f00dd',
            padding: { x: 10, y: 5 }
        }).setOrigin(0.5, 0).setVisible(false);

        // Drone info text (shows selected drone's available actions)
        this.droneInfoText = this.add.text(this.scale.width / 2, 55, '', {
            fontSize: '14px',
            fill: '#cccccc',
            backgroundColor: '#000000bb',
        }).setOrigin(0.5, 0);

        this.fuelListText = this.add.text(20, 20, '', {
            fontSize: '13px',
            fill: '#ffffff',
            backgroundColor: '#00000088',
            padding: { x: 8, y: 6 }
        }).setOrigin(0, 0);

        // Action buttons container (bottom center)
        this.createActionButtons();

        // Listen for events from MainScene
        const mainScene = this.scene.get('MainScene');
        
        mainScene.events.on('statusChanged', this.onStatusChanged, this);
        mainScene.events.on('gameStarted', this.onGameStarted, this);
        mainScene.events.on('turnChanged', this.onTurnChanged, this);
        mainScene.events.on('actionsUpdated', this.onActionsUpdated, this);
        mainScene.events.on('attackModeEnded', this.deselectAttackButton, this);
        mainScene.events.on('selectionChanged', this.onSelectionChanged, this);
        mainScene.events.on('deployPanelOpen', this.onDeployPanelOpen, this);
        mainScene.events.on('deployPanelClose', this.onDeployPanelClose, this);
        mainScene.events.on('combatMessage', this.onCombatMessage, this);

        // Attack side-view events
        this._onAttackAnimStart = (payload) => this.sideImpactView?.onAttackStart(payload);
        this._onAttackAnimImpact = (payload) => this.sideImpactView?.onAttackImpact(payload);
        this._onAttackAnimEnd = (payload) => this.sideImpactView?.onAttackEnd(payload);

        mainScene.events.on('attackAnimStart', this._onAttackAnimStart, this);
        mainScene.events.on('attackAnimImpact', this._onAttackAnimImpact, this);
        mainScene.events.on('attackAnimEnd', this._onAttackAnimEnd, this);

        // Keep UI anchored on resize
        this.scale.on('resize', (gameSize) => {
            const w = gameSize.width;
            const h = gameSize.height;
            if (this.sideImpactView) {
                this.sideImpactView.reposition(margin + sideViewW / 2, h - reservedBottom - sideViewH / 2);
            }
            if (this.forfeitStatusText) {
                this.forfeitStatusText.setPosition(w / 2, 62);
            }
            this.layoutBackButton(w, h);
            this.layoutActionButtons();
            this.layoutSaveButton();
        });

        this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
            if (!mainScene) return;
            if (this._onAttackAnimStart) mainScene.events.off('attackAnimStart', this._onAttackAnimStart, this);
            if (this._onAttackAnimImpact) mainScene.events.off('attackAnimImpact', this._onAttackAnimImpact, this);
            if (this._onAttackAnimEnd) mainScene.events.off('attackAnimEnd', this._onAttackAnimEnd, this);
            mainScene.events.off('combatMessage', this.onCombatMessage, this);
            if (this._onGameForfeited) networkManager.off('gameForfeited', this._onGameForfeited);
            if (this._onNetworkError) networkManager.off('error', this._onNetworkError);
        });

        this._onGameForfeited = () => {
            this.clearForfeitPending();
        };
        this._onNetworkError = () => {
            this.clearForfeitPending();
        };
        networkManager.on('gameForfeited', this._onGameForfeited);
        networkManager.on('error', this._onNetworkError);
    }

    createBackButton() {
        const isLobby = this.hudMode === 'lobby';
        this.backBtnConfig = {
            margin: 20,
            width: 120,
            height: 40,
            radius: 6,
            baseColor: isLobby ? 0x1b7a3a : 0x2476e2,
            hoverColor: isLobby ? 0x20a24b : 0x2f90ff,
            textColor: isLobby ? '#d9ffe3' : '#e2e9ea'
        };

        this.backBtnBg = this.add.graphics();
        this.backBtnBg.setDepth(340);

        this.backBtnText = this.add.text(0, 0, 'Volver', {
            fontSize: '16px',
            fill: this.backBtnConfig.textColor,
            fontFamily: '"Orbitron", "Share Tech Mono", monospace'
        }).setOrigin(0.5);
        this.backBtnText.setDepth(342);
        this.backBtnText.setScrollFactor(0);

        this.backBtn = this.add.rectangle(0, 0, this.backBtnConfig.width, this.backBtnConfig.height, 0x000000, 0.001)
            .setInteractive({ useHandCursor: true });
        this.backBtn.setDepth(343);

        this.backBtn.on('pointerover', () => {
            this.drawRoundedButton(
                this.backBtnBg,
                this.backBtn.x - this.backBtnConfig.width / 2,
                this.backBtn.y - this.backBtnConfig.height / 2,
                this.backBtnConfig.width,
                this.backBtnConfig.height,
                this.backBtnConfig.radius,
                this.backBtnConfig.hoverColor,
                0.95
            );
        });

        this.backBtn.on('pointerout', () => {
            this.layoutBackButton();
        });

        this.backBtn.on('pointerup', () => {
            if (this._backNavigationPending) return;
            this._backNavigationPending = true;

            if (this.hudMode === 'game') {
                this.leaveGameAndReturnToMenu();
                return;
            }

            window.location.href = '/menu';
        });

        this.layoutBackButton();
    }

    layoutBackButton(width = this.scale.width, height = this.scale.height) {
        if (!this.backBtn || !this.backBtnBg || !this.backBtnText || !this.backBtnConfig) return;

        const x = this.backBtnConfig.margin + this.backBtnConfig.width / 2;
        const y = height - this.backBtnConfig.margin - this.backBtnConfig.height / 2;

        this.drawRoundedButton(
            this.backBtnBg,
            x - this.backBtnConfig.width / 2,
            y - this.backBtnConfig.height / 2,
            this.backBtnConfig.width,
            this.backBtnConfig.height,
            this.backBtnConfig.radius,
            this.backBtnConfig.baseColor,
            0.85
        );

        this.backBtn.setPosition(x, y);
        this.backBtnText.setPosition(x, y);
    }

    leaveGameAndReturnToMenu() {
        if (this._leaveInProgress) return;
        this._leaveInProgress = true;

        const cleanupLeaveHandlers = () => {
            if (this._leaveDisconnectHandler) {
                networkManager.off('disconnect', this._leaveDisconnectHandler);
                this._leaveDisconnectHandler = null;
            }
            if (this._leaveFallbackTimer) {
                this._leaveFallbackTimer.remove(false);
                this._leaveFallbackTimer = null;
            }
        };

        this._leaveDisconnectHandler = () => {
            cleanupLeaveHandlers();
            window.location.href = '/menu';
        };

        networkManager.on('disconnect', this._leaveDisconnectHandler);

        // Fallback in case the close event is delayed or dropped.
        this._leaveFallbackTimer = this.time.delayedCall(3500, () => {
            cleanupLeaveHandlers();
            window.location.href = '/menu';
        });

        networkManager.leaveGame();
    }

    createActionButtons() {
        const margin = 20;
        const btnWidth = 140;
        const btnHeight = 40;
        const btnRadius = 18;
        const gap = 12;
        this.actionBtnConfig = { margin, btnWidth, btnHeight, btnRadius, gap };
        this.saveBtnConfig = { margin, btnWidth, btnHeight, btnRadius };
        const { startX, y } = this.getActionButtonLayout();

        // Colors (radar theme)
        this.grayColor = 0x2a3232;
        this.attackActiveColor = 0x4a0f0f; // Reddish
        this.recallActiveColor = 0x0f4a0f; // Greenish
        this.endTurnActiveColor = 0x0f1c2a; // Bluish
        this.radarColors = {
            attackFrame: 0xff4444, // Red
            attackGlow: 0xff6666,
            recallFrame: 0x44ff44, // Green
            recallGlow: 0x66ff66,
            endTurnFrame: 0x4ab3ff, // Blue
            endTurnGlow: 0x6ac8ff,
            disabledFrame: 0x2d3b3b,
            disabledGlow: 0x0f1a1a
        };
        this.btnTextColor = '#ffffff';
        this.btnTextDisabledColor = '#5f6f6b';
        this.btnFontFamily = '"Orbitron", "Share Tech Mono", monospace';

        // Attack button state
        this.attackSelected = false;
        this.attackHovered = false;
        this.recallHovered = false;
        this.endTurnHovered = false;

        const btnDepth = 300;

        // Attack button background
        this.attackBtnGlow = this.add.graphics();
        this.attackBtnBg = this.add.graphics();
        this.attackBtnFrame = this.add.graphics();
        this.drawRadarButton(this.attackBtnGlow, this.attackBtnBg, this.attackBtnFrame,
            startX - btnWidth / 2, y - btnHeight / 2, btnWidth, btnHeight, btnRadius,
            this.grayColor, this.radarColors.attackFrame, this.radarColors.attackGlow, 1, 0.2);
        this.attackBtnGlow.setDepth(btnDepth);
        this.attackBtnBg.setDepth(btnDepth + 1);
        this.attackBtnFrame.setDepth(btnDepth + 2);

        // Attack button text
        this.attackBtnText = this.add.text(startX, y, 'ATACAR', {
            fontSize: '15px',
            fill: '#ffffff',
            fontFamily: this.btnFontFamily,
            stroke: '#000000',
            strokeThickness: 3
        }).setOrigin(0.5);
        this.attackBtnText.setDepth(btnDepth + 10);
        this.attackBtnText.setScrollFactor(0);

        // Attack button hitbox
        this.attackBtn = this.add.rectangle(startX, y, btnWidth, btnHeight, 0x000000, 0.001)
            .setInteractive({ useHandCursor: true });
        this.attackBtn.setDepth(btnDepth + 4);

        this.attackBtn.on('pointerup', () => {
            const mainScene = this.scene.get('MainScene');
            // Only trigger if it wasn't a camera drag in the main scene
            if (mainScene?.isDragging) return;

            // Don't allow attack mode if drone can't attack
            if (!mainScene?.selectedDrone || mainScene.selectedDrone.hasAttacked) return;

            this.attackSelected = !this.attackSelected;
            this.updateButtonStates();
            if (this.attackSelected) {
                mainScene.enterAttackMode();
            } else {
                mainScene.cancelAttackMode();
            }
        });
        this.attackBtn.on('pointerover', () => {
            this.attackHovered = true;
        });
        this.attackBtn.on('pointerout', () => {
            this.attackHovered = false;
        });

        // Recall (Replegar) button
        const recallX = startX + btnWidth + gap;
        this.recallBtnGlow = this.add.graphics();
        this.recallBtnBg = this.add.graphics();
        this.recallBtnFrame = this.add.graphics();
        this.drawRadarButton(this.recallBtnGlow, this.recallBtnBg, this.recallBtnFrame,
            recallX - btnWidth / 2, y - btnHeight / 2, btnWidth, btnHeight, btnRadius,
            this.grayColor, this.radarColors.recallFrame, this.radarColors.recallGlow, 1, 0.2);

        this.recallBtnText = this.add.text(recallX, y, 'REPLEGAR', {
            fontSize: '15px',
            fill: '#ffffff',
            fontFamily: this.btnFontFamily,
            stroke: '#000000',
            strokeThickness: 3
        }).setOrigin(0.5);
        this.recallBtnGlow.setDepth(btnDepth);
        this.recallBtnBg.setDepth(btnDepth + 1);
        this.recallBtnFrame.setDepth(btnDepth + 2);
        this.recallBtnText.setDepth(btnDepth + 10);
        this.recallBtnText.setScrollFactor(0);

        this.recallBtn = this.add.rectangle(recallX, y, btnWidth, btnHeight, 0x000000, 0.001)
            .setInteractive({ useHandCursor: true });
        this.recallBtn.setDepth(btnDepth + 4);

        this.recallBtn.on('pointerup', () => {
            const mainScene = this.scene.get('MainScene');
            if (mainScene?.isDragging) return;
            if (!mainScene?.canRecallSelectedDrone?.()) return;
            mainScene.recallSelectedDrone();
        });
        this.recallBtn.on('pointerover', () => {
            this.recallHovered = true;
        });
        this.recallBtn.on('pointerout', () => {
            this.recallHovered = false;
        });

        // End Turn button background
        const endTurnX = recallX + btnWidth + gap;
        this.endTurnBtnGlow = this.add.graphics();
        this.endTurnBtnBg = this.add.graphics();
        this.endTurnBtnFrame = this.add.graphics();
        this.drawRadarButton(this.endTurnBtnGlow, this.endTurnBtnBg, this.endTurnBtnFrame,
            endTurnX - btnWidth / 2, y - btnHeight / 2, btnWidth, btnHeight, btnRadius,
            this.endTurnActiveColor, this.radarColors.endTurnFrame, this.radarColors.endTurnGlow, 1, 0.2);

        // End Turn button text
        this.endTurnBtnText = this.add.text(endTurnX, y, 'FIN TURNO', {
            fontSize: '15px',
            fill: '#ffffff',
            fontFamily: this.btnFontFamily,
            stroke: '#000000',
            strokeThickness: 3
        }).setOrigin(0.5);
        this.endTurnBtnGlow.setDepth(btnDepth);
        this.endTurnBtnBg.setDepth(btnDepth + 1);
        this.endTurnBtnFrame.setDepth(btnDepth + 2);
        this.endTurnBtnText.setDepth(btnDepth + 10);
        this.endTurnBtnText.setScrollFactor(0);

        // End Turn button hitbox
        this.endTurnBtn = this.add.rectangle(endTurnX, y, btnWidth, btnHeight, 0x000000, 0.001)
            .setInteractive({ useHandCursor: true });
        this.endTurnBtn.setDepth(btnDepth + 4);

        this.endTurnBtn.on('pointerup', () => {
            const mainScene = this.scene.get('MainScene');
            if (mainScene?.isDragging) return;
            mainScene.endTurn();
        });
        this.endTurnBtn.on('pointerover', () => {
            this.endTurnHovered = true;
        });
        this.endTurnBtn.on('pointerout', () => {
            this.endTurnHovered = false;
        });

        // Save & Exit button (always visible during game, positioned at top-right)
        this.saveExitActiveColor = 0x995500;
        this.forfeitActiveColor = 0x8f1f1f;
        const saveExitX = this.scale.width - margin - btnWidth / 2;
        const saveExitY = margin + btnHeight / 2;

        this.saveExitBtnBg = this.add.graphics();
        this.drawRoundedButton(this.saveExitBtnBg, saveExitX - btnWidth / 2, saveExitY - btnHeight / 2, btnWidth, btnHeight, btnRadius, this.saveExitActiveColor);

        this.saveExitBtnText = this.add.text(saveExitX, saveExitY, '💾 Guardar', {
            fontSize: '16px',
            fill: '#ffffff'
        }).setOrigin(0.5);

        this.saveExitBtn = this.add.rectangle(saveExitX, saveExitY, btnWidth, btnHeight, 0x000000, 0.001)
            .setInteractive({ useHandCursor: true });

        this.saveExitBtn.on('pointerup', () => {
            if (confirm('¿Guardar y salir de la partida?')) {
                networkManager.saveAndExit();
            }
        });

        const forfeitX = saveExitX - btnWidth - gap;
        const forfeitY = saveExitY;

        this.forfeitBtnBg = this.add.graphics();
        this.drawRoundedButton(this.forfeitBtnBg, forfeitX - btnWidth / 2, forfeitY - btnHeight / 2, btnWidth, btnHeight, btnRadius, this.forfeitActiveColor);

        this.forfeitBtnText = this.add.text(forfeitX, forfeitY, 'Abandonar', {
            fontSize: '16px',
            fill: '#ffffff'
        }).setOrigin(0.5);

        this.forfeitBtn = this.add.rectangle(forfeitX, forfeitY, btnWidth, btnHeight, 0x000000, 0.001)
            .setInteractive({ useHandCursor: true });

        this.forfeitBtn.on('pointerup', () => {
            if (this.forfeitPending) return;
            const message = 'Esta seguro que desea abandona la partida?, le sera contado como derrota';
            if (confirm(message)) {
                this.forfeitPending = true;
                this.showForfeitPendingStatus();
                if (this.forfeitRedirectFallback) {
                    this.forfeitRedirectFallback.remove(false);
                }
                this.forfeitRedirectFallback = this.time.delayedCall(4500, () => {
                    window.location.href = '/menu';
                });
                networkManager.forfeitGame();
            }
        });

        // Save button hidden until game starts
        this.setSaveButtonVisible(false);

        // Initial layout
        this.layoutActionButtons();
        this.layoutSaveButton();

        // Initially hidden
        this.setButtonsVisible(false);
    }

    getActionButtonLayout() {
        const { margin, btnWidth, btnHeight, gap } = this.actionBtnConfig || {
            margin: 20,
            btnWidth: 120,
            btnHeight: 40,
            gap: 10
        };
        const totalWidth = btnWidth * 3 + gap * 2;
        const startX = (this.scale.width - totalWidth) / 2 + btnWidth / 2;
        const recallX = startX + btnWidth + gap;
        const endTurnX = recallX + btnWidth + gap;
        const y = this.scale.height - margin - btnHeight / 2;
        return { startX, recallX, endTurnX, y, btnWidth, btnHeight };
    }

    layoutActionButtons() {
        if (!this.attackBtn || !this.endTurnBtn) return;
        const layout = this.getActionButtonLayout();
        this.actionBtnLayout = layout;

        this.attackBtn.setPosition(layout.startX, layout.y);
        this.attackBtnText.setPosition(layout.startX, layout.y);
        if (this.recallBtn) {
            this.recallBtn.setPosition(layout.recallX, layout.y);
            this.recallBtnText.setPosition(layout.recallX, layout.y);
        }
        this.endTurnBtn.setPosition(layout.endTurnX, layout.y);
        this.endTurnBtnText.setPosition(layout.endTurnX, layout.y);

        this.updateButtonStates();
    }

    layoutSaveButton() {
        if (!this.saveExitBtn) return;
        const { margin, btnWidth, btnHeight, btnRadius } = this.saveBtnConfig || {
            margin: 20,
            btnWidth: 120,
            btnHeight: 40,
            btnRadius: 20
        };
        const saveExitX = this.scale.width - margin - btnWidth / 2;
        const saveExitY = margin + btnHeight / 2;
        const gap = this.actionBtnConfig?.gap ?? 10;
        const forfeitX = saveExitX - btnWidth - gap;
        const forfeitY = saveExitY;

        this.drawRoundedButton(
            this.saveExitBtnBg,
            saveExitX - btnWidth / 2,
            saveExitY - btnHeight / 2,
            btnWidth,
            btnHeight,
            btnRadius,
            this.saveExitActiveColor
        );
        this.saveExitBtn.setPosition(saveExitX, saveExitY);
        this.saveExitBtnText.setPosition(saveExitX, saveExitY);

        if (this.forfeitBtnBg) {
            this.drawRoundedButton(
                this.forfeitBtnBg,
                forfeitX - btnWidth / 2,
                forfeitY - btnHeight / 2,
                btnWidth,
                btnHeight,
                btnRadius,
                this.forfeitActiveColor
            );
        }
        if (this.forfeitBtn) {
            this.forfeitBtn.setPosition(forfeitX, forfeitY);
        }
        if (this.forfeitBtnText) {
            this.forfeitBtnText.setPosition(forfeitX, forfeitY);
        }
    }

    drawRoundedButton(graphics, x, y, width, height, radius, color, alpha = 1) {
        graphics.clear();
        graphics.fillStyle(color, alpha);
        graphics.fillRoundedRect(x, y, width, height, radius);
    }

    drawRadarButton(glow, base, frame, x, y, width, height, radius, baseColor, frameColor, glowColor, alpha = 1, glowAlpha = 0.25) {
        if (glow) {
            glow.clear();
            glow.fillStyle(glowColor, glowAlpha);
            glow.fillRoundedRect(x - 2, y - 2, width + 4, height + 4, radius + 2);
        }

        if (base) {
            base.clear();
            base.fillStyle(baseColor, alpha);
            base.fillRoundedRect(x, y, width, height, radius);
        }

        if (frame) {
            frame.clear();
            frame.lineStyle(2, frameColor, Math.min(1, alpha + 0.15));
            frame.strokeRoundedRect(x, y, width, height, radius);
            frame.lineStyle(1, frameColor, 0.35);
            frame.strokeRoundedRect(x + 2, y + 2, width - 4, height - 4, Math.max(4, radius - 2));
        }
    }

    onCombatMessage(message) {
        if (!message) return;

        if (this.combatMessageTween) {
            this.combatMessageTween.stop();
            this.combatMessageTween = null;
        }
        if (this.combatMessageText) {
            this.combatMessageText.destroy();
            this.combatMessageText = null;
        }

        const text = this.add.text(this.scale.width / 2, this.scale.height * 0.22, message, {
            fontSize: '34px',
            fill: '#ffd54f',
            fontStyle: 'bold',
            fontFamily: this.btnFontFamily || 'monospace',
            stroke: '#000000',
            strokeThickness: 6,
            align: 'center'
        }).setOrigin(0.5).setScrollFactor(0).setDepth(1100);

        this.combatMessageText = text;
        this.combatMessageTween = this.tweens.add({
            targets: text,
            alpha: { from: 1, to: 0 },
            y: text.y - 28,
            duration: 1500,
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

    updateButtonStates() {
        // Find main scene
        const mainScene = this.scene.get('MainScene');
        // Use either the internal isMyTurn or the one from MainScene
        const isActuallyMyTurn = mainScene ? mainScene.isMyTurn : this.isMyTurn;

        if (!isActuallyMyTurn) {
            this.setButtonsVisible(false);
            return;
        }
        this.setButtonsVisible(true);

        const layout = this.actionBtnLayout || this.getActionButtonLayout();
        const { startX, endTurnX, y, btnWidth, btnHeight } = layout;
        const btnRadius = this.actionBtnConfig?.btnRadius ?? 20;

        // Check if selected drone can attack
        const selectedDrone = mainScene?.selectedDrone;
        const hasAmmoForAttack = !selectedDrone
            ? true
            : ((selectedDrone.missiles ?? 0) > 0) && (selectedDrone.droneType !== 'Naval' || selectedDrone.canUseMissileAttack());
        const canAttack = selectedDrone && !selectedDrone.hasAttacked && hasAmmoForAttack;

        // Update attack button color and alpha
        let attackBase = this.grayColor;
        let attackFrame = this.radarColors.disabledFrame;
        let attackGlow = this.radarColors.disabledGlow;
        let attackAlpha = 0.55;
        let textColor = this.btnTextDisabledColor;
        let glowAlpha = 0.15;

        if (canAttack) {
            attackBase = this.attackSelected ? this.attackActiveColor : this.grayColor;
            attackFrame = this.radarColors.attackFrame;
            attackGlow = this.radarColors.attackGlow;
            attackAlpha = 1;
            textColor = this.btnTextColor;
            glowAlpha = this.attackSelected ? 0.4 : (this.attackHovered ? 0.35 : 0.3);
        }

        this.drawRadarButton(
            this.attackBtnGlow,
            this.attackBtnBg,
            this.attackBtnFrame,
            startX - btnWidth / 2,
            y - btnHeight / 2,
            btnWidth,
            btnHeight,
            btnRadius,
            attackBase,
            attackFrame,
            attackGlow,
            attackAlpha,
            glowAlpha
        );
        this.attackBtnText.setAlpha(1);
        this.attackBtnText.setVisible(true);

        // End turn is always available
        this.drawRadarButton(
            this.endTurnBtnGlow,
            this.endTurnBtnBg,
            this.endTurnBtnFrame,
            endTurnX - btnWidth / 2,
            y - btnHeight / 2,
            btnWidth,
            btnHeight,
            btnRadius,
            this.endTurnActiveColor,
            this.radarColors.endTurnFrame,
            this.radarColors.endTurnGlow,
            1,
            this.endTurnHovered ? 0.35 : 0.2
        );
        this.endTurnBtnText.setColor(this.btnTextColor);
        this.endTurnBtnText.setAlpha(1);

        // Recall button — enabled only when selected drone is deployed and within 2 hexes of carrier
        if (this.recallBtnBg) {
            const canRecall = !!(mainScene?.canRecallSelectedDrone?.());
            const recallBase = canRecall ? this.recallActiveColor : this.grayColor;
            const recallAlpha = canRecall ? 1 : 0.55;
            const recallFrame = canRecall ? this.radarColors.recallFrame : this.radarColors.disabledFrame;
            const recallGlow = canRecall ? this.radarColors.recallGlow : this.radarColors.disabledGlow;
            const recallGlowAlpha = canRecall ? (this.recallHovered ? 0.35 : 0.2) : 0.12;
            const recallX = layout.recallX ?? (startX + btnWidth + (this.actionBtnConfig?.gap ?? 10));
            this.drawRadarButton(
                this.recallBtnGlow,
                this.recallBtnBg,
                this.recallBtnFrame,
                recallX - btnWidth / 2,
                y - btnHeight / 2,
                btnWidth,
                btnHeight,
                btnRadius,
                recallBase,
                recallFrame,
                recallGlow,
                recallAlpha,
                recallGlowAlpha
            );
            if (this.recallBtnText) {
                this.recallBtnText.setColor(canRecall ? this.btnTextColor : this.btnTextDisabledColor);
                this.recallBtnText.setAlpha(1);
            }
        }

        this.updateAttackPulse(canAttack && !this.attackSelected);
    }

    setButtonsVisible(visible) {
        const btns = [
            this.attackBtn, this.attackBtnText, this.attackBtnBg, this.attackBtnGlow, this.attackBtnFrame,
            this.recallBtn, this.recallBtnText, this.recallBtnBg, this.recallBtnGlow, this.recallBtnFrame,
            this.endTurnBtn, this.endTurnBtnText, this.endTurnBtnBg, this.endTurnBtnGlow, this.endTurnBtnFrame
        ];
        btns.forEach(btn => {
            if (btn) {
                btn.setVisible(visible);
                if (visible && btn.setAlpha) btn.setAlpha(1);
            }
        });
    }

    updateAttackPulse(shouldPulse) {
        if (!this.attackBtnGlow) return;
        if (shouldPulse) {
            if (this.attackPulseTween) return;
            this.attackPulseTween = this.tweens.add({
                targets: this.attackBtnGlow,
                alpha: { from: 0.35, to: 0.85 },
                duration: 900,
                yoyo: true,
                repeat: -1,
                ease: 'Sine.easeInOut'
            });
        } else if (this.attackPulseTween) {
            this.attackPulseTween.stop();
            this.attackPulseTween = null;
            this.attackBtnGlow.setAlpha(1);
        }
    }

    deselectAttackButton() {
        this.attackSelected = false;
        this.updateButtonStates();
    }

    /**
     * Received when MainScene wants to show the deploy panel.
     * data = { carrier, drones: [{ droneIndex, fuel, maxFuel, missiles, droneType }] }
     */
    onDeployPanelOpen(data) {
        this.onDeployPanelClose(); // clear any existing panel first

        const { drones } = data;
        if (!drones || drones.length === 0) return;

        const mainScene = this.scene.get('MainScene');

        // Layout constants
        const panelX = 20;
        const panelY = 60;
        const panelW = 330;
        const rowH = 36;
        const padding = 10;
        const titleH = 32;
        const btnW = 90;
        const btnH = 26;
        const panelH = padding + titleH + drones.length * rowH + padding;

        const els = this.deployPanelElements;

        // Panel background
        const bg = this.add.graphics();
        bg.fillStyle(0x0d1b2a, 0.93);
        bg.fillRoundedRect(panelX, panelY, panelW, panelH, 10);
        bg.lineStyle(1, 0x3a7bd5, 0.8);
        bg.strokeRoundedRect(panelX, panelY, panelW, panelH, 10);
        bg.setDepth(200);
        els.push(bg);

        // Title
        const title = this.add.text(
            panelX + panelW / 2,
            panelY + padding,
            '🚁 Desplegar Drones',
            { fontSize: '14px', fill: '#7ecfff', fontStyle: 'bold' }
        ).setOrigin(0.5, 0).setDepth(201);
        els.push(title);

        // Close (✕) button in title bar
        const closeBg = this.add.graphics();
        closeBg.fillStyle(0x553333, 1);
        closeBg.fillRoundedRect(panelX + panelW - 28, panelY + 7, 20, 20, 4);
        closeBg.setDepth(201);
        els.push(closeBg);

        const closeText = this.add.text(panelX + panelW - 18, panelY + 17, '✕', {
            fontSize: '12px', fill: '#ffaaaa'
        }).setOrigin(0.5).setDepth(202);
        els.push(closeText);

        const closeHit = this.add.rectangle(panelX + panelW - 18, panelY + 17, 22, 22, 0x000000, 0.001)
            .setInteractive({ useHandCursor: true }).setDepth(203);
        closeHit.on('pointerup', (ptr) => {
            ptr.event.stopPropagation();
            mainScene.events.emit('deployPanelClose');
        });
        els.push(closeHit);

        // Drone rows
        for (let i = 0; i < drones.length; i++) {
            const d = drones[i];
            const rowY = panelY + padding + titleH + i * rowH;

            // Row separator (except first)
            if (i > 0) {
                const sep = this.add.graphics();
                sep.lineStyle(1, 0x2a4a6a, 0.6);
                sep.beginPath();
                sep.moveTo(panelX + padding, rowY);
                sep.lineTo(panelX + panelW - padding, rowY);
                sep.strokePath();
                sep.setDepth(201);
                els.push(sep);
            }

            // Info text
            const ammoStr = d.droneType === 'Naval' ? `  Mis: ${d.missiles ?? 0}` : '';
            const infoLabel = this.add.text(
                panelX + padding,
                rowY + rowH / 2,
                `#${d.droneIndex + 1}  Comb: ${d.fuel}/${d.maxFuel}${ammoStr}`,
                { fontSize: '12px', fill: '#dddddd' }
            ).setOrigin(0, 0.5).setDepth(201);
            els.push(infoLabel);

            // Deploy button background
            const btnX = panelX + panelW - padding - btnW;
            const btnY = rowY + (rowH - btnH) / 2;
            const btnBg = this.add.graphics();
            btnBg.fillStyle(0x1a7a40, 1);
            btnBg.fillRoundedRect(btnX, btnY, btnW, btnH, 6);
            btnBg.setDepth(201);
            els.push(btnBg);

            const btnLabel = this.add.text(btnX + btnW / 2, rowY + rowH / 2, 'Desplegar', {
                fontSize: '12px', fill: '#ffffff'
            }).setOrigin(0.5).setDepth(202);
            els.push(btnLabel);

            // Capture droneIndex in closure
            const droneIdx = d.droneIndex;
            const hit = this.add.rectangle(btnX + btnW / 2, rowY + rowH / 2, btnW, btnH, 0x000000, 0.001)
                .setInteractive({ useHandCursor: true }).setDepth(203);
            hit.on('pointerup', (ptr) => {
                if (mainScene?.isDragging) return;
                ptr.event.stopPropagation();
                mainScene.deployDroneFromPanel(droneIdx);
            });
            hit.on('pointerover', () => {
                btnBg.clear();
                btnBg.fillStyle(0x27ae60, 1);
                btnBg.fillRoundedRect(btnX, btnY, btnW, btnH, 6);
            });
            hit.on('pointerout', () => {
                btnBg.clear();
                btnBg.fillStyle(0x1a7a40, 1);
                btnBg.fillRoundedRect(btnX, btnY, btnW, btnH, 6);
            });
            els.push(hit);
        }

        // Hide fuel list while panel is open to avoid overlap
        if (this.fuelListText) this.fuelListText.setVisible(false);
    }

    /** Remove the deploy panel from the screen. */
    onDeployPanelClose() {
        for (const el of this.deployPanelElements) {
            if (el && el.active !== false) {
                el.destroy();
            }
        }
        this.deployPanelElements = [];
        if (this.fuelListText) this.fuelListText.setVisible(true);
    }


    onSelectionChanged(selectionData) {
        this.selectionData = selectionData;
    }

    onStatusChanged(status) {
        this.turnText.setText(status);
    }

    onGameStarted() {
        this.gameStarted = true;
        this.setSaveButtonVisible(true);
    }

    onTurnChanged({ isMyTurn }) {
        this.isMyTurn = isMyTurn;

        if (isMyTurn) {
            this.turnText.setText(`Tu turno (${this.actionsRemaining}/${this.actionsPerTurn} acciones)`);
            this.turnText.setStyle({ fill: '#00ff00' });
            this.setButtonsVisible(true);
            this.updateButtonStates(); // Force refresh of colors when turn starts
        } else {
            this.turnText.setText('Turno del oponente');
            this.turnText.setStyle({ fill: '#fc0f0f' });
            this.droneInfoText.setText('');
            this.setButtonsVisible(false);
        }
    }


    onActionsUpdated({ actionsRemaining, actionsPerTurn }) {
        this.actionsRemaining = actionsRemaining;
        if (typeof actionsPerTurn === 'number' && actionsPerTurn > 0) {
            this.actionsPerTurn = actionsPerTurn;
        }

        if (this.isMyTurn) {
            this.turnText.setText(`Tu turno (${this.actionsRemaining}/${this.actionsPerTurn} acciones)`);
        }
    }

    setButtonsVisible(visible) {
        this.attackBtn.setVisible(visible);
        if (this.attackBtnGlow) this.attackBtnGlow.setVisible(visible);
        this.attackBtnBg.setVisible(visible);
        if (this.attackBtnFrame) this.attackBtnFrame.setVisible(visible);
        this.attackBtnText.setVisible(visible);
        if (this.recallBtn) {
            this.recallBtn.setVisible(visible);
            if (this.recallBtnGlow) this.recallBtnGlow.setVisible(visible);
            this.recallBtnBg.setVisible(visible);
            if (this.recallBtnFrame) this.recallBtnFrame.setVisible(visible);
            this.recallBtnText.setVisible(visible);
        }
        this.endTurnBtn.setVisible(visible);
        if (this.endTurnBtnGlow) this.endTurnBtnGlow.setVisible(visible);
        this.endTurnBtnBg.setVisible(visible);
        if (this.endTurnBtnFrame) this.endTurnBtnFrame.setVisible(visible);
        this.endTurnBtnText.setVisible(visible);

        // Reset attack selection when hiding
        if (!visible) {
            this.attackSelected = false;
            this.updateAttackPulse(false);
        }
    }

    setSaveButtonVisible(visible) {
        this.saveExitBtn.setVisible(visible);
        this.saveExitBtnBg.setVisible(visible);
        this.saveExitBtnText.setVisible(visible);
        if (this.forfeitBtn) this.forfeitBtn.setVisible(visible);
        if (this.forfeitBtnBg) this.forfeitBtnBg.setVisible(visible);
        if (this.forfeitBtnText) this.forfeitBtnText.setVisible(visible);
    }

    clearForfeitPending() {
        this.forfeitPending = false;
        this.hideForfeitPendingStatus();
        if (this.forfeitRedirectFallback) {
            this.forfeitRedirectFallback.remove(false);
            this.forfeitRedirectFallback = null;
        }
    }

    showForfeitPendingStatus() {
        if (!this.forfeitStatusText) return;
        this.forfeitStatusText.setText('Abandono confirmado. Redirigiendo al menu...');
        this.forfeitStatusText.setVisible(true);
    }

    hideForfeitPendingStatus() {
        if (!this.forfeitStatusText) return;
        this.forfeitStatusText.setVisible(false);
        this.forfeitStatusText.setText('');
    }

    update() {
        const mainScene = this.scene.get('MainScene');
        if (!mainScene) return;

        // Update button states based on selected drone
        if (this.isMyTurn) {
            this.updateButtonStates();
        }

        // Update selected unit info
        if (this.isMyTurn && mainScene.selectedDrone) {
            const drone = mainScene.selectedDrone;
            const hasAmmoForAttack = (drone.missiles ?? 0) > 0 && (drone.droneType !== 'Naval' || drone.canUseMissileAttack());
            const canAttack = !drone.hasAttacked && hasAmmoForAttack;
            const droneNumber = mainScene.myDrones.indexOf(drone) + 1;
            const ammo = typeof drone.missiles === 'number' ? drone.missiles : 0;
            const parts = [`Dron seleccionado: #${droneNumber}`, 'Puede moverse', `Munición: ${ammo}`];
            if (canAttack) parts.push('Puede atacar');
            this.droneInfoText.setText(parts.join('  |  '));
        } else if (this.isMyTurn && mainScene.selectedCarrier) {
            const maxMove = mainScene.selectedCarrier.maxMoveDistance;
            const undeployedCount = (mainScene.myDrones || []).filter(d => !d.deployed && d.isAlive()).length;
            if (undeployedCount > 0) {
                this.droneInfoText.setText(
                    `Portadrones seleccionado  |  ${undeployedCount} dron(es) por desplegar  |  Mov. máx: ${maxMove}`
                );
            } else {
                this.droneInfoText.setText(`Portadrones seleccionado  |  Movimiento máx: ${maxMove} casillas`);
            }
        } else if (this.isMyTurn) {
            this.droneInfoText.setText('Selecciona un dron o portadrones');
        }

        const visibleUnits = mainScene.getVisibleUnitsForMinimap();
        if (!visibleUnits.length) return;

        const tracked = visibleUnits.map((unit) => ({
            x: unit.x,
            y: unit.y,
            color: unit.isSelected
                ? 0xffff00
                : TEAM_COLORS[unit.playerIndex]
        }));

        this.minimap.update(mainScene.cameras.main, tracked);

        // Only show deployed drones in the fuel list
        const myAliveDrones = (mainScene.myDrones || []).filter((drone) => drone.isAlive() && drone.deployed);
        const allMyDrones = (mainScene.myDrones || []).filter((drone) => drone.isAlive());
        const undeployedCount = allMyDrones.length - myAliveDrones.length;

        if (this.deployPanelElements.length === 0) {
            // Panel is closed – show fuel list
            if (myAliveDrones.length) {
                const fuelLines = myAliveDrones.map((drone, idx) => {
                    const realIdx = mainScene.myDrones.indexOf(drone);
                    return `Dron ${realIdx + 1}: ${drone.fuel}/${drone.maxFuel}`;
                });
                const header = undeployedCount > 0
                    ? `Combustible  (${undeployedCount} en hangar)`
                    : 'Combustible';
                this.fuelListText.setText([header, ...fuelLines].join('\n'));
            } else if (undeployedCount > 0) {
                this.fuelListText.setText(`Combustible\n${undeployedCount} dron(es) en hangar`);
            } else {
                this.fuelListText.setText('Combustible\nSin drones activos');
            }
        }

    }
}
