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

        // Fondo con imagen proporcionada para la vista lateral
        this.bgImage = scene.add.image(0, 0, 'vista_lateral_fondo');
        this.bgImage.setDisplaySize(this.width, this.height);
        this.bgImage.setOrigin(0.5);
        this.container.add(this.bgImage);
        this.frameBorder = scene.add.graphics();

        // Sea/ground layer (inserted between bg and content)
        this.sea = null;
        this._ensureSea();
        this.container.add(this.frameBorder);
        this._drawMetalBorder();

        this.content = scene.add.container(0, 0);
        this.container.add(this.content);

        this._attackKind = null;
        this._pendingHideTimer = null;
        this._projectileTween = null;

        // Ya no se dibuja el fondo con gráficos, se usa la imagen
    }

    _ensureSea() {
        const w = this.width;
        const seaH = 90;
        const seaW = w - 24;
        const seaCenterY = this.groundY + seaH / 2 + 8;

        if (this.sea) {
            this.sea.setSize(seaW, seaH);
            this.sea.setPosition(0, seaCenterY);
            return;
        }

        if (this.scene?.textures?.exists('mar')) {
            this.sea = this.scene.add.tileSprite(0, seaCenterY, seaW, seaH, 'mar');
            this.sea.setOrigin(0.5);
            this.sea.setAlpha(0.7);
            this.container.add(this.sea);
        }
    }

    _drawFrame() {
        // Método eliminado, ahora el fondo es una imagen
    }

    _drawMetalBorder() {
        if (!this.frameBorder) return;

        const g = this.frameBorder;
        const halfWidth = this.width / 2;
        const halfHeight = this.height / 2;

        g.clear();
        g.lineStyle(10, 0x4c525b, 1);
        g.strokeRoundedRect(-halfWidth, -halfHeight, this.width, this.height, 18);

        g.lineStyle(3, 0xc3c7cd, 1);
        g.strokeRoundedRect(-halfWidth + 3, -halfHeight + 3, this.width - 6, this.height - 6, 14);

        g.lineStyle(1, 0xffffff, 0.6);
        g.strokeRoundedRect(-halfWidth + 6, -halfHeight + 6, this.width - 12, this.height - 12, 10);
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
        const leftX = -w / 4 + 10;
        const rightX = w / 4 - 10;
        const groundY = this.groundY;

        // Heights: top is "high altitude" for bomb, mid for missile.
        const attackerY = kind === 'bomb' ? -h / 2 + 48 : -h / 2 + 70;
        const targetYOffset = kind === 'bomb' ? 15 : 10;
        const targetY = groundY - targetYOffset;

        const attackerKey = payload?.attackerKey || (kind === 'bomb' ? 'dron_bomba_0' : 'dron_misil_0');
        const targetKey = payload?.targetKey || 'dron_bomba_0';

        this._attackerImg = this.scene.add.image(leftX, attackerY, attackerKey);
        this._attackerImg.setDisplaySize(48, 48);
        this.content.add(this._attackerImg);

        this._targetImg = this.scene.add.image(rightX, targetY, targetKey);
        this._targetImg.setDisplaySize(48, 48);
        this.content.add(this._targetImg);

        this._trailGfx = this.scene.add.graphics();
        this.content.add(this._trailGfx);

        const projectileKey = kind === 'bomb' ? 'bomba' : (payload?.projectileKey || 'misil_2');
        this._projectileImg = this.scene.add.image(leftX, attackerY, projectileKey);
        this._projectileImg.setDisplaySize(kind === 'bomb' ? 28 : 30, kind === 'bomb' ? 28 : 18);
        this.content.add(this._projectileImg);

        const startDelayMs = payload?.startDelayMs ?? (kind === 'bomb' ? 650 : 0);
        const travelMs = payload?.travelMs ?? (kind === 'bomb' ? 1350 : 1500);

        // Draw intended trajectory once.
        this._drawTrajectory(leftX, attackerY, rightX, targetY, groundY);

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
        const rightX = w / 4 - 10;
        const groundY = this.groundY;

        if (this._explosionImg) {
            this._explosionImg.destroy();
            this._explosionImg = null;
        }

        this._explosionImg = this.scene.add.image(rightX, groundY, 'explosion');
        this._explosionImg.setDisplaySize(95, 95);
        this._explosionImg.setAlpha(0.95);
        this.content.add(this._explosionImg);
        this.scene.sound.play('explosion', { volume: 0.35 });

        this.scene.tweens.add({
            targets: this._explosionImg,
            alpha: 0,
            scale: 0.5,
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
