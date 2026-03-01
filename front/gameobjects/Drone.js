const DRONE_MOVE_DURATION_MS = 1200;

export default class Drone {
    /**
     * @param {Phaser.Scene} scene
     * @param {number} x
     * @param {number} y
     * @param {number} color      – fill colour (e.g. 0x00ff00) - used for health bar and fallback
     * @param {boolean} interactive – only true for the local player's drones
     * @param {object} stats      – { health, maxHealth, attackDamage, attackRange, droneType }
     */
    constructor(scene, x, y, color = 0x00ff00, interactive = true, stats = {}) {
        this.scene = scene;
        this.color = color;
        this.isLocal = interactive;

        // Combat stats
        this.health = stats.health ?? 100;
        this.maxHealth = stats.maxHealth ?? 100;
        this.attackDamage = stats.attackDamage ?? 25;
        this.attackRange = stats.attackRange ?? 3;
        this.droneType = stats.droneType ?? 'Aereo';
        this.maxFuel = stats.maxFuel ?? 10;
        this.fuel = stats.fuel ?? this.maxFuel;
        this.missiles = stats.missiles ?? 0;
        this.destroyed = false;

        // Visibility (used for fog-of-war/vision).
        // NOTE: This is "visible to the local client", not Phaser's global visibility.
        this.isVisibleToLocal = true;

        // Animation/interaction locks
        this.isAttacking = false;
        this.isMoving = false;
        this.isMoveQueued = false;
        this.isAttackQueued = false;
        this._moveQueueTimer = null;
        this._attackQueueTimer = null;

        // UI offsets relative to sprite
        this.healthBarOffsetY = 16;
        this.healthBarOffsetX = 12;

        // Turn action tracking (reset each turn)
        this.hasMoved = false;
        this.hasAttacked = false;

        // Determine sprite key based on drone type y movimiento
        let spriteKey;
        // FIX: Assets were swapped. Bomb drone belongs to Aereo; Missile drone belongs to Naval.
        if (this.droneType === 'Naval') {
            spriteKey = 'dron_misil_0'; // por defecto, estático
        } else {
            spriteKey = 'dron_bomba_0'; // por defecto, estático
        }

        // Crear sprite y escalar a 128x128
        this.sprite = scene.add.image(x, y, spriteKey);
        this.sprite.setDisplaySize(128, 128); // Escala a 128x128
        this.sprite.setDepth(10);
        this.sprite.setOrigin(0.5);

        // Guardar el estado de movimiento para cambiar el asset
        this.currentDirection = 0; // 0: estático, 1: izq, 2: der, 3: abajo, 4: arriba

        // Don't tint - keep original drone colors

        // Health bar background (positioned above the smaller sprite)
        this.healthBarBg = scene.add.rectangle(x, y - this.healthBarOffsetY, 20, 3, 0x333333);
        this.healthBarBg.setDepth(11);
        this.healthBarBg.setOrigin(0.5);

        // Health bar fill
        this.healthBar = scene.add.rectangle(x - this.healthBarOffsetX, y - this.healthBarOffsetY, 20, 2, 0x00ff00);
        this.healthBar.setDepth(12);
        this.healthBar.setOrigin(0, 0.5);
        this.updateHealthBar();

        // Selection ring (only relevant for your own drones)
        this.ring = scene.add.circle(x, y, 10);
        this.ring.setDepth(13);
        this.ring.setStrokeStyle(2, 0xffff00);
        this.ring.setFillStyle();
        this.ring.setVisible(false);

        // Target ring (shown when this drone is targetable for attack)
        this.targetRing = scene.add.circle(x, y, 12);
        this.targetRing.setDepth(13);
        this.targetRing.setStrokeStyle(2, 0xff0000);
        this.targetRing.setFillStyle();
        this.targetRing.setVisible(false);

        // Track UI state separate from Phaser visibility (needed for vision/fog).
        this.isTargetable = false;

        // Make drone clickable
        this.sprite.setInteractive({ useHandCursor: true, pixelPerfect: true, alphaTolerance: 1 });
        this.sprite.on('pointerdown', (pointer) => {
            pointer.event.stopPropagation();
            scene.onDroneClicked(this);
        });

        this.syncUIPositions();
    }

