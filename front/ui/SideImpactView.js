export default class SideImpactView {
    constructor(scene, x, y, width = 300, height = 200) {
        this.scene = scene;
        this.width = width;
        this.height = height;
        this.groundY = 30 ; 
        // Background image already includes ambience; keep sea overlay disabled unless explicitly enabled.
        this.seaOverlayEnabled = false;
   

        this.container = scene.add.container(x, y);
        // Keep this overlay above any in-scene overlays/popups.
        this.container.setDepth(20000);
        this.container.setScrollFactor(0);
        this.container.setVisible(false);

        // Fondo con imagen proporcionada para la vista lateral
        this.bgImage = scene.add.image(0, 0, 'vista_lateral_fondo');
        // Reduce size to fit within the border (accounting for 10px border + padding)
        this.bgImage.setDisplaySize(this.width - 20, this.height - 20);
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
        this._missileLaunchSfx = null;

        // Ya no se dibuja el fondo con gráficos, se usa la imagen
    }

    _ensureSea() {
        if (!this.seaOverlayEnabled) {
            if (this.sea) {
                this.sea.destroy();
                this.sea = null;
            }
            return;
        }

        const w = this.width;
        const h = this.height;
        const seaH = 70; // Reduced height to fit within bounds
        const seaW = w - 24;
        // Position to stay within the border (height/2 - border - seaH/2)
        const seaCenterY = (h / 2) - 15 - (seaH / 2);

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
        if (this._missileLaunchSfx) {
            this._missileLaunchSfx.stop();
            this._missileLaunchSfx.destroy();
            this._missileLaunchSfx = null;
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

        // Ensure HUD scene is top-most so this overlay stays above MainScene popups.
        this.scene?.scene?.bringToTop?.('HudScene');
        this.container.setDepth(20000);
        this.container.setVisible(true);

        const w = this.width;
        const h = this.height;
        const leftX = -w / 4 + 10;
        const rightX = w / 4 - 10;
        const groundY = this.groundY;
        const attackerKey = payload?.attackerKey || (kind === 'bomb' ? 'dron_bomba_0' : 'dron_misil_0');
        const isBombAttacker = /^dron_bomba/i.test(String(attackerKey)) || kind === 'bomb';

        // Heights: top is "high altitude" for bomb, mid for missile.
        const attackerY = isBombAttacker ? -h / 2 + 48 : -h / 2 + 70;
        const targetYOffset = kind === 'bomb' ? 15 : 10;
        const targetY = groundY - targetYOffset;

        // Bombers should appear directly above the target before dropping.
        const attackerX = isBombAttacker ? rightX : leftX;
        const bomberDropY = targetY - 58;
        const targetKey = payload?.targetKey || 'dron_bomba_0';
        const isCarrierTarget = /^porta_drones_/i.test(String(targetKey));
        const targetSize = isCarrierTarget ? 96 : 48;

        this._attackerImg = this.scene.add.image(attackerX, isBombAttacker ? bomberDropY : attackerY, attackerKey);
        this._attackerImg.setDisplaySize(48, 48);
        this.content.add(this._attackerImg);

        this._targetImg = this.scene.add.image(rightX, targetY, targetKey);
        this._targetImg.setDisplaySize(targetSize, targetSize);
        this.content.add(this._targetImg);

        this._trailGfx = this.scene.add.graphics();
        this.content.add(this._trailGfx);

        const projectileKey = kind === 'bomb' ? 'bomba' : (payload?.projectileKey || 'misil_2');
        this._projectileImg = this.scene.add.image(attackerX, isBombAttacker ? bomberDropY + 8 : attackerY, projectileKey);
        this._projectileImg.setDisplaySize(kind === 'bomb' ? 28 : 30, kind === 'bomb' ? 28 : 18);
        this.content.add(this._projectileImg);

        const startDelayMs = payload?.startDelayMs ?? (kind === 'bomb' ? 650 : 0);
        const travelMs = payload?.travelMs ?? (kind === 'bomb' ? 1350 : 1500);

        // Draw intended trajectory once.
        this._drawTrajectory(attackerX, isBombAttacker ? bomberDropY + 8 : attackerY, rightX, targetY, groundY);

        this._projectileTween = this.scene.tweens.addCounter({
            from: 0,
            to: 1,
            duration: travelMs,
            delay: startDelayMs,
            ease: 'Linear',
            onUpdate: (tween) => {
                const t = tween.getValue();
                const x = isBombAttacker ? rightX : Phaser.Math.Linear(leftX, rightX, t);

                let y;
                if (!isBombAttacker) {
                    y = Phaser.Math.Linear(attackerY, targetY, t);
                } else {
                    y = Phaser.Math.Linear(bomberDropY + 8, targetY, t);
                }

                if (this._projectileImg) {
                    this._projectileImg.setPosition(x, y);
                }
            }
        });

        if (kind === 'missile' && this.scene?.sound) {
            this._missileLaunchSfx = this.scene.sound.add('missile_launch', { loop: true, volume: 0.6 });
            this._missileLaunchSfx.play();
        }
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
            return;
        }

        // Bomb: straight vertical drop over the target.
        this._trailGfx.beginPath();
        this._trailGfx.moveTo(endX, startY);
        this._trailGfx.lineTo(endX, endY);
        this._trailGfx.strokePath();
    }

    onAttackImpact(payload) {
        const kind = payload?.kind;
        if (!this.container.visible) return;
        if (kind !== this._attackKind) return;

        if (this._missileLaunchSfx) {
            this._missileLaunchSfx.stop();
            this._missileLaunchSfx.destroy();
            this._missileLaunchSfx = null;
        }

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
        this.scene.sound.play('explosion', { volume: 0.50 });

        this.scene.tweens.add({
            targets: this._explosionImg,
            alpha: 0,
            scale: 0.5,
            duration: 1920,
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

        // Hide after the attack finishes with a longer delay for better visibility.
        if (this._pendingHideTimer) {
            clearTimeout(this._pendingHideTimer);
        }
        this._pendingHideTimer = setTimeout(() => {
            this.hide();
        }, 1000);
    }
}
