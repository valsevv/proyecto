//export const WS_URL = 'wss://developable-roderick-unjokingly.ngrok-free.dev/ws' // wss://developable-roderick-unjokingly.ngrok-free.dev/ws'

//export const API_BASE = 'https://developable-roderick-unjokingly.ngrok-free.dev/api';
// Usa el host actual para que funcione en localhost y en LAN (ej: 192.168.x.x)
const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
const host = window.location.host;

export const API_BASE = `${protocol}//${host}`;
export const WS_URL = `${wsProtocol}//${host}/ws`;

export const WORLD_WIDTH = 3200;
export const WORLD_HEIGHT = 2400;

export const VIEW_WIDTH = 1200;
export const VIEW_HEIGHT = 600;
