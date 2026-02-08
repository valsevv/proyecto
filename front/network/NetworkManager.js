/**
 * NetworkManager — thin wrapper around WebSocket.
 *
 * Usage:
 *   const net = new NetworkManager('ws://localhost:8080/ws');
 *   net.on('welcome',   msg => { ... });
 *   net.on('gameStart', msg => { ... });
 *   net.on('moveDrone', msg => { ... });
 *   await net.connect();
 *   net.join();
 *   net.requestMove(droneIndex, x, y);
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
        this.ws.send(JSON.stringify(msg));
    }

    join() {
        this.send({ type: 'join' });
    }

    requestMove(droneIndex, x, y) {
        this.send({ type: 'move', droneIndex, x, y });
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

        this._fire(msg.type, msg);
    }

    _fire(type, msg) {
        const handler = this.handlers[type];
        if (handler) handler(msg);
    }
}