    isBusy() {
        return !!(this.destroyed || this.isMoving || this.isAttacking || this.isMoveQueued || this.isAttackQueued);
    }

    queueMoveLock(ttlMs = 2500) {
        this.isMoveQueued = true;
        if (this._moveQueueTimer) {
            clearTimeout(this._moveQueueTimer);
        }
        this._moveQueueTimer = setTimeout(() => {
            this.isMoveQueued = false;
            this._moveQueueTimer = null;
        }, ttlMs);
    }

    clearMoveLock() {
        this.isMoveQueued = false;
        if (this._moveQueueTimer) {
            clearTimeout(this._moveQueueTimer);
            this._moveQueueTimer = null;
        }
    }

    queueAttackLock(ttlMs = 3000) {
        this.isAttackQueued = true;
        if (this._attackQueueTimer) {
            clearTimeout(this._attackQueueTimer);
        }
        this._attackQueueTimer = setTimeout(() => {
            this.isAttackQueued = false;
            this._attackQueueTimer = null;
        }, ttlMs);
    }

    clearAttackLock() {
        this.isAttackQueued = false;
        if (this._attackQueueTimer) {
            clearTimeout(this._attackQueueTimer);
            this._attackQueueTimer = null;
        }
    }

    syncUIPositions() {
        if (!this.sprite || !this.sprite.active) return;
        const x = this.sprite.x;
        const y = this.sprite.y;

        if (this.ring) this.ring.setPosition(x, y);
        if (this.targetRing) this.targetRing.setPosition(x, y);
        if (this.healthBarBg) this.healthBarBg.setPosition(x, y - this.healthBarOffsetY);
        if (this.healthBar) this.healthBar.setPosition(x - this.healthBarOffsetX, y - this.healthBarOffsetY);
    }

    stopMotionTweens() {
        // Stop only movement/attack tweens (don't kill selection pulse tweens).
        const tweensToStop = [
            this.moveTweenBody,
            this.moveTweenHealthBg,
            this.moveTweenHealth,
            this.attackTweenBody,
            this.attackTweenReturn
        ].filter(Boolean);

        for (const tween of tweensToStop) {
            tween.stop();
        }

        // If we stopped an in-flight move tween, consider movement interrupted.
        if (this.moveTweenBody || this.moveTweenHealthBg || this.moveTweenHealth) {
            this.isMoving = false;
        }

        this.moveTweenBody = null;
        this.moveTweenHealthBg = null;
        this.moveTweenHealth = null;
        this.attackTweenBody = null;
        this.attackTweenReturn = null;
    }

    select() {
        this.syncUIPositions();
        this.ring.setVisible(true);
        this.ring.setStrokeStyle(3, 0xfff176);

        if (this.selectionTween) {
            this.selectionTween.stop();
        }
        this.selectionTween = this.scene.tweens.add({
            targets: this.ring,
            alpha: { from: 0.35, to: 1 },
            duration: 500,
            yoyo: true,
            repeat: -1,
            ease: 'Sine.easeInOut'
        });
    }

    deselect() {
        this.ring.setVisible(false);
        this.ring.setAlpha(1);
        if (this.selectionTween) {
            this.selectionTween.stop();
            this.selectionTween = null;
        }
    }

    setTargetable(show) {
        this.syncUIPositions();
        this.isTargetable = !!show;
        // Only show target ring if this drone is currently visible to the local player.
        this.targetRing.setVisible(this.isTargetable && this.isVisibleToLocal);
    }

    /**
     * Sets whether this drone should be visible to the local player.
     * Used by MainScene's vision system.
     * @param {boolean} isVisible
     */
    setLocalVisibility(isVisible) {
        this.isVisibleToLocal = !!isVisible;
        const v = this.isVisibleToLocal;
        if (this.sprite) this.sprite.setVisible(v);
        if (this.healthBarBg) this.healthBarBg.setVisible(v);
        if (this.healthBar) this.healthBar.setVisible(v);
        if (this.targetRing) this.targetRing.setVisible(v && this.isTargetable);

        // Selection ring is only meaningful for local drones; don't force it on.
        if (this.ring && !v) this.ring.setVisible(false);
    }

