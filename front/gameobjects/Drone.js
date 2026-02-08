export default class Drone {
    /**
     * @param {Phaser.Scene} scene
     * @param {number} x
     * @param {number} y
     * @param {number} color      – fill colour (e.g. 0x00ff00)
     * @param {boolean} interactive – only true for the local player's drones
     */
    constructor(scene, x, y, color = 0x00ff00, interactive = true) {
        this.scene = scene;
        this.color = color;

        // Placeholder graphic
        this.sprite = scene.add.circle(x, y, 14, color);
        this.sprite.setStrokeStyle(2, 0xffffff);

        // Selection ring (only relevant for your own drones)
        this.ring = scene.add.circle(x, y, 20);
        this.ring.setStrokeStyle(2, 0xffff00);
        this.ring.setFillStyle();
        this.ring.setVisible(false);

        // Only local drones are clickable
        if (interactive) {
            this.sprite.setInteractive({ useHandCursor: true });
            this.sprite.on('pointerdown', (pointer) => {
                pointer.event.stopPropagation();
                scene.selectDrone(this);
            });
        }
    }

    select() {
        this.ring.setPosition(this.sprite.x, this.sprite.y);
        this.ring.setVisible(true);
    }

    deselect() {
        this.ring.setVisible(false);
    }

    moveTo(x, y) {
        this.scene.tweens.add({
            targets: [this.sprite, this.ring],
            x: x,
            y: y,
            duration: 1600,
            ease: 'Power2'
        });
    }
}
