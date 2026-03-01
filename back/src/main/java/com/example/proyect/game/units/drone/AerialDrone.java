package com.example.proyect.game.units.drone;

import com.example.proyect.game.units.weapons.BombWeapon;

public class AerialDrone extends Drone {

    public AerialDrone() {
        super();

        // Ajustes típicos (podés tunear)
        setMovementRange(2);
        setVisionRange(4);

        // Arma por defecto: bombas
        setWeapon(new BombWeapon(6, 45, 1, 0.60, 4));
    }
}
