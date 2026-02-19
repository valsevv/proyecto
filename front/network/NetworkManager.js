import { WS_URL } from "../shared/constants.js";

/**
 * NetworkManager — thin wrapper around WebSocket.
*/
class NetworkManager {
    constructor(url) {
        this.url = url;
        this.ws = null;
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
        this.token = token;
    }
    connect() {
        return new Promise((resolve, reject) => {

            if (this.token) {
                reject("No JWT token found");
                return;
            }

            const urlWithToken = `${this.url}?token=${token}`;

            this.ws = new WebSocket(urlWithToken);

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
        this.ws.send(JSON.stringify(msg));
    }

    join() {
        console.log('[net] === SENDING JOIN MESSAGE ===');
        this.send({ type: 'join' });
    }

    requestMove(droneIndex, x, y) {
        this.send({ type: 'move', droneIndex, x, y });
    }

    requestAttack(attackerIndex, targetPlayer, targetDrone) {
        this.send({ type: 'attack', attackerIndex, targetPlayer, targetDrone });
    }

    endTurn() {
        this.send({ type: 'endTurn' });
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
        const isOverwriting = !!this.handlers[type];
        if (isOverwriting) {
            console.warn('[net] ⚠️  OVERWRITING EXISTING HANDLER for:', type);
            console.trace('[net] Stack trace for handler overwrite:');
        } else {
            console.log('[net] ✓ Registering new handler for:', type);
        }
        this.handlers[type] = callback;
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
        const handler = this.handlers[type];
        if (handler) {
            console.log('[net] ✓ Handler exists for', type, '- executing...');
            handler(msg);
            console.log('[net] ✓ Handler executed for', type);
        } else {
            console.warn('[net] ⚠️  NO HANDLER REGISTERED for:', type);
            console.log('[net] Current registered handlers:', Object.keys(this.handlers));
        }
    }
}

export default new NetworkManager(WS_URL);