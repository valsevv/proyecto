import { WORLD_WIDTH, WORLD_HEIGHT } from '../shared/constants.js';

/**
 * Minimap - Radar circular estilo barco/avión
 * 
 * Dibuja un círculo en la esquina inferior derecha que representa
 * el mundo completo. Muestra:
 *   - Fondo oscuro semi-transparente con líneas de "barrido" estilo radar
 *   - Punto verde para el dron
 *   - Rectángulo para la vista actual de la cámara
 * 
 * Al hacer click en el minimap, la cámara se mueve a esa zona del mundo.
 */
export default class Minimap {
    constructor(scene, x, y, radius) {
        this.scene = scene;
        this.cx = x;
        this.cy = y;
        this.radius = radius;

        // Escala del mundo al minimap
        this.scaleX = (radius * 2) / WORLD_WIDTH;
        this.scaleY = (radius * 2) / WORLD_HEIGHT;

        this.createBackground();
        this.createElements();
        this.createMask();
        this.createInteraction();
    }

    createBackground() {
        // Fondo del radar
        this.bg = this.scene.add.graphics();
        this.bg.fillStyle(0x001a00, 0.6);
        this.bg.fillCircle(this.cx, this.cy, this.radius);

        // Borde
        this.bg.lineStyle(2, 0x00ff00, 0.45);
        this.bg.strokeCircle(this.cx, this.cy, this.radius);

        // Líneas de cuadrícula del radar (cruz)
        this.bg.lineStyle(1, 0x00ff00, 0.1);
        this.bg.lineBetween(this.cx - this.radius, this.cy, this.cx + this.radius, this.cy);
        this.bg.lineBetween(this.cx, this.cy - this.radius, this.cx, this.cy + this.radius);

        // Círculos concéntricos
        this.bg.lineStyle(1, 0x00ff00, 0.07);
        this.bg.strokeCircle(this.cx, this.cy, this.radius * 0.33);
        this.bg.strokeCircle(this.cx, this.cy, this.radius * 0.66);
    }

    createElements() {
        this.elementsGfx = this.scene.add.graphics();
    }

    createMask() {
        // Máscara circular para recortar todo dentro del radar
        const maskShape = this.scene.make.graphics({ x: 0, y: 0, add: false });
        maskShape.fillStyle(0xffffff);
        maskShape.fillCircle(this.cx, this.cy, this.radius);
        const mask = maskShape.createGeometryMask();
        this.elementsGfx.setMask(mask);
    }

    createInteraction() {
        // Zona interactiva circular
        const hitArea = this.scene.add.circle(this.cx, this.cy, this.radius, 0x000000, 0.001);
        hitArea.setInteractive({ useHandCursor: true });

        hitArea.on('pointerdown', (pointer) => {
            pointer.event.stopPropagation();
            this.isMinimapDragging = true;
            this.dragStartX = pointer.x;
            this.dragStartY = pointer.y;
            this.dragConfirmed = false;
            this.moveCameraTo(pointer);
        });

        this.scene.input.on('pointermove', (pointer) => {
            if (!this.isMinimapDragging || !pointer.isDown) return;

            // Requiere al menos 3px de movimiento para confirmar el drag
            if (!this.dragConfirmed) {
                const dx = pointer.x - this.dragStartX;
                const dy = pointer.y - this.dragStartY;
                if (Math.abs(dx) < 3 && Math.abs(dy) < 3) return;
                this.dragConfirmed = true;
            }

            this.moveCameraTo(pointer);
        });

        this.scene.input.on('pointerup', () => {
            this.isMinimapDragging = false;
        });
    }

    moveCameraTo(pointer) {
        // Convertir posición en el minimap a coordenadas del mundo
        const offsetX = pointer.x - (this.cx - this.radius);
        const offsetY = pointer.y - (this.cy - this.radius);
        const worldX = offsetX / this.scaleX;
        const worldY = offsetY / this.scaleY;

        // Mover cámara del MainScene a esa posición
        const mainScene = this.scene.scene.get('MainScene');
        if (mainScene) {
            mainScene.cameras.main.centerOn(worldX, worldY);
        }
    }

    /**
     * Convierte coordenada del mundo a posición en el minimap
     */
    worldToMinimap(wx, wy) {
        const mx = (this.cx - this.radius) + wx * this.scaleX;
        const my = (this.cy - this.radius) + wy * this.scaleY;
        return { x: mx, y: my };
    }

    update(camera, trackedObjects) {
        this.elementsGfx.clear();

        // Dibujar vista de la cámara (rectángulo verde)
        const camPos = this.worldToMinimap(camera.scrollX, camera.scrollY);
        const camW = camera.width * this.scaleX;
        const camH = camera.height * this.scaleY;
        this.elementsGfx.lineStyle(1, 0x00ff00, 0.6);
        this.elementsGfx.strokeRect(camPos.x, camPos.y, camW, camH);

        // Dibujar objetos rastreados (puntos brillantes)
        for (const obj of trackedObjects) {
            const pos = this.worldToMinimap(obj.x, obj.y);
            this.elementsGfx.fillStyle(obj.color || 0x00ff00, 1);
            this.elementsGfx.fillCircle(pos.x, pos.y, 3);
        }
    }
}
