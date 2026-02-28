package com.example.proyect.game.units.weapons;

public class MissileWeapon extends Weapon {

    public MissileWeapon(int ammo, int damage, int shotsPerTurn, double accuracy, int range) {
        super(ammo, damage, shotsPerTurn, accuracy, range);
    }

    public double getEffectiveAccuracy(double traveledDistance) {
        if (traveledDistance <= 0) {
            return getAccuracy();
        }

        double normalizedDistance = Math.min(traveledDistance, getRange()) / getRange();
        double effectiveAccuracy = getAccuracy() * (1.0 - normalizedDistance);
        return Math.max(0.0, effectiveAccuracy);
    }

    public boolean canReach(double traveledDistance) {
        return traveledDistance >= 0 && traveledDistance <= getRange();
    }
}
