import Network from '../network/NetworkManager.js';

function isAerialSide(side) {
    const normalized = String(side ?? '').trim().toLowerCase();
    return normalized === 'aereo' || normalized === 'aerial';
}

function resolveLocalPlayerIndex(scene) {
    if (Number.isInteger(Network.playerIndex) && Network.playerIndex >= 0) {
        return Network.playerIndex;
    }

    for (const pi in (scene.carriers || {})) {
        const carrier = scene.carriers[pi];
        if (carrier?.isLocal) {
            return Number(pi);
        }
    }

    for (const pi in (scene.drones || {})) {
        const drones = scene.drones[pi] || [];
        if (drones.some((d) => d?.isLocal)) {
            return Number(pi);
        }
    }

    return null;
}

function resolveLocalSide(scene, localIndex) {
    const direct = scene.localSide;
    if (direct) return direct;
    const byPlayer = scene.playerSides?.[localIndex];
    if (byPlayer) return byPlayer;
    return 'Aereo';
}

export function getVisionRangeForSide(scene, side) {
    return isAerialSide(side) ? scene.aereoVisionRange : scene.navalVisionRange;
}

export function getCarrierVisionRangeForSide(scene, side) {
    return isAerialSide(side) ? scene.aereoCarrierVisionRange : scene.navalCarrierVisionRange;
}

function getLocalVisionSources(scene) {
    const sources = [];
    const localIndex = resolveLocalPlayerIndex(scene);
    if (!Number.isInteger(localIndex)) {
        return sources;
    }

    const localSide = resolveLocalSide(scene, localIndex);
    const droneVisionRange = getVisionRangeForSide(scene, localSide);
    const carrierVisionRange = getCarrierVisionRangeForSide(scene, localSide);

    const myDrones = scene.drones[localIndex] || [];
    for (const d of myDrones) {
        if (d?.isAlive() && d.sprite && d.deployed && droneVisionRange > 0) {
            sources.push({ sprite: d.sprite, visionRange: droneVisionRange });
        }
    }

    const myCarrier = scene.carriers?.[localIndex];
    if (myCarrier?.sprite && !myCarrier.destroyed && carrierVisionRange > 0) {
        sources.push({ sprite: myCarrier.sprite, visionRange: carrierVisionRange });
    }

    return sources;
}

export function isDroneVisibleToLocal(scene, drone) {
    if (!drone || !drone.isAlive()) return false;
    if (drone.playerIndex === Network.playerIndex) return true;

    const sources = getLocalVisionSources(scene);
    if (!sources.length) return false;

    for (const source of sources) {
        const sourceSprite = source.sprite;
        const distance = scene.hexGrid.getHexDistance(
            sourceSprite.x,
            sourceSprite.y,
            drone.sprite.x,
            drone.sprite.y
        );

        if (distance <= source.visionRange) return true;
    }

    return false;
}

export function isCarrierVisibleToLocal(scene, carrier) {
    if (!carrier?.sprite) return false;
    if (carrier.playerIndex === Network.playerIndex) return true;

    const sources = getLocalVisionSources(scene);
    if (!sources.length) return false;

    for (const source of sources) {
        const sourceSprite = source.sprite;
        const distance = scene.hexGrid.getHexDistance(
            sourceSprite.x,
            sourceSprite.y,
            carrier.sprite.x,
            carrier.sprite.y
        );

        if (distance <= source.visionRange) return true;
    }

    return false;
}

export function updateFogOfWar(scene) {
    if (!scene.fogRT || !scene.hexGrid) return;

    scene.fogRT.clear();
    scene.fogRT.fill(0x000000, scene.fogAlpha);

    const sources = getLocalVisionSources(scene);
    if (!sources.length) return;

    const hexWidth = Math.sqrt(3) * scene.hexGrid.size;

    for (const source of sources) {
        const radiusPx = source.visionRange * hexWidth;
        scene.fogEraser.clear();
        scene.fogEraser.fillStyle(0xffffff, 1);
        scene.fogEraser.fillCircle(0, 0, radiusPx);
        scene.fogRT.erase(scene.fogEraser, source.sprite.x, source.sprite.y);
    }
}

export function updateVision(scene) {
    if (!scene.hexGrid) return;
    const localIndex = resolveLocalPlayerIndex(scene);
    if (!Number.isInteger(localIndex)) return;

    for (const pi in scene.drones) {
        const playerIndex = parseInt(pi);
        for (const drone of scene.drones[pi]) {
            if (!drone?.isAlive()) continue;

            if (playerIndex === localIndex) {
                drone.setLocalVisibility(true);
                continue;
            }

            const visible = isDroneVisibleToLocal(scene, drone);
            drone.setLocalVisibility(visible);
            if (!visible) {
                drone.setTargetable(false);
            }
        }
    }

    for (const pi in scene.carriers) {
        const playerIndex = parseInt(pi);
        const carrier = scene.carriers[pi];
        if (!carrier?.sprite) continue;
        if (carrier.destroyed) {
            carrier.sprite.setVisible(false);
            carrier.targetRing?.setVisible(false);
            carrier.healthBarBg?.setVisible(false);
            carrier.healthBar?.setVisible(false);
            continue;
        }

        if (playerIndex === localIndex) {
            carrier.sprite.setVisible(true);
            carrier.targetRing?.setVisible(false);
            scene.updateCarrierHealthBar?.(carrier);
            continue;
        }

        const visible = isCarrierVisibleToLocal(scene, carrier);
        carrier.sprite.setVisible(visible);
        carrier.targetRing?.setVisible(Boolean(visible && carrier.isTargetable));
        scene.updateCarrierHealthBar?.(carrier);
    }

    updateFogOfWar(scene);
}
