package com.example.proyect.game.units.drone;

import com.example.proyect.game.units.weapons.MissileWeapon;

public class AerialDrone extends Drone {

    public static final int DEFAULT_MISSILES = 2;

    private int missiles;

    public AerialDrone() {
        super();

        // Ajustes típicos (podés tunear)
        setMovementRange(3);
        setVisionRange(4);

        // Arma por defecto: misiles
        setWeapon(new MissileWeapon(10, 50, 1, 0.75, 15));
        this.missiles = DEFAULT_MISSILES;
    }

    public int getMissiles() {
        return missiles;
    }

    public void setMissiles(int missiles) {
        if (missiles < 0) {
            throw new IllegalArgumentException("missiles no puede ser negativo");
        }
        this.missiles = missiles;
    }

    public boolean hasMissiles() {
        return missiles > 0;
    }

    public void consumeMissile() {
        if (!hasMissiles()) {
            throw new IllegalStateException("No hay misiles disponibles");
        }
        missiles--;
    }
}
