export default class Drone {
    /**
     * @param {Phaser.Scene} scene
     * @param {number} x
     * @param {number} y
     * @param {number} color      – fill colour (e.g. 0x00ff00)
     * @param {boolean} interactive – only true for the local player's drones
     * @param {object} stats      – { health, maxHealth, attackDamage, attackRange }
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

        // Turn action tracking (reset each turn)
        this.hasMoved = false;
        this.hasAttacked = false;

        // Placeholder graphic
        this.sprite = scene.add.circle(x, y, 14, color);
        this.sprite.setStrokeStyle(2, 0xffffff);

        // Health bar background
        this.healthBarBg = scene.add.rectangle(x, y - 22, 30, 6, 0x333333);
        this.healthBarBg.setOrigin(0.5);

        // Health bar fill
        this.healthBar = scene.add.rectangle(x - 15, y - 22, 30, 4, 0x00ff00);
        this.healthBar.setOrigin(0, 0.5);
        this.updateHealthBar();

        // Selection ring (only relevant for your own drones)
        this.ring = scene.add.circle(x, y, 20);
        this.ring.setStrokeStyle(2, 0xffff00);
        this.ring.setFillStyle();
        this.ring.setVisible(false);

        // Target ring (shown when this drone is targetable for attack)
        this.targetRing = scene.add.circle(x, y, 22);
        this.targetRing.setStrokeStyle(3, 0xff0000);
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
            y: y - 22,
            duration: 600,
            ease: 'Power2'
        });
        // Health bar fill (different origin)
        this.scene.tweens.add({
            targets: this.healthBar,
            x: x - 15,
            y: y - 22,
            duration: 600,
            ease: 'Power2'
        });
    }

    takeDamage(damage, remainingHealth) {
        this.health = remainingHealth;
        this.updateHealthBar();

        // Flash red
        this.scene.tweens.add({
            targets: this.sprite,
            fillColor: { from: 0xff0000, to: this.color },
            duration: 300,
            ease: 'Power2'
        });

        if (this.health <= 0) {
            this.destroy();
        }
    }

    updateHealthBar() {
        const ratio = Math.max(0, this.health / this.maxHealth);
        this.healthBar.width = 30 * ratio;

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
