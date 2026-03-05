import Network from "../network/NetworkManager.js";

export function getVisionRangeForSide(scene, side) {
  return side === "Aereo" ? scene.aereoVisionRange : scene.navalVisionRange;
}

export function isDroneVisibleToLocal(scene, drone) {
  if (!drone || !drone.isAlive()) return false;
  if (drone.playerIndex === Network.playerIndex) return true;

  const visionRange = getVisionRangeForSide(scene, scene.localSide);
  if (!visionRange || visionRange <= 0) return false;

  const sources = [];
  const myDrones = scene.drones[Network.playerIndex] || [];
  for (const d of myDrones) {
    if (d?.isAlive() && d.sprite && d.deployed) sources.push(d.sprite);
  }

  const myCarrier = scene.carriers?.[Network.playerIndex];
  if (myCarrier?.sprite && !myCarrier.destroyed) sources.push(myCarrier.sprite);

  for (const sourceSprite of sources) {
    const distance = scene.hexGrid.getHexDistance(
      sourceSprite.x,
      sourceSprite.y,
      drone.sprite.x,
      drone.sprite.y
    );

    if (distance <= visionRange) return true;
  }

  return false;
}

export function isCarrierVisibleToLocal(scene, carrier) {
  if (!carrier?.sprite) return false;
  if (carrier.playerIndex === Network.playerIndex) return true;

  const visionRange = getVisionRangeForSide(scene, scene.localSide);
  if (!visionRange || visionRange <= 0) return false;

  const sources = [];
  const myDrones = scene.drones[Network.playerIndex] || [];
  for (const d of myDrones) {
    if (d?.isAlive() && d.sprite && d.deployed) sources.push(d.sprite);
  }

  const myCarrier = scene.carriers?.[Network.playerIndex];
  if (myCarrier?.sprite && !myCarrier.destroyed) sources.push(myCarrier.sprite);

  for (const sourceSprite of sources) {
    const distance = scene.hexGrid.getHexDistance(
      sourceSprite.x,
      sourceSprite.y,
      carrier.sprite.x,
      carrier.sprite.y
    );

    if (distance <= visionRange) return true;
  }

  return false;
}

export function updateFogOfWar(scene) {
  if (!scene.fogRT || !scene.hexGrid) return;

  scene.fogRT.clear();
  scene.fogRT.fill(0x000000, scene.fogAlpha);

  const visionRange = getVisionRangeForSide(scene, scene.localSide);
  if (!visionRange || visionRange <= 0) {
    return;
  }

  const hexWidth = Math.sqrt(3) * scene.hexGrid.size;
  const radiusPx = visionRange * hexWidth;

  scene.fogEraser.clear();
  scene.fogEraser.fillStyle(0xffffff, 1);
  scene.fogEraser.fillCircle(0, 0, radiusPx);

  const sources = [];
  const myDrones = scene.drones[Network.playerIndex] || [];
  for (const d of myDrones) {
    if (d?.isAlive() && d.sprite && d.deployed) sources.push(d.sprite);
  }
  const myCarrier = scene.carriers?.[Network.playerIndex];
  if (myCarrier?.sprite && !myCarrier.destroyed) sources.push(myCarrier.sprite);

  for (const s of sources) {
    scene.fogRT.erase(scene.fogEraser, s.x, s.y);
  }
}

export function updateVision(scene) {
  if (!scene.hexGrid) return;
  const localIndex = Network.playerIndex;
  if (typeof localIndex !== "number") return;

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
      continue;
    }

    if (playerIndex === localIndex) {
      carrier.sprite.setVisible(true);
      carrier.targetRing?.setVisible(false);
      continue;
    }

    const visible = isCarrierVisibleToLocal(scene, carrier);
    carrier.sprite.setVisible(visible);
    carrier.targetRing?.setVisible(Boolean(visible && carrier.isTargetable));
  }

  updateFogOfWar(scene);
}
