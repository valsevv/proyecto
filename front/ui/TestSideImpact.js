import TestScene from "TestScene.js";

const config = {
  type: Phaser.AUTO, // Phaser decide WebGL o Canvas
  width: 800, // ancho del juego
  height: 600, // alto del juego
  backgroundColor: "#000000",
  parent: "game-container", // id del div en tu HTML
  scene: [TestScene] // escenas que va a usar
};

new Phaser.Game(config);
