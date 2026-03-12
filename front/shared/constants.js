export const API_BASE = window.location.origin + '/api';
export const WS_URL = (window.location.protocol === 'https:' ? window.location.origin.replace('https:', 'wss:') : window.location.origin.replace('http:', 'ws:')) + '/ws';

export const WORLD_WIDTH = 3200;
export const WORLD_HEIGHT = 2400;

export const VIEW_WIDTH = 1200;
export const VIEW_HEIGHT = 600;
