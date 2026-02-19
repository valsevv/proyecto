import Network from '../network/NetworkManager.js';
import { VIEW_WIDTH, VIEW_HEIGHT } from '../shared/constants.js';

export default class LobbyScene extends Phaser.Scene {
    constructor() {
        super('LobbyScene');
        this.sideSelected = false;
        this.waitingForOpponent = false;
        this.transitioning = false; // Track if we're already transitioning
    }

    preload() {
        // Load side preview images (use same keys as MainScene will use)
        this.load.image('dron_bomba', 'assets/dron_bomba.png');
        this.load.image('dron_misil', 'assets/dron_misil.png');
        this.load.image('mar', 'assets/mar.png');
    }

    create() {
        // Background
        const bg = this.add.image(VIEW_WIDTH / 2, VIEW_HEIGHT / 2, 'mar');
        bg.setDisplaySize(VIEW_WIDTH, VIEW_HEIGHT);

        // Title (save reference to hide if player 1)
        this.titleText = this.add.text(VIEW_WIDTH / 2, 80, 'SELECCIONA TU FACCIÓN', {
            fontSize: '36px',
            fontFamily: 'Arial',
            fontStyle: 'bold',
            fill: '#ffffff',
            stroke: '#000000',
            strokeThickness: 4
        }).setOrigin(0.5);

        // Container for buttons
        const leftX = VIEW_WIDTH / 2 - 200;
        const rightX = VIEW_WIDTH / 2 + 200;
        const centerY = VIEW_HEIGHT / 2;

        // Naval side (left)
        this.createSideButton(leftX, centerY, 'Naval', 'dron_bomba', 
            'Dron Bomba\n\nAlto daño\nCorto alcance\nMovimiento lento');

        // Aereo side (right)
        this.createSideButton(rightX, centerY, 'Aereo', 'dron_misil',
            'Dron Misil\n\nDaño medio\nLargo alcance\nMovimiento rápido');

        // Status text (hidden by default, only shown for player 1)
        this.statusText = this.add.text(VIEW_WIDTH / 2, VIEW_HEIGHT - 100, '', {
            fontSize: '24px',
            fontFamily: 'Arial',
            fill: '#ffff00',
            align: 'center'
        }).setOrigin(0.5);
        this.statusText.setVisible(false); // Hidden by default

        // Setup network handlers
        this.setupNetwork();

        // Connect and join
        console.log('[LobbyScene] === CONNECTING TO SERVER ===');
        
        const token = localStorage.getItem("token");

        if (!token) {
            console.error("No token found, redirecting to login");
            this.scene.start('LoginScene');
            return;
        }

        Network.setToken(token);

        Network.connect().then(() => {
            console.log('[LobbyScene] === CONNECTION ESTABLISHED ===');
            Network.join();
            console.log('[LobbyScene] === JOIN MESSAGE SENT ===');
            // Don't set status text here - wait for welcome message
        }).catch(err => {
            console.error('[LobbyScene] === CONNECTION FAILED ===', err);
            this.statusText.setText('Error de conexión');
        });
    }

    createSideButton(x, y, side, imageKey, description) {
        // Container for the button
        const container = this.add.container(x, y);

        // Background panel
        const bg = this.add.rectangle(0, 0, 300, 400, 0x001122, 0.8);
        bg.setStrokeStyle(3, 0x00ffff);

        // Preview image
        const preview = this.add.image(0, -80, imageKey);
        preview.setDisplaySize(128, 128);

        // Side name
        const nameText = this.add.text(0, 30, side, {
            fontSize: '32px',
            fontFamily: 'Arial',
            fontStyle: 'bold',
            fill: '#00ffff'
        }).setOrigin(0.5);

        // Description
        const descText = this.add.text(0, 100, description, {
            fontSize: '16px',
            fontFamily: 'Arial',
            fill: '#cccccc',
            align: 'center',
            lineSpacing: 8
        }).setOrigin(0.5);

        // Add to container
        container.add([bg, preview, nameText, descText]);

        // Make interactive
        bg.setInteractive({ useHandCursor: true });
        bg.on('pointerover', () => {
            if (!this.sideSelected) {
                bg.setStrokeStyle(4, 0xffff00);
            }
        });
        bg.on('pointerout', () => {
            if (!this.sideSelected) {
                bg.setStrokeStyle(3, 0x00ffff);
            }
        });
        bg.on('pointerdown', () => {
            if (!this.sideSelected) {
                this.selectSide(side, container);
            }
        });

        // Store reference
        if (side === 'Naval') {
            this.navalButton = container;
        } else {
            this.aereoButton = container;
        }
    }

    selectSide(side, container) {
        console.log('[LobbyScene] === SELECT SIDE CALLED ===');
        console.log('[LobbyScene] Side:', side);
        console.log('[LobbyScene] Already selected?:', this.sideSelected);
        console.log('[LobbyScene] Player index:', Network.playerIndex);
        
        this.sideSelected = true;
        this.waitingForOpponent = true;

        // Send selection to server
        console.log('[LobbyScene] === SENDING SELECT_SIDE TO SERVER ===');
        Network.selectSide(side);

        // Visual feedback - highlight selected
        const bg = container.getAt(0);
        bg.setFillStyle(0x003344, 1);
        bg.setStrokeStyle(4, 0x00ff00);

        // Disable both buttons
        this.navalButton.getAt(0).disableInteractive();
        this.aereoButton.getAt(0).disableInteractive();

        // Don't show status text for player 0 (they just see the selection UI disabled)
        console.log('[LobbyScene] === UI UPDATED - WAITING FOR OPPONENT ===');
    }

