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

        // Turn action tracking (reset each turn)
        this.hasMoved = false;
        this.hasAttacked = false;

        // Determine sprite key based on drone type
        const spriteKey = this.droneType === 'Naval' ? 'dron_bomba' : 'dron_misil';

        // Create sprite instead of circle - use image instead of sprite since they're not animated
        this.sprite = scene.add.image(x, y, spriteKey);
        this.sprite.setScale(0.125); // Scale down the 64x64 image to 8x8
        this.sprite.setOrigin(0.5);

        // Don't tint - keep original drone colors

        // Health bar background (positioned above the smaller sprite)
        this.healthBarBg = scene.add.rectangle(x, y - 10, 20, 3, 0x333333);
        this.healthBarBg.setOrigin(0.5);

        // Health bar fill
        this.healthBar = scene.add.rectangle(x - 10, y - 10, 20, 2, 0x00ff00);
        this.healthBar.setOrigin(0, 0.5);
        this.updateHealthBar();

        // Selection ring (only relevant for your own drones)
        this.ring = scene.add.circle(x, y, 10);
        this.ring.setStrokeStyle(2, 0xffff00);
        this.ring.setFillStyle();
        this.ring.setVisible(false);

        // Target ring (shown when this drone is targetable for attack)
        this.targetRing = scene.add.circle(x, y, 12);
        this.targetRing.setStrokeStyle(2, 0xff0000);
        this.targetRing.setFillStyle();
        this.targetRing.setVisible(false);

        // Make drone clickable
        this.sprite.setInteractive({ useHandCursor: true });
        this.sprite.on('pointerdown', (pointer) => {
            pointer.event.stopPropagation();
            scene.onDroneClicked(this);
        });
    }

    select() {
        this.ring.setPosition(this.sprite.x, this.sprite.y);
        this.ring.setVisible(true);
    }

    deselect() {
        this.ring.setVisible(false);
    }

    setTargetable(show) {
        this.targetRing.setPosition(this.sprite.x, this.sprite.y);
        this.targetRing.setVisible(show);
    }

    moveTo(x, y) {
        // Drone body and rings
        this.scene.tweens.add({
            targets: [this.sprite, this.ring, this.targetRing],
            x: x,
            y: y,
            duration: 600,
            ease: 'Power2'
        });
        // Health bar background (offset above drone)
        this.scene.tweens.add({
            targets: this.healthBarBg,
            x: x,
            y: y - 16,
            duration: 600,
            ease: 'Power2'
        });
        // Health bar fill (different origin)
        this.scene.tweens.add({
            targets: this.healthBar,
            x: x - 12,
            y: y - 16,
            duration: 600,
            ease: 'Power2'
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
        return this.health > 0;
    }

    destroy() {
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
