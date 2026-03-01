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
    }

    create() {
        console.log('[HudScene] === CREATE CALLED ===');
        
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
            backgroundColor: '#000000aa',
            padding: { x: 12, y: 6 }
        }).setOrigin(0.5, 0);

        // Drone info text (shows selected drone's available actions)
        this.droneInfoText = this.add.text(this.scale.width / 2, 55, '', {
            fontSize: '14px',
            fill: '#cccccc',
            backgroundColor: '#000000aa',
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
        console.log('[HudScene] === SETTING UP EVENT LISTENERS ===');
        const mainScene = this.scene.get('MainScene');
        console.log('[HudScene] MainScene exists?:', !!mainScene);        
        
        mainScene.events.on('statusChanged', this.onStatusChanged, this);
        mainScene.events.on('gameStarted', this.onGameStarted, this);
        mainScene.events.on('turnChanged', this.onTurnChanged, this);
        mainScene.events.on('actionsUpdated', this.onActionsUpdated, this);
        mainScene.events.on('attackModeEnded', this.deselectAttackButton, this);
        mainScene.events.on('selectionChanged', this.onSelectionChanged, this);

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
        });

        this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
            if (!mainScene) return;
            if (this._onAttackAnimStart) mainScene.events.off('attackAnimStart', this._onAttackAnimStart, this);
            if (this._onAttackAnimImpact) mainScene.events.off('attackAnimImpact', this._onAttackAnimImpact, this);
            if (this._onAttackAnimEnd) mainScene.events.off('attackAnimEnd', this._onAttackAnimEnd, this);
        });
        
        console.log('[HudScene] === EVENT LISTENERS REGISTERED ===');
        console.log('[HudScene] === CREATE COMPLETE ===');
    }

    createActionButtons() {
        const margin = 20;
        const btnWidth = 120;
        const btnHeight = 40;
        const btnRadius = 20;
        const startX = margin + btnWidth / 2;
        const y = this.scale.height - margin - btnHeight / 2;

        // Colors
        this.grayColor = 0x555555;
        this.attackActiveColor = 0xaa3333;
        this.endTurnActiveColor = 0x336699;

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

        // End Turn button background
        const endTurnX = startX + btnWidth + 10;
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

        // Initially hidden
        this.setButtonsVisible(false);
    }

    drawRoundedButton(graphics, x, y, width, height, radius, color, alpha = 1) {
        graphics.clear();
        graphics.fillStyle(color, alpha);
        graphics.fillRoundedRect(x, y, width, height, radius);
    }

    updateButtonStates() {
        const btnWidth = 120;
        const btnHeight = 40;
        const btnRadius = 20;
        const margin = 20;
        const startX = margin;
        const y = this.scale.height - margin - btnHeight;

        // Check if selected drone can attack
        const mainScene = this.scene.get('MainScene');
        const selectedDrone = mainScene?.selectedDrone;
        const hasAmmoForAttack = !selectedDrone || selectedDrone.droneType !== 'Naval' || selectedDrone.canUseMissileAttack();
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
        this.drawRoundedButton(this.attackBtnBg, startX, y, btnWidth, btnHeight, btnRadius, attackColor, attackAlpha);
        this.attackBtnText.setAlpha(textAlpha);

        // End turn is always blue
        const endTurnX = startX + btnWidth + 10;
        this.drawRoundedButton(this.endTurnBtnBg, endTurnX, y, btnWidth, btnHeight, btnRadius, this.endTurnActiveColor);
    }

    deselectAttackButton() {
        this.attackSelected = false;
        this.updateButtonStates();
    }


    onSelectionChanged(selectionData) {
        this.selectionData = selectionData;
    }

    onStatusChanged(status) {
        console.log('[HudScene] === ON STATUS CHANGED ===');
        console.log('[HudScene] New status:', status);
        this.turnText.setText(status);
    }

    onGameStarted() {
        console.log('[HudScene] === ON GAME STARTED ===');
        this.gameStarted = true;
        this.setSaveButtonVisible(true);
    }

    onTurnChanged({ isMyTurn }) {
        console.log('[HudScene] === ON TURN CHANGED ===');
        console.log('[HudScene] isMyTurn:', isMyTurn);
        
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
            const canAttack = !drone.hasAttacked;
            const droneNumber = mainScene.myDrones.indexOf(drone) + 1;
            const parts = [`Dron seleccionado: #${droneNumber}`, 'Puede moverse'];
            if (canAttack) parts.push('Puede atacar');
            this.droneInfoText.setText(parts.join('  |  '));
        } else if (this.isMyTurn && mainScene.selectedCarrier) {
            const maxMove = mainScene.selectedCarrier.maxMoveDistance;
            this.droneInfoText.setText(`Portadrones seleccionado  |  Movimiento máx: ${maxMove} casillas`);
        } else if (this.isMyTurn) {
            this.droneInfoText.setText('Selecciona un dron o portadrones');
        }

        const allDrones = mainScene.getAllDrones();
        if (!allDrones.length) return;

        const tracked = allDrones.map(({ drone, playerIndex }) => ({
            x: drone.sprite.x,
            y: drone.sprite.y,
            color: mainScene.selectedDrone === drone
                ? 0xffff00
                : TEAM_COLORS[playerIndex]
        }));

        this.minimap.update(mainScene.cameras.main, tracked);

        const myAliveDrones = (mainScene.myDrones || []).filter((drone) => drone.isAlive());
        if (myAliveDrones.length) {
            const fuelLines = myAliveDrones.map((drone, idx) => `Dron ${idx + 1}: ${drone.fuel}/${drone.maxFuel}`);
            this.fuelListText.setText(['Combustible', ...fuelLines].join('\n'));
        } else {
            this.fuelListText.setText('Combustible\nSin drones activos');
        }

    }
}
