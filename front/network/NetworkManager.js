import { WS_URL } from "../shared/constants.js";

/**
 * NetworkManager — thin wrapper around WebSocket.
*/
class NetworkManager {
    constructor(url) {
        this.url = url;
        this.ws = null;
        //cada tipo tiene un ARRAY de handlers
        this.handlers = {};

        /** Set after the server sends "welcome" */
        this.playerId = null;
        /** 0 or 1 — which player slot we occupy */
        this.playerIndex = -1;
        /** Track whose turn it is */
        this.currentTurn = -1;
        /** Actions remaining in current turn */
        this.actionsRemaining = 0;
    }

    setToken(token) {
        // Token no longer needed - cookies are sent automatically
        // Keep method for backward compatibility but it's a no-op
        console.log('[net] setToken() called but cookies are used instead');
    }
    
    connect() {
        return new Promise((resolve, reject) => {
            // Connect to WebSocket - browser automatically sends cookies
            this.ws = new WebSocket(this.url);

            this.ws.onopen = () => {
                console.log('[net] connected');
                resolve();
            };

            this.ws.onerror = (err) => {
                console.error('[net] error:', err);
                reject(err);
            };

            this.ws.onmessage = (event) => this._onMessage(event);

            this.ws.onclose = () => {
                console.log('[net] disconnected');
                this._fire('disconnect', {});
            };
        });
    }


    send(msg) {
        console.log('='.repeat(80));
        console.log('[net] → SENDING MESSAGE:', msg.type);
        console.log('[net] Full message:', msg);
        console.log('='.repeat(80));

        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            console.warn('[net] Cannot send, socket not open');
            return;
        }
        this.ws.send(JSON.stringify(msg));
    }

    join(lobbyId) {
        console.log('[net] === SENDING JOIN MESSAGE ===');
        console.log('[net] Lobby ID:', lobbyId);
        this.send({ type: 'join', lobbyId });
    }

    requestMove(droneIndex, x, y) {
        this.send({ type: 'move', droneIndex, x, y });
    }

    requestAttack(attackerIndex, targetPlayer, targetDrone, lineX = null, lineY = null) {
        this.send({ type: 'attack', attackerIndex, targetPlayer, targetDrone, lineX, lineY });
    }

    endTurn() {
        this.send({ type: 'endTurn' });
    }

    saveAndExit() {
        console.log('[net] === SENDING SAVE_AND_EXIT MESSAGE ===');
        this.send({ type: 'saveAndExit' });
    }

    loadGame(gameId) {
        console.log('[net] === SENDING LOAD_GAME MESSAGE ===');
        console.log('[net] Game ID:', gameId);
        this.send({ type: 'loadGame', gameId });
    }
    
    selectSide(side) {
        console.log('[net] === SENDING SELECT_SIDE MESSAGE ===');
        console.log('[net] Side:', side);
        this.send({ type: 'selectSide', side });
    }

    isMyTurn() {
        return this.currentTurn === this.playerIndex;
    }

    on(type, callback) {
        if (!this.handlers[type]) {
            this.handlers[type] = [];
        }

        this.handlers[type].push(callback);
        console.log(`[net] ✓ Handler added for: ${type} (total: ${this.handlers[type].length})`);
    }

    off(type, callback) {
        if (!this.handlers[type]) return;

        this.handlers[type] = this.handlers[type].filter(cb => cb !== callback);
        console.log(`[net] Handler removed for: ${type}`);
    }
    
    _onMessage(event) {
        const msg = JSON.parse(event.data);
        console.log('='.repeat(80));
        console.log('[net] ← RECEIVED MESSAGE:', msg.type);
        console.log('[net] Full message:', msg);
        console.log('='.repeat(80));

        // Internal bookkeeping
        if (msg.type === 'welcome') {
            this.playerId = msg.playerId;
            this.playerIndex = msg.playerIndex;
            console.log('[net] Updated playerId:', this.playerId);
            console.log('[net] Updated playerIndex:', this.playerIndex);
        }

        if (msg.type === 'turnStart') {
            this.currentTurn = msg.activePlayer;
            this.actionsRemaining = msg.actionsRemaining;
            console.log('[net] Updated currentTurn:', this.currentTurn);
            console.log('[net] Updated actionsRemaining:', this.actionsRemaining);
        }

        if (msg.type === 'gameStart' && msg.state) {
            this.currentTurn = msg.state.currentTurn;
            this.actionsRemaining = msg.state.actionsRemaining;
            console.log('[net] Updated from gameStart - currentTurn:', this.currentTurn);
            console.log('[net] Updated from gameStart - actionsRemaining:', this.actionsRemaining);
        }

        console.log('[net] Firing handler for:', msg.type);
        this._fire(msg.type, msg);
        console.log('[net] Handler fired');
    }

    _fire(type, msg) {
        const handlers = this.handlers[type];

        if (!handlers || handlers.length === 0) {
            console.warn('[net] No handlers for:', type);
            return;
        }

        console.log(`[net] Firing ${handlers.length} handler(s) for: ${type}`);

        for (const handler of handlers) {
            try {
                handler(msg);
            } catch (err) {
                console.error('[net] Handler error:', err);
            }
        }
    }
}

export default new NetworkManager(WS_URL);
