export default class Drone {
    constructor(scene, x, y) {
        this.scene = scene;

        // Placeholder graphic
        this.sprite = scene.add.circle(x, y, 14, 0x00ff00);
        this.sprite.setStrokeStyle(2, 0xffffff);
        this.sprite.setInteractive({ useHandCursor: true });

        // Selection ring
        this.ring = scene.add.circle(x, y, 20);
        this.ring.setStrokeStyle(2, 0xffff00);
        this.ring.setFillStyle();
        this.ring.setVisible(false);

        // Click to select
        this.sprite.on('pointerdown', (pointer) => {
            pointer.event.stopPropagation();
            scene.selectDrone(this);
        });
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
