import Network from '../network/NetworkManager.js';

export function getVisionRangeForSide(scene, side) {
    return side === 'Aereo' ? scene.aereoVisionRange : scene.navalVisionRange;
}

export function getCarrierVisionRangeForSide(scene, side) {
    return side === 'Aereo' ? scene.aereoCarrierVisionRange : scene.navalCarrierVisionRange;
}

function getLocalVisionSources(scene) {
    const sources = [];
    const localSide = scene.localSide;
    const droneVisionRange = getVisionRangeForSide(scene, localSide);
    const carrierVisionRange = getCarrierVisionRangeForSide(scene, localSide);

    const myDrones = scene.drones[Network.playerIndex] || [];
    for (const d of myDrones) {
        if (d?.isAlive() && d.sprite && d.deployed && droneVisionRange > 0) {
            sources.push({ sprite: d.sprite, visionRange: droneVisionRange });
        }
    }

    const myCarrier = scene.carriers?.[Network.playerIndex];
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
    const localIndex = Network.playerIndex;
    if (typeof localIndex !== 'number') return;

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
