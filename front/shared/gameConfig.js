const DEFAULT_GAME_CONFIG = {
    actionsPerTurn: 10,
    navalVisionRange: 4,
    aerialVisionRange: 6,
    aerialDroneMovementRange: 4,
    navalDroneMovementRange: 6,
    aerialDroneMaxFuel: 10,
    navalDroneMaxFuel: 10,
    aerialDroneMaxHp: 100,
    navalDroneMaxHp: 100,
    aerialDroneAmmo: 1,
    navalDroneMissiles: 2,
    aerialCarrierMaxHp: 6,
    navalCarrierMaxHp: 3,
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
