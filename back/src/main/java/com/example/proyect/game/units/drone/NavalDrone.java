package com.example.proyect.game.units.drone;

import com.example.proyect.game.config.UnitBalanceRegistry;
import com.example.proyect.game.units.weapons.MissileWeapon;

public class NavalDrone extends Drone {

    public static final int DEFAULT_MISSILES = UnitBalanceRegistry.DEFAULT_NAVAL_DRONE_MISSILES;

    private int missiles;

    public NavalDrone() {
        super();

        setMaxHp(UnitBalanceRegistry.getNavalDroneMaxHp());
        setMovementRange(UnitBalanceRegistry.getNavalDroneMovementRange());
        setVisionRange(UnitBalanceRegistry.getNavalDroneVisionRange());
        setMaxFuel(UnitBalanceRegistry.getNavalDroneMaxFuel());
        setFuel(UnitBalanceRegistry.getNavalDroneMaxFuel());

        setWeapon(new MissileWeapon(UnitBalanceRegistry.getNavalDroneWeaponAmmo(), 50, 1, 0.75, 8));
        this.missiles = UnitBalanceRegistry.getNavalDroneMissiles();
    }

    public static int getConfiguredDefaultMissiles() {
        return UnitBalanceRegistry.getNavalDroneMissiles();
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
