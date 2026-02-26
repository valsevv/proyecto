package com.example.proyect.game.units.drone;

import com.example.proyect.game.units.weapons.Weapon;
import com.example.proyect.game.units.Unit;

public abstract class Drone extends Unit {

    private int visionRange;
    private Weapon weapon;
    private int maxFuel = 10;
    private int fuel = 10;

    public Drone() {
        super();
    }

    public int getVisionRange() {
        return visionRange;
    }

    public void setVisionRange(int visionRange) {
        if (visionRange < 0) {
            throw new IllegalArgumentException("visionRange no puede ser negativo");
        }
        this.visionRange = visionRange;
    }

    public Weapon getWeapon() {
        return weapon;
    }

    public int getMaxFuel() {
        return maxFuel;
    }

    public int getFuel() {
        return fuel;
    }

    protected void setWeapon(Weapon weapon) {
        if (weapon == null) {
            throw new IllegalArgumentException("weapon requerida");
        }
        this.weapon = weapon;
    }

    public void setMaxFuel(int maxFuel) {
        if (maxFuel < 0) {
            throw new IllegalArgumentException("maxFuel no puede ser negativo");
        }
        this.maxFuel = maxFuel;
        if (fuel > maxFuel) {
            fuel = maxFuel;
        }
    }

    public void setFuel(int fuel) {
        if (fuel < 0 || fuel > maxFuel) {
            throw new IllegalArgumentException("fuel fuera de rango");
        }
        this.fuel = fuel;
    }

    public void consumeFuel(int amount) {
        if (amount <= 0 || !isAlive()) {
            return;
        }
        fuel = Math.max(0, fuel - amount);
    }
}
