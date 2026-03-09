package com.example.proyect.game.units.drone;

import com.example.proyect.game.config.UnitBalanceRegistry;
import com.example.proyect.game.units.weapons.BombWeapon;

public class AerialDrone extends Drone {

    public AerialDrone() {
        super();

        setMaxHp(UnitBalanceRegistry.getAerialDroneMaxHp());
        setMovementRange(UnitBalanceRegistry.getAerialDroneMovementRange());
        setVisionRange(UnitBalanceRegistry.getAerialDroneVisionRange());
        setMaxFuel(UnitBalanceRegistry.getAerialDroneMaxFuel());
        setFuel(UnitBalanceRegistry.getAerialDroneMaxFuel());

        setWeapon(new BombWeapon(UnitBalanceRegistry.getAerialDroneAmmo(), 45, 1, UnitBalanceRegistry.getAerialDroneAccuracy(), 4));
    }
}
