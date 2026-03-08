const DEFAULT_GAME_CONFIG = {
    actionsPerTurn: 10,
    navalVisionRange: 4,
    aerialVisionRange: 6,
    aerialDroneMovementRange: 2,
    navalDroneMovementRange: 2,
    aerialDroneMaxFuel: 10,
    navalDroneMaxFuel: 10,
    aerialDroneAmmo: 1,
    navalDroneMissiles: 2,
    aerialCarrierMovementRange: 3,
    navalCarrierMovementRange: 1,
    missileMaxDistance: 15
};

let runtimeGameConfig = { ...DEFAULT_GAME_CONFIG };

export function getGameConfig() {
    return runtimeGameConfig;
}

export async function loadGameConfig() {
    try {
        const response = await fetch('/api/game/config', {
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();
        runtimeGameConfig = {
            ...DEFAULT_GAME_CONFIG,
            ...data
        };
    } catch (error) {
        console.warn('[game-config] usando valores por defecto:', error);
        runtimeGameConfig = { ...DEFAULT_GAME_CONFIG };
    }

    return runtimeGameConfig;
}
