export function showPlayerLeftOverlay(scene) {
    if (scene.playerLeftOverlay) {
        scene.playerLeftOverlay.setVisible(true);
        return;
    }

    const w = scene.scale?.width ?? 800;
    const h = scene.scale?.height ?? 600;
    const cx = w / 2;
    const cy = h / 2;

    // Highest depth to ensure it is above EVERYTHING
    const overlay = scene.add.container(0, 0).setDepth(10000).setScrollFactor(0);

    // Full screen semi-transparent background to block interaction with game
    const screenBlocker = scene.add.graphics();
    screenBlocker.fillStyle(0x000000, 0.7);
    screenBlocker.fillRect(0, 0, w, h);
    overlay.add(screenBlocker);

    // Main Banner in the center
    const bannerW = 600;
    const bannerH = 120;
    const bannerX = cx - bannerW / 2;
    const bannerY = cy - bannerH / 2;

    const bannerBg = scene.add.graphics();
    bannerBg.fillStyle(0x0b1416, 0.95);
    bannerBg.fillRoundedRect(bannerX, bannerY, bannerW, bannerH, 16);
    bannerBg.lineStyle(3, 0x26f0a7, 1);
    bannerBg.strokeRoundedRect(bannerX, bannerY, bannerW, bannerH, 16);
    overlay.add(bannerBg);

    const bannerText = scene.add.text(cx, cy - 15, 'OPONENTE DESCONECTADO', {
        fontSize: '28px',
        fill: '#e7fff6',
        fontFamily: '"Orbitron", "Share Tech Mono", monospace',
        fontStyle: 'bold'
    }).setOrigin(0.5);
    overlay.add(bannerText);

    const subText = scene.add.text(cx, cy + 25, 'Volviendo al lobby en unos segundos...', {
        fontSize: '16px',
        fill: '#9bb3ad',
        fontFamily: '"Share Tech Mono", monospace'
    }).setOrigin(0.5);
    overlay.add(subText);

    scene.playerLeftOverlay = overlay;

    // Automatic redirection after 3 seconds
    scene.time.delayedCall(3000, () => {
        window.location.href = '/lobby-browser';
    });
}