    moveTo(x, y) {
        if (this.destroyed || this.isAttacking) return;

        // If there were prior tweens (e.g., interrupted movement), stop them.
        this.stopMotionTweens();
        this.isMoving = true;
        this.clearMoveLock();

        // Detectar dirección del movimiento
        let direction = 0;
        const dx = x - this.sprite.x;
        const dy = y - this.sprite.y;
        if (Math.abs(dx) > Math.abs(dy)) {
            if (dx < 0) direction = 1; // izquierda
            else if (dx > 0) direction = 2; // derecha
        } else if (Math.abs(dy) > 0) {
            if (dy > 0) direction = 3; // abajo
            else if (dy < 0) direction = 4; // arriba
        }
        this.setDirection(direction);

        // Drone body and rings
        this.moveTweenBody = this.scene.tweens.add({
            targets: [this.sprite, this.ring, this.targetRing],
            x: x,
            y: y,
            duration: DRONE_MOVE_DURATION_MS,
            ease: 'Power2',
            onComplete: () => {
                this.setDirection(0); // volver a estático al terminar
                this.syncUIPositions();

                this.isMoving = false;

                if (typeof this.scene?.updateVision === 'function') {
                    this.scene.updateVision();
                }

                this.moveTweenBody = null;
            }
        });
        // Health bar background (offset above drone)
        this.moveTweenHealthBg = this.scene.tweens.add({
            targets: this.healthBarBg,
            x: x,
            y: y - this.healthBarOffsetY,
            duration: DRONE_MOVE_DURATION_MS,
            ease: 'Power2',
            onComplete: () => {
                this.moveTweenHealthBg = null;
            }
        });
        // Health bar fill (different origin)
        this.moveTweenHealth = this.scene.tweens.add({
            targets: this.healthBar,
            x: x - this.healthBarOffsetX,
            y: y - this.healthBarOffsetY,
            duration: DRONE_MOVE_DURATION_MS,
            ease: 'Power2',
            onComplete: () => {
                this.moveTweenHealth = null;
            }
        });

    }

    /**
     * Cambia el asset del dron según la dirección
     * @param {number} direction 0=estático, 1=izq, 2=der, 3=abajo, 4=arriba
     */
    setDirection(direction) {
        let keys;
        // FIX: Assets were swapped. Bomb = Aereo, Missile = Naval.
        if (this.droneType === 'Naval') {
            keys = ['dron_misil_0', 'dron_misil_1', 'dron_misil_2', 'dron_misil_3', 'dron_misil_4'];
        } else {
            keys = ['dron_bomba_0', 'dron_bomba_1', 'dron_bomba_2', 'dron_bomba_3', 'dron_bomba_4'];
        }
        if (this.currentDirection === direction) return;
        this.currentDirection = direction;
        this.sprite.setTexture(keys[direction]);
        this.sprite.setDisplaySize(128, 128);
    }

