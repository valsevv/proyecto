export default class SideImpactView {
    constructor(scene, x, y, width = 300, height = 200) {
        this.scene = scene;
        this.width = width;
        this.height = height;
        this.groundY = this.height / 2 - 28;

        this.container = scene.add.container(x, y);
        this.container.setDepth(2000);
        this.container.setScrollFactor(0);
        this.container.setVisible(false);

        this.bg = scene.add.graphics();
        this.container.add(this.bg);

        // Sea/ground layer (inserted between bg and content)
        this.sea = null;
        this._ensureSea();

        this.content = scene.add.container(0, 0);
        this.container.add(this.content);

        this._attackKind = null;
        this._pendingHideTimer = null;
        this._projectileTween = null;

        this._drawFrame();
    }

    _ensureSea() {
        const w = this.width;
        const seaH = 56;
        const seaW = w - 24;
        const seaCenterY = this.groundY + seaH / 2;

        if (this.sea) {
            this.sea.setSize(seaW, seaH);
            this.sea.setPosition(0, seaCenterY);
            return;
        }

        if (this.scene?.textures?.exists('mar')) {
            this.sea = this.scene.add.tileSprite(0, seaCenterY, seaW, seaH, 'mar');
            this.sea.setOrigin(0.5);
            this.sea.setAlpha(0.85);
            this.container.add(this.sea);
        }
    }

    _drawFrame() {
        const w = this.width;
        const h = this.height;

        this.bg.clear();
        this.bg.fillStyle(0x000000, 0.55);
        this.bg.fillRoundedRect(-w / 2, -h / 2, w, h, 10);

        // ground line
        this.bg.lineStyle(2, 0xffffff, 0.12);
        this.bg.lineBetween(-w / 2 + 12, this.groundY, w / 2 - 12, this.groundY);

        // height markers (subtle)
        this.bg.lineStyle(1, 0xffffff, 0.06);
        this.bg.lineBetween(-w / 2 + 12, -h / 2 + 24, w / 2 - 12, -h / 2 + 24);
        this.bg.lineBetween(-w / 2 + 12, -h / 2 + 70, w / 2 - 12, -h / 2 + 70);
    }

    reposition(x, y) {
        this.container.setPosition(x, y);
    }

    _clearContent() {
        if (this._pendingHideTimer) {
            clearTimeout(this._pendingHideTimer);
            this._pendingHideTimer = null;
        }
        if (this._projectileTween) {
            this._projectileTween.stop();
            this._projectileTween = null;
        }

        this.content.removeAll(true);
        this._attackerImg = null;
        this._targetImg = null;
        this._projectileImg = null;
        this._trailGfx = null;
        this._explosionImg = null;
    }

    hide() {
        this._clearContent();
        this.container.setVisible(false);
        this._attackKind = null;
    }

    onAttackStart(payload) {
        const kind = payload?.kind;
        if (kind !== 'bomb' && kind !== 'missile') return;

        this._attackKind = kind;
        this._clearContent();
        this.container.setVisible(true);

        const w = this.width;
        const h = this.height;
        const leftX = -w / 2 + 44;
        const rightX = w / 2 - 44;
        const groundY = this.groundY;

        // Heights: top is "high altitude".
        const attackerY = kind === 'bomb' ? -h / 2 + 42 : -h / 2 + 86;
        const targetY = groundY;

        const attackerKey = payload?.attackerKey || (kind === 'bomb' ? 'dron_bomba_0' : 'dron_misil_0');
        const targetKey = payload?.targetKey || 'dron_bomba_0';

        this._attackerImg = this.scene.add.image(leftX, attackerY, attackerKey);
        this._attackerImg.setDisplaySize(46, 46);
        this.content.add(this._attackerImg);

        this._targetImg = this.scene.add.image(rightX, targetY, targetKey);
        this._targetImg.setDisplaySize(46, 46);
        this.content.add(this._targetImg);

        this._trailGfx = this.scene.add.graphics();
        this.content.add(this._trailGfx);

        const projectileKey = kind === 'bomb' ? 'bomba' : (payload?.projectileKey || 'misil_2');
        this._projectileImg = this.scene.add.image(leftX, attackerY, projectileKey);
        this._projectileImg.setDisplaySize(kind === 'bomb' ? 26 : 28, kind === 'bomb' ? 26 : 16);
        this.content.add(this._projectileImg);

        const startDelayMs = payload?.startDelayMs ?? (kind === 'bomb' ? 650 : 0);
        const travelMs = payload?.travelMs ?? (kind === 'bomb' ? 1350 : 1500);

        // Draw intended trajectory once.
        this._drawTrajectory(leftX, attackerY, rightX, groundY, groundY);

        this._projectileTween = this.scene.tweens.addCounter({
            from: 0,
            to: 1,
            duration: travelMs,
            delay: startDelayMs,
            ease: 'Linear',
            onUpdate: (tween) => {
                const t = tween.getValue();
                const x = Phaser.Math.Linear(leftX, rightX, t);

                let y;
                if (kind === 'missile') {
                    y = Phaser.Math.Linear(attackerY, targetY, t);
                } else {
                    // Free-fall style: height decays quadratically.
                    const startHeight = groundY - attackerY;
                    const heightNow = startHeight * (1 - t) * (1 - t);
                    y = groundY - heightNow;
                }

                if (this._projectileImg) {
                    this._projectileImg.setPosition(x, y);
                }
            }
        });
    }

    _drawTrajectory(startX, startY, endX, endY, groundY) {
        if (!this._trailGfx) return;
        this._trailGfx.clear();

        this._trailGfx.lineStyle(2, 0xffffff, 0.18);

        if (this._attackKind === 'missile') {
            this._trailGfx.beginPath();
            this._trailGfx.moveTo(startX, startY);
            this._trailGfx.lineTo(endX, endY);
            this._trailGfx.strokePath();
            this.scene.sound.play('missile_launch', { volume: 0.35 });
            return;
        }

        // Bomb: sample free-fall curve.
        this._trailGfx.beginPath();
        const steps = 28;
        for (let i = 0; i <= steps; i++) {
            const t = i / steps;
            const x = Phaser.Math.Linear(startX, endX, t);
            const startHeight = groundY - startY;
            const heightNow = startHeight * (1 - t) * (1 - t);
            const y = groundY - heightNow;
            if (i === 0) this._trailGfx.moveTo(x, y);
            else this._trailGfx.lineTo(x, y);
        }
        this._trailGfx.strokePath();
    }

    onAttackImpact(payload) {
        const kind = payload?.kind;
        if (!this.container.visible) return;
        if (kind !== this._attackKind) return;

        // Explosion at target.
        const w = this.width;
        const rightX = w / 2 - 44;
        const groundY = this.groundY;

        if (this._explosionImg) {
            this._explosionImg.destroy();
            this._explosionImg = null;
        }

        this._explosionImg = this.scene.add.image(rightX, groundY, 'explosion');
        this._explosionImg.setDisplaySize(90, 90);
        this._explosionImg.setAlpha(0.95);
        this.content.add(this._explosionImg);
        this.scene.sound.play('explosion', { volume: 0.35 });

        this.scene.tweens.add({
            targets: this._explosionImg,
            alpha: 0,
            scale: 1.2,
            duration: 1420,
            ease: 'Quad.easeOut',
            onComplete: () => {
                if (this._explosionImg) {
                    this._explosionImg.destroy();
                    this._explosionImg = null;
                }
            }
        });
    }

    onAttackEnd(payload) {
        const kind = payload?.kind;
        if (kind !== this._attackKind) return;

        // Hide promptly after the attack finishes.
        if (this._pendingHideTimer) {
            clearTimeout(this._pendingHideTimer);
        }
        this._pendingHideTimer = setTimeout(() => {
            this.hide();
        }, 150);
    }
}
