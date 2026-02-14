package com.example.proyect.game.units.drone;

import com.example.proyect.game.units.weapons.Weapon;
import com.example.proyect.game.units.Unit;

public abstract class Drone extends Unit {

    private int visionRange;
    private Weapon weapon;

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

    protected void setWeapon(Weapon weapon) {
        if (weapon == null) {
            throw new IllegalArgumentException("weapon requerida");
        }
        this.weapon = weapon;
    }
}