    /**
     * Animación de ataque del dron bomba: vuela sobre el enemigo y lanza bomba
     * @param {number} targetX - posición X del enemigo
     * @param {number} targetY - posición Y del enemigo
     * @param {number | null} settleX - posición final X donde queda el dron tras atacar
     * @param {number | null} settleY - posición final Y donde queda el dron tras atacar
     * @param {Drone | null} targetDrone - dron objetivo (opcional, para UI lateral)
     */
    aereoDronAttack(targetX, targetY, settleX = null, settleY = null, targetDrone = null) {
        if (this.droneType !== 'Aereo') return;

        this.isAttacking = true;
        this.stopMotionTweens();
        this.clearAttackLock();
        this.setDirection(0);
        this.syncUIPositions();

        // Notify HUD to show the side impact view.
        this.scene?.events?.emit('attackAnimStart', {
            kind: 'bomb',
            attackerKey: this.sprite?.texture?.key,
            targetKey: targetDrone?.sprite?.texture?.key ?? null,
            startDelayMs: 650,
            travelMs: 1350
        });

        const startX = this.sprite.x;
        const startY = this.sprite.y;

        const dropX = targetX;
        const dropY = targetY - 180;
        const endX = typeof settleX === 'number' ? settleX : startX;
        const endY = typeof settleY === 'number' ? settleY : startY;

        // Step 1: move above the enemy to drop the bomb.
        this.attackTweenBody = this.scene.tweens.add({
            targets: this.sprite,
            x: dropX,
            y: dropY,
            scale: 0.15,
            duration: 650,
            ease: 'Power2',
            onUpdate: () => this.syncUIPositions(),
            onComplete: () => {
                this.attackTweenBody = null;
                this.launchBomb(targetX, targetY, () => {
                    // Step 2: settle where the server says (or back to start).
                    this.attackTweenReturn = this.scene.tweens.add({
                        targets: this.sprite,
                        x: endX,
                        y: endY,
                        scale: 0.15,
                        duration: 650,
                        ease: 'Power2',
                        onUpdate: () => this.syncUIPositions(),
                        onComplete: () => {
                            this.attackTweenReturn = null;
                            this.isAttacking = false;
                            this.syncUIPositions();

                            this.scene?.events?.emit('attackAnimEnd', { kind: 'bomb' });
                        }
                    });
                });
            }
        });
    }

    /**
     * Lanza la bomba desde el dron bomba hacia el enemigo
     * @param {number} targetX
     * @param {number} targetY
     */
    launchBomb(targetX, targetY, onComplete) {
        // Crear sprite de la bomba
        const bomb = this.scene.add.image(this.sprite.x, this.sprite.y, 'bomba');
        bomb.setDisplaySize(64, 64);
        bomb.setDepth(20);
        let finished = false;
        const finishImpact = () => {
            if (finished) return;
            finished = true;
            if (bomb && bomb.active) {
                bomb.destroy();
            }

            // Impact explosion (shared asset)
            if (this.scene?.textures?.exists('explosion')) {
                const boom = this.scene.add.image(targetX, targetY, 'explosion');
                boom.setDepth(22);
                boom.setDisplaySize(140, 140);
                boom.setAlpha(0.95);
                this.scene.tweens.add({
                    targets: boom,
                    alpha: 0,
                    scale: 1.25,
                    duration: 420,
                    ease: 'Quad.easeOut',
                    onComplete: () => boom.destroy()
                });
            }

            this.scene?.events?.emit('attackAnimImpact', { kind: 'bomb' });
            if (typeof onComplete === 'function') {
                onComplete();
            }
        };

        this.scene.tweens.add({
            targets: bomb,
            x: targetX,
            y: targetY,
            duration: 1350,
            ease: 'Cubic.easeIn',
            onComplete: () => {
                finishImpact();
            }
        });

        // Failsafe: in case the tween gets interrupted.
        this.scene?.time?.delayedCall(1550, () => {
            finishImpact();
        });
    }

