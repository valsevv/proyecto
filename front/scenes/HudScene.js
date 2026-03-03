import Minimap from '../ui/Minimap.js';
import SideImpactView from '../ui/SideImpactView.js';
import networkManager from '../network/NetworkManager.js';

const TEAM_COLORS = [0x00ff00, 0xff4444];

/**
 * HudScene — overlay for UI elements (minimap, turn indicator, action buttons).
 * Runs in parallel with MainScene; not affected by camera scroll.
 */
export default class HudScene extends Phaser.Scene {
    constructor() {
        super('HudScene');
        this.isMyTurn = false;
        this.gameStarted = false;
        this.actionsRemaining = 0;
        this.actionsPerTurn = 10;
        this.selectionData = null;
        /** Phaser objects comprising the deploy panel */
        this.deployPanelElements = [];
    }

    create() {        
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
            this.layoutActionButtons();
            this.layoutSaveButton();
        });

        this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
            if (!mainScene) return;
            if (this._onAttackAnimStart) mainScene.events.off('attackAnimStart', this._onAttackAnimStart, this);
            if (this._onAttackAnimImpact) mainScene.events.off('attackAnimImpact', this._onAttackAnimImpact, this);
            if (this._onAttackAnimEnd) mainScene.events.off('attackAnimEnd', this._onAttackAnimEnd, this);
        });
    }

    createActionButtons() {
        const margin = 20;
        const btnWidth = 120;
        const btnHeight = 40;
        const btnRadius = 20;
        const gap = 10;
        this.actionBtnConfig = { margin, btnWidth, btnHeight, btnRadius, gap };
        this.saveBtnConfig = { margin, btnWidth, btnHeight, btnRadius };
        const { startX, y } = this.getActionButtonLayout();

        // Colors
        this.grayColor = 0x555555;
        this.attackActiveColor = 0xaa3333;
        this.endTurnActiveColor = 0x336699;
        this.recallActiveColor = 0x226622;

        // Attack button state
        this.attackSelected = false;

        // Attack button background
        this.attackBtnBg = this.add.graphics();
        this.drawRoundedButton(this.attackBtnBg, startX - btnWidth / 2, y - btnHeight / 2, btnWidth, btnHeight, btnRadius, this.grayColor);

        // Attack button text
        this.attackBtnText = this.add.text(startX, y, '⚔ Atacar', {
            fontSize: '16px',
            fill: '#ffffff'
        }).setOrigin(0.5);

        // Attack button hitbox
        this.attackBtn = this.add.rectangle(startX, y, btnWidth, btnHeight, 0x000000, 0.001)
            .setInteractive({ useHandCursor: true });

        this.attackBtn.on('pointerdown', () => {
            const mainScene = this.scene.get('MainScene');
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

        // Recall (Replegar) button
        const recallX = startX + btnWidth + gap;
        this.recallBtnBg = this.add.graphics();
        this.drawRoundedButton(this.recallBtnBg, recallX - btnWidth / 2, y - btnHeight / 2, btnWidth, btnHeight, btnRadius, this.grayColor);

        this.recallBtnText = this.add.text(recallX, y, '🔄 Replegar', {
            fontSize: '16px',
            fill: '#ffffff'
        }).setOrigin(0.5);

        this.recallBtn = this.add.rectangle(recallX, y, btnWidth, btnHeight, 0x000000, 0.001)
            .setInteractive({ useHandCursor: true });

        this.recallBtn.on('pointerdown', () => {
            const mainScene = this.scene.get('MainScene');
            if (!mainScene?.canRecallSelectedDrone?.()) return;
            mainScene.recallSelectedDrone();
        });

        // End Turn button background
        const endTurnX = recallX + btnWidth + gap;
        this.endTurnBtnBg = this.add.graphics();
        this.drawRoundedButton(this.endTurnBtnBg, endTurnX - btnWidth / 2, y - btnHeight / 2, btnWidth, btnHeight, btnRadius, this.endTurnActiveColor);

        // End Turn button text
        this.endTurnBtnText = this.add.text(endTurnX, y, '⏭ Fin Turno', {
            fontSize: '16px',
            fill: '#ffffff'
        }).setOrigin(0.5);

        // End Turn button hitbox
        this.endTurnBtn = this.add.rectangle(endTurnX, y, btnWidth, btnHeight, 0x000000, 0.001)
            .setInteractive({ useHandCursor: true });

        this.endTurnBtn.on('pointerdown', () => {
            const mainScene = this.scene.get('MainScene');
            mainScene.endTurn();
        });

        // Save & Exit button (always visible during game, positioned at top-right)
        this.saveExitActiveColor = 0x995500;
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

        this.saveExitBtn.on('pointerdown', () => {
            if (confirm('¿Guardar y salir de la partida?')) {
                networkManager.saveAndExit();
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
    }

    drawRoundedButton(graphics, x, y, width, height, radius, color, alpha = 1) {
        graphics.clear();
        graphics.fillStyle(color, alpha);
        graphics.fillRoundedRect(x, y, width, height, radius);
    }

    updateButtonStates() {
        const layout = this.actionBtnLayout || this.getActionButtonLayout();
        const { startX, endTurnX, y, btnWidth, btnHeight } = layout;
        const btnRadius = this.actionBtnConfig?.btnRadius ?? 20;

        // Check if selected drone can attack
        const mainScene = this.scene.get('MainScene');
        const selectedDrone = mainScene?.selectedDrone;
        const hasAmmoForAttack = !selectedDrone
            ? true
            : ((selectedDrone.missiles ?? 0) > 0) && (selectedDrone.droneType !== 'Naval' || selectedDrone.canUseMissileAttack());
        const canAttack = selectedDrone && !selectedDrone.hasAttacked && hasAmmoForAttack;

        // Update attack button color and alpha
        let attackColor, attackAlpha, textAlpha;
        if (!canAttack) {
            attackColor = this.grayColor;
            attackAlpha = 0.4;
            textAlpha = 0.4;
        } else if (this.attackSelected) {
            attackColor = this.attackActiveColor;
            attackAlpha = 1;
            textAlpha = 1;
        } else {
            attackColor = this.grayColor;
            attackAlpha = 1;
            textAlpha = 1;
        }
        this.drawRoundedButton(
            this.attackBtnBg,
            startX - btnWidth / 2,
            y - btnHeight / 2,
            btnWidth,
            btnHeight,
            btnRadius,
            attackColor,
            attackAlpha
        );
        this.attackBtnText.setAlpha(textAlpha);

        // End turn is always blue
        this.drawRoundedButton(
            this.endTurnBtnBg,
            endTurnX - btnWidth / 2,
            y - btnHeight / 2,
            btnWidth,
            btnHeight,
            btnRadius,
            this.endTurnActiveColor
        );

        // Recall button — enabled only when selected drone is deployed and within 2 hexes of carrier
        if (this.recallBtnBg) {
            const canRecall = !!(mainScene?.canRecallSelectedDrone?.());
            const recallColor = canRecall ? this.recallActiveColor : this.grayColor;
            const recallAlpha = canRecall ? 1 : 0.4;
            const recallX = layout.recallX ?? (startX + btnWidth + (this.actionBtnConfig?.gap ?? 10));
            this.drawRoundedButton(
                this.recallBtnBg,
                recallX - btnWidth / 2,
                y - btnHeight / 2,
                btnWidth,
                btnHeight,
                btnRadius,
                recallColor,
                recallAlpha
            );
            if (this.recallBtnText) this.recallBtnText.setAlpha(recallAlpha);
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
        closeHit.on('pointerdown', (ptr) => {
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
            hit.on('pointerdown', (ptr) => {
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
        this.attackBtnBg.setVisible(visible);
        this.attackBtnText.setVisible(visible);
        if (this.recallBtn) {
            this.recallBtn.setVisible(visible);
            this.recallBtnBg.setVisible(visible);
            this.recallBtnText.setVisible(visible);
        }
        this.endTurnBtn.setVisible(visible);
        this.endTurnBtnBg.setVisible(visible);
        this.endTurnBtnText.setVisible(visible);

        // Reset attack selection when hiding
        if (!visible) {
            this.attackSelected = false;
            this.updateButtonStates();
        }
    }

    setSaveButtonVisible(visible) {
        this.saveExitBtn.setVisible(visible);
        this.saveExitBtnBg.setVisible(visible);
        this.saveExitBtnText.setVisible(visible);
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