    setupNetwork() {
        console.log('[LobbyScene] === SETTING UP NETWORK HANDLERS ===');
        
        Network.on('welcome', (msg) => {
            console.log('[LobbyScene] === WELCOME RECEIVED ===');
            console.log('[LobbyScene] Player index:', msg.playerIndex);
            console.log('[LobbyScene] Full message:', msg);
            
            // Update status based on player index
            if (msg.playerIndex === 0) {
                // First player - can select immediately (no status text)
                console.log('[LobbyScene] I am PLAYER 0 (first player)');
                // Player 0 doesn't see status text
            } else if (msg.playerIndex === 1) {
                // Second player - wait for first player to select or auto-select if already chosen
                console.log('[LobbyScene] I am PLAYER 1 (second player)');
                
                // Hide selection UI for second player
                console.log('[LobbyScene] === HIDING SELECTION UI FOR PLAYER 1 ===');
                if (this.titleText) this.titleText.setVisible(false);
                if (this.navalButton) this.navalButton.setVisible(false);
                if (this.aereoButton) this.aereoButton.setVisible(false);
                
                // Show status text for player 1
                this.statusText.setVisible(true);
                this.statusText.setText('Esperando al primer jugador...');
            }
        });

        Network.on('sideChosen', (msg) => {
            console.log('[LobbyScene] === SIDE_CHOSEN RECEIVED ===');
            console.log('[LobbyScene] Player who chose:', msg.playerIndex);
            console.log('[LobbyScene] Side chosen:', msg.side);
            console.log('[LobbyScene] My player index:', Network.playerIndex);
            console.log('[LobbyScene] Have I selected?:', this.sideSelected);
            console.log('[LobbyScene] Full message:', msg);
            
            // If we're the second player and haven't selected yet, auto-assign opposite side
            if (!this.sideSelected && Network.playerIndex === 1) {
                console.log('[LobbyScene] === AUTO-SELECTING OPPOSITE SIDE FOR PLAYER 1 ===');
                const oppositeSide = msg.side === 'Naval' ? 'Aereo' : 'Naval';
                console.log('[LobbyScene] Opposite side:', oppositeSide);
                const container = oppositeSide === 'Naval' ? this.navalButton : this.aereoButton;
                this.statusText.setText('Asignando lado: ' + oppositeSide);
                // Small delay so player can see the assignment
                this.time.delayedCall(500, () => {
                    console.log('[LobbyScene] === EXECUTING AUTO-SELECTION ===');
                    this.selectSide(oppositeSide, container);
                });
            } else {
                console.log('[LobbyScene] Not auto-selecting (either already selected or not player 1)');
            }
        });

        Network.on('bothReady', () => {
            console.log('[LobbyScene] === BOTH_READY RECEIVED ===');
            console.log('[LobbyScene] Both players have selected sides');
            this.statusText.setText('¡Comenzando juego!');
        });

        Network.on('gameStart', (msg) => {
            console.log('[LobbyScene] === GAME_START RECEIVED ===');
            console.log('[LobbyScene] Transitioning?:', this.transitioning);
            console.log('[LobbyScene] Game state present?:', !!msg.state);
            console.log('[LobbyScene] Full game state:', msg.state);
            
            // Prevent double transition
            if (this.transitioning) {
                console.log('[LobbyScene] === ALREADY TRANSITIONING - IGNORING ===');
                return;
            }
            
            this.transitioning = true;
            this.statusText.setText('¡Iniciando partida!');
            console.log('[LobbyScene] === STARTING SCENE TRANSITION IN 100ms ===');
            
            // Small delay to ensure server has fully initialized
            this.time.delayedCall(100, () => {
                console.log('[LobbyScene] === CALLING scene.start(MainScene) ===');
                console.log('[LobbyScene] Passing game state:', msg.state);
                // Transition to main game scene with game state
                this.scene.start('MainScene', { gameState: msg.state });
                console.log('[LobbyScene] === scene.start CALLED ===');
            });
        });

        Network.on('playerLeft', () => {
            this.statusText.setText('El oponente se desconectó');
            this.sideSelected = false;
            this.waitingForOpponent = false;
            
            // Re-enable buttons and reset their appearance
            if (this.navalButton) {
                const navBg = this.navalButton.getAt(0);
                navBg.setInteractive({ useHandCursor: true });
                navBg.setFillStyle(0x001122, 0.8);
                navBg.setStrokeStyle(3, 0x00ffff);
            }
            if (this.aereoButton) {
                const aerBg = this.aereoButton.getAt(0);
                aerBg.setInteractive({ useHandCursor: true });
                aerBg.setFillStyle(0x001122, 0.8);
                aerBg.setStrokeStyle(3, 0x00ffff);
            }
        });

        Network.on('error', (msg) => {
            console.error('[LobbyScene] Server error:', msg.message);
            this.statusText.setText('Error: ' + msg.message);
        });
    }
}
