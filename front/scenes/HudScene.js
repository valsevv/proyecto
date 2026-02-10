import Minimap from '../ui/Minimap.js';

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

        // Action buttons container (bottom center)
        this.createActionButtons();

        // Listen for events from MainScene
        const mainScene = this.scene.get('MainScene');
        mainScene.events.on('statusChanged', this.onStatusChanged, this);
        mainScene.events.on('gameStarted', this.onGameStarted, this);
        mainScene.events.on('turnChanged', this.onTurnChanged, this);
        mainScene.events.on('attackModeEnded', this.deselectAttackButton, this);
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
        const canAttack = mainScene?.selectedDrone && !mainScene.selectedDrone.hasAttacked;

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

    onStatusChanged(status) {
        this.turnText.setText(status);
    }

    onGameStarted() {
        this.gameStarted = true;
    }

    onTurnChanged({ isMyTurn }) {
        this.isMyTurn = isMyTurn;

        if (isMyTurn) {
            this.turnText.setText('Tu turno');
            this.turnText.setStyle({ fill: '#00ff00' });
            this.setButtonsVisible(true);
        } else {
            this.turnText.setText('Turno del oponente');
            this.turnText.setStyle({ fill: '#ff4444' });
            this.droneInfoText.setText('');
            this.setButtonsVisible(false);
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

    update() {
        const mainScene = this.scene.get('MainScene');
        if (!mainScene) return;

        // Update button states based on selected drone
        if (this.isMyTurn) {
            this.updateButtonStates();
        }

        // Update drone info if we have a selected drone
        if (this.isMyTurn && mainScene.selectedDrone) {
            const drone = mainScene.selectedDrone;
            const canMove = !drone.hasMoved;
            const canAttack = !drone.hasAttacked;
            const parts = [];
            if (canMove) parts.push('Puede moverse');
            if (canAttack) parts.push('Puede atacar');
            this.droneInfoText.setText(parts.join('  |  ') || 'Sin acciones');
        } else if (this.isMyTurn) {
            this.droneInfoText.setText('Selecciona un dron');
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
    }
}
