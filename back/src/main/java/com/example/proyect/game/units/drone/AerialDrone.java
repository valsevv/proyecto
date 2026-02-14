package com.example.proyect.game.units.drone;

import com.example.proyect.game.units.weapons.MissileWeapon;

public class AerialDrone extends Drone {

    public AerialDrone() {
        super();

        // Ajustes típicos (podés tunear)
        setMovementRange(3);
        setVisionRange(4);

        // Arma por defecto: misiles
        setWeapon(new MissileWeapon(10, 30, 1, 0.75, 4));
    }
}