    /**
     * Animación de ataque del dron misil: lanza un cohete rápido
     * @param {number} targetX - posición X del enemigo
     * @param {number} targetY - posición Y del enemigo
     * @param {Drone | null} targetDrone - dron objetivo (opcional, para UI lateral)
     */
    launchMissile(targetX, targetY, targetDrone = null) {
        // FIX: Missile drone belongs to Naval.
        if (this.droneType !== 'Naval') return;
        this.isAttacking = true;
        this.stopMotionTweens();
        this.clearAttackLock();

        // Detect missile direction based on target position
        const dx = targetX - this.sprite.x;
        const dy = targetY - this.sprite.y;
        let missileDir = 0; // default
        if (Math.abs(dx) > Math.abs(dy)) {
            if (dx < 0) missileDir = 1; // izquierda
            else if (dx > 0) missileDir = 2; // derecha
        } else if (Math.abs(dy) > 0) {
            if (dy > 0) missileDir = 3; // abajo
            else if (dy < 0) missileDir = 4; // arriba
        }
        const missileAssetKeys = ['misil', 'misil_1', 'misil_2', 'misil_3', 'misil_4'];
        const missileKey = missileAssetKeys[missileDir];

        // Notify HUD to show the side impact view.
        this.scene?.events?.emit('attackAnimStart', {
            kind: 'missile',
            attackerKey: this.sprite?.texture?.key,
            targetKey: targetDrone?.sprite?.texture?.key ?? null,
            projectileKey: missileKey,
            startDelayMs: 0,
            travelMs: 1500
        });

        // Crear sprite del cohete con dirección correcta
        const missile = this.scene.add.image(this.sprite.x, this.sprite.y, missileKey);
        missile.setDisplaySize(48, 48);
        missile.setDepth(21);
        
        // Calcular rotación hacia el objetivo

        const angle = Math.atan2(dy, dx) * (180 / Math.PI);
        missile.setRotation(angle * (Math.PI / 180));
        
        this.scene.tweens.add({
            targets: missile,
            x: targetX,
            y: targetY,
            duration: 1500,
            ease: 'Quad.easeIn',
            onComplete: () => {
                missile.destroy();
                this.isAttacking = false;

                this.scene?.events?.emit('attackAnimImpact', { kind: 'missile' });
                this.scene?.events?.emit('attackAnimEnd', { kind: 'missile' });
            }
        });
    }

    takeDamage(damage, remainingHealth) {
        this.health = remainingHealth;
        this.updateHealthBar();

        // Flash effect using alpha instead of tint
        this.sprite.setAlpha(0.3);
        this.scene.time.delayedCall(150, () => {
            if (this.sprite && this.sprite.active) {
                this.sprite.setAlpha(1);
            }
        });

        if (this.health <= 0) {
            this.destroy();
        }
    }

    updateHealthBar() {
        const ratio = Math.max(0, this.health / this.maxHealth);
        this.healthBar.width = 24 * ratio;

        if (ratio > 0.6) {
            this.healthBar.setFillStyle(0x00ff00);
        } else if (ratio > 0.3) {
            this.healthBar.setFillStyle(0xffff00);
        } else {
            this.healthBar.setFillStyle(0xff0000);
        }
    }

    isAlive() {
        return !this.destroyed && this.health > 0;
    }

    setFuel(remainingFuel) {
        if (typeof remainingFuel === "number") {
            this.fuel = Math.max(0, remainingFuel);
        }
    }

    consumeMissile() {
        this.missiles = Math.max(0, (this.missiles ?? 0) - 1);
    }

    canUseMissileAttack() {
        // FIX: Missile drone belongs to Naval.
        return this.droneType === 'Naval' && (this.missiles ?? 0) > 0;
    }

    sinkAndDestroy() {
        if (this.destroyed) return;
        this.destroyed = true;
        this.health = 0;
        this.fuel = 0;

        this.scene.tweens.add({
            targets: [this.sprite, this.ring, this.targetRing, this.healthBar, this.healthBarBg],
            y: `+=20`,
            alpha: 0,
            duration: 700,
            ease: "Cubic.easeIn"
        });
        this.scene.tweens.add({
            targets: this.sprite,
            angle: 15,
            scaleX: 0.05,
            scaleY: 0.05,
            duration: 700,
            ease: "Cubic.easeIn",
            onComplete: () => {
                this.sprite.destroy();
                this.ring.destroy();
                this.targetRing.destroy();
                this.healthBar.destroy();
                this.healthBarBg.destroy();
            }
        });
    }

    destroy() {
        if (this.destroyed) return;
        this.destroyed = true;
        this.health = 0;
        this.scene.tweens.add({
            targets: [this.sprite, this.ring, this.targetRing, this.healthBar, this.healthBarBg],
            alpha: 0,
            duration: 500,
            ease: 'Power2',
            onComplete: () => {
                this.sprite.destroy();
                this.ring.destroy();
                this.targetRing.destroy();
                this.healthBar.destroy();
                this.healthBarBg.destroy();
            }
        });
    }
}
