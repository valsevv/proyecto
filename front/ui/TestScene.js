import SideImpactView from "./SideImpactView.js";

export default class TestScene extends Phaser.Scene {
  constructor() {
    super("TestScene");
  }

  preload() {
    this.load.image("mar", "assets/mar.png");
    this.load.image("vista_lateral_fondo", "assets/bg.png");

    this.load.image("dron_bomba_0", "assets/dron.png");
    this.load.image("misil_2", "assets/misil.png");
    this.load.image("bomba", "assets/bomba.png");
    this.load.image("explosion", "assets/explosion.png");

    this.load.audio("missile_launch", "assets/missile.mp3");
    this.load.audio("explosion", "assets/explosion.mp3");
  }

  create() {
    this.sideView = new SideImpactView(this, 400, 300, 400, 260);

    // Lanza prueba automática
    this.time.delayedCall(500, () => {
      this.sideView.onAttackStart({ kind: "missile" });
    });

    this.time.delayedCall(2500, () => {
      this.sideView.onAttackImpact({ kind: "missile" });
    });

    this.time.delayedCall(4000, () => {
      this.sideView.onAttackEnd({ kind: "missile" });
    });
  }
}
