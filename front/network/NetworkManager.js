/**
 * NetworkManager — thin wrapper around WebSocket.
*/
export default class NetworkManager {
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

    connect() {
        return new Promise((resolve, reject) => {
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
        console.log('[net] →', msg.type, msg);
        this.ws.send(JSON.stringify(msg));
    }


    join() {
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


    isMyTurn() {
        return this.currentTurn === this.playerIndex;
    }

    on(type, callback) {
        this.handlers[type] = callback;
    }

    _onMessage(event) {
        const msg = JSON.parse(event.data);
        console.log('[net] ←', msg.type, msg);

        // Internal bookkeeping
        if (msg.type === 'welcome') {
            this.playerId = msg.playerId;
            this.playerIndex = msg.playerIndex;
        }

        if (msg.type === 'turnStart') {
            this.currentTurn = msg.activePlayer;
            this.actionsRemaining = msg.actionsRemaining;
        }

        if (msg.type === 'gameStart' && msg.state) {
            this.currentTurn = msg.state.currentTurn;
            this.actionsRemaining = msg.state.actionsRemaining;
        }

        this._fire(msg.type, msg);
    }

    _fire(type, msg) {
        const handler = this.handlers[type];
        if (handler) handler(msg);
    }
}
