/**
 * Sistema de cuadricula hexagonal
 * 
 * Geometría del hexágono:
 *   - Cada hexágono tiene un "size" que es la distancia del centro a cualquier vértice.
 *   - El ancho de un hexágono es: w = √3 × size
 *   - La altura de un hexágono es: h = 2 × size
 * 
 * Disposición en la cuadricula:
 *   - Las filas se separan verticalmente (y) por 3/4 de la altura (h × 0.75)
 *   - Las filas impares se desplazan horizontalmente medio ancho (w / 2)
 *     para crear el patrón de hexagonos.
 * 
 * Dibujo de cada hexágono:
 *   - Se calculan 6 vértices usando ángulos de 60° entre sí, comenzando en -30°
 *     para lograr la orientación "pointy-top".
 *   - Fórmula de cada vértice:
 *       x = centro_x + size × cos(60° × i - 30°)
 *       y = centro_y + size × sin(60° × i - 30°)
 * 
 * Búsqueda del hexágono más cercano:
 *   - Se almacenan todos los centros de los hexágonos en un array.
 *   - Para encontrar el hexágono más cercano a un punto (click del mouse),
 *     se calcula la distancia euclidiana a cada centro y se devuelve el menor.
 */
export default class HexGrid {
    constructor(scene, size, worldWidth, worldHeight) {
        this.scene = scene;
        this.size = size;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.centers = [];
    }

    /**
     * Dibuja la grilla completa de hexágonos en el mundo.
     * Calcula cuántas columnas y filas son necesarias para cubrir
     * todo el área del mundo y dibuja cada hexágono.
     */
    draw() {
        const size = this.size;
        const w = Math.sqrt(3) * size;  // Ancho de cada hexágono
        const h = 2 * size;             // Altura de cada hexágono
        const graphics = this.scene.add.graphics();
        graphics.lineStyle(1, 0xffffff, 0.15);

        const cols = Math.ceil(this.worldWidth / w) + 1;
        const rows = Math.ceil(this.worldHeight / (h * 0.75)) + 1;

        for (let row = 0; row < rows; row++) {
            for (let col = 0; col < cols; col++) {
                // Las filas impares se desplazan medio ancho para el patrón de hexagonos
                const x = col * w + (row % 2 === 1 ? w / 2 : 0); // Si queres ver la razon, comenta la ultima suma
                // Las filas se separan 3/4 de la altura
                const y = row * h * 0.75;
                this.drawHex(graphics, x, y, size);
                this.centers.push({ x, y });
            }
        }
    }

    /**
     * Dibuja un hexágono individual con 6 vértices.
     * Los ángulos comienzan en -30°
     */
    drawHex(graphics, cx, cy, size) {
        const points = [];
        for (let i = 0; i < 6; i++) {
            const angle = Phaser.Math.DegToRad(60 * i - 30);
            points.push({
                x: cx + size * Math.cos(angle),
                y: cy + size * Math.sin(angle)
            });
        }
        graphics.beginPath();
        graphics.moveTo(points[0].x, points[0].y);
        for (let i = 1; i < 6; i++) {
            graphics.lineTo(points[i].x, points[i].y);
        }
        graphics.closePath();
        graphics.strokePath();
    }

    /**
     * Dado un punto (px, py), devuelve el centro del hexágono más cercano.
     */
    getNearestCenter(px, py) {
        let closest = this.centers[0];
        let minDist = Infinity; // para que inicialmente cualquier distancia sea menor
        for (const hex of this.centers) {
            const d = Phaser.Math.Distance.Between(px, py, hex.x, hex.y);
            if (d < minDist) {
                minDist = d;
                closest = hex;
            }
        }
        return closest;
    }

    /**
     * Calculate approximate hex distance between two pixel positions.
     * Uses pixel distance divided by hex width as approximation.
     */
    getHexDistance(x1, y1, x2, y2) {
        const pixelDist = Math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2);
        const hexWidth = Math.sqrt(3) * this.size;
        return Math.round(pixelDist / hexWidth);
    }

    /**
     * Draw a filled hexagon at the given center with specified color and alpha.
     * @param {Phaser.GameObjects.Graphics} graphics - The graphics object to draw on
     * @param {number} cx - Center X coordinate
     * @param {number} cy - Center Y coordinate
     * @param {number} color - Fill color (e.g., 0x00ff00)
     * @param {number} alpha - Fill alpha (0-1)
     */
    drawFilledHex(graphics, cx, cy, color, alpha) {
        const points = [];
        for (let i = 0; i < 6; i++) {
            const angle = Phaser.Math.DegToRad(60 * i - 30);
            points.push({
                x: cx + this.size * Math.cos(angle),
                y: cy + this.size * Math.sin(angle)
            });
        }
        graphics.fillStyle(color, alpha);
        graphics.beginPath();
        graphics.moveTo(points[0].x, points[0].y);
        for (let i = 1; i < 6; i++) {
            graphics.lineTo(points[i].x, points[i].y);
        }
        graphics.closePath();
        graphics.fillPath();
    }
}
