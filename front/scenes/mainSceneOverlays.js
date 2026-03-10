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

    const subText = scene.add.text(cx, cy + 25, 'Volviendo al menu en 3 segundos...', {
        fontSize: '16px',
        fill: '#9bb3ad',
        fontFamily: '"Share Tech Mono", monospace'
    }).setOrigin(0.5);
    overlay.add(subText);

    scene.playerLeftOverlay = overlay;

    // Automatic redirection after 3 seconds
    scene.time.delayedCall(3000, () => {
        window.location.href = '/menu';
    });
}

export function showMatchResultOverlay(scene, { result, isDraw } = {}) {
    if (scene.matchResultOverlay) {
        scene.matchResultOverlay.setVisible(true);
        return;
    }

    const w = scene.scale?.width ?? 800;
    const h = scene.scale?.height ?? 600;
    const cx = w / 2;
    const cy = h / 2;

    const overlay = scene.add.container(0, 0).setDepth(10000).setScrollFactor(0);

    const screenBlocker = scene.add.graphics();
    screenBlocker.fillStyle(0x000000, 0.72);
    screenBlocker.fillRect(0, 0, w, h);
    overlay.add(screenBlocker);

    const bannerW = 680;
    const bannerH = 160;
    const bannerX = cx - bannerW / 2;
    const bannerY = cy - bannerH / 2;

    const isWin = result === 'win';
    const isResultDraw = Boolean(isDraw || result === 'draw');
    const accent = isResultDraw ? 0xffc107 : (isWin ? 0x35d07f : 0xe35d5d);
    const bannerLabel = isResultDraw ? 'EMPATE' : (isWin ? 'VICTORIA' : 'DERROTA');

    const bannerBg = scene.add.graphics();
    bannerBg.fillStyle(0x10141a, 0.96);
    bannerBg.fillRoundedRect(bannerX, bannerY, bannerW, bannerH, 16);
    bannerBg.lineStyle(3, accent, 1);
    bannerBg.strokeRoundedRect(bannerX, bannerY, bannerW, bannerH, 16);
    overlay.add(bannerBg);

    const bannerText = scene.add.text(cx, cy - 16, bannerLabel, {
        fontSize: '42px',
        fill: '#fff8e1',
        fontFamily: '"Orbitron", "Share Tech Mono", monospace',
        fontStyle: 'bold'
    }).setOrigin(0.5);
    overlay.add(bannerText);

    const subText = scene.add.text(cx, cy + 32, 'Volviendo al menu principal...', {
        fontSize: '16px',
        fill: '#c5cbd1',
        fontFamily: '"Share Tech Mono", monospace'
    }).setOrigin(0.5);
    overlay.add(subText);

    scene.matchResultOverlay = overlay;
}

export function showGameForfeitOverlay(scene, isLocalForfeiter) {
    if (scene.gameForfeitOverlay) {
        scene.gameForfeitOverlay.setVisible(true);
        return;
    }

    const w = scene.scale?.width ?? 800;
    const h = scene.scale?.height ?? 600;
    const cx = w / 2;
    const cy = h / 2;

    const overlay = scene.add.container(0, 0).setDepth(10000).setScrollFactor(0);

    const screenBlocker = scene.add.graphics();
    screenBlocker.fillStyle(0x000000, 0.72);
    screenBlocker.fillRect(0, 0, w, h);
    overlay.add(screenBlocker);

    const bannerW = 700;
    const bannerH = 150;
    const bannerX = cx - bannerW / 2;
    const bannerY = cy - bannerH / 2;

    const bannerBg = scene.add.graphics();
    bannerBg.fillStyle(0x10141a, 0.96);
    bannerBg.fillRoundedRect(bannerX, bannerY, bannerW, bannerH, 16);
    bannerBg.lineStyle(3, 0xffc107, 1);
    bannerBg.strokeRoundedRect(bannerX, bannerY, bannerW, bannerH, 16);
    overlay.add(bannerBg);

    const title = isLocalForfeiter ? 'ABANDONASTE LA PARTIDA' : 'EL OPONENTE ABANDONO';
    const subtitle = isLocalForfeiter
        ? 'Se registrara una derrota para tu cuenta'
        : 'Se registrara una victoria para tu cuenta';

    const titleText = scene.add.text(cx, cy - 18, title, {
        fontSize: '30px',
        fill: '#fff8e1',
        fontFamily: '"Orbitron", "Share Tech Mono", monospace',
        fontStyle: 'bold'
    }).setOrigin(0.5);
    overlay.add(titleText);

    const subtitleText = scene.add.text(cx, cy + 24, subtitle, {
        fontSize: '17px',
        fill: '#d7d7d7',
        fontFamily: '"Share Tech Mono", monospace'
    }).setOrigin(0.5);
    overlay.add(subtitleText);

    const footerText = scene.add.text(cx, cy + 55, 'Volviendo al menu principal...', {
        fontSize: '14px',
        fill: '#9ea7ad',
        fontFamily: '"Share Tech Mono", monospace'
    }).setOrigin(0.5);
    overlay.add(footerText);

    scene.gameForfeitOverlay = overlay;

    scene.time.delayedCall(3000, () => {
        window.location.href = '/menu';
    });
}
