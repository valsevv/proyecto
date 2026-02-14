package com.example.proyect.game.units.drone;

import com.example.proyect.game.units.weapons.BombWeapon;

public class NavalDrone extends Drone {

    public NavalDrone() {
        super();

        // Ajustes típicos (podés tunear)
        setMovementRange(2);
        setVisionRange(3);

        // Arma por defecto: bombas
        setWeapon(new BombWeapon(6, 45, 1, 0.60, 2));
    }
}
