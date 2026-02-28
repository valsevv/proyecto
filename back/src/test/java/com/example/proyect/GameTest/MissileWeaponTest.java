package com.example.proyect.GameTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.proyect.game.units.drone.AerialDrone;
import com.example.proyect.game.units.weapons.MissileWeapon;

class MissileWeaponTest {

    @Test
    void shouldDecreaseAccuracyAsDistanceIncreases() {
        MissileWeapon weapon = new MissileWeapon(2, 50, 1, 0.8, 15);

        assertEquals(0.8, weapon.getEffectiveAccuracy(0), 0.0001);
        assertEquals(0.4, weapon.getEffectiveAccuracy(7.5), 0.0001);
        assertEquals(0.0, weapon.getEffectiveAccuracy(15), 0.0001);
    }

    @Test
    void shouldRespectMissileStockInAerialDrone() {
        AerialDrone drone = new AerialDrone();

        assertEquals(AerialDrone.DEFAULT_MISSILES, drone.getMissiles());
        assertTrue(drone.hasMissiles());

        drone.consumeMissile();
        drone.consumeMissile();

        assertEquals(0, drone.getMissiles());
        assertFalse(drone.hasMissiles());
    }
}
