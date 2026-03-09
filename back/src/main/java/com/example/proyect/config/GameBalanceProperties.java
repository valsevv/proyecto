package com.example.proyect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.example.proyect.game.config.UnitBalanceRegistry;

@Component
@ConfigurationProperties(prefix = "game")
public class GameBalanceProperties {

    private int actionsPerTurn = 12;
    private int missileMaxDistance = 15;
    private int aerialAttackFuelCost = 2;
    private int carrierHitsToDestroy = 5;
    private Units units = new Units();

    public int getActionsPerTurn() {
        return actionsPerTurn;
    }

    public void setActionsPerTurn(int actionsPerTurn) {
        this.actionsPerTurn = actionsPerTurn;
    }

    public int getMissileMaxDistance() {
        return missileMaxDistance;
    }

    public void setMissileMaxDistance(int missileMaxDistance) {
        this.missileMaxDistance = missileMaxDistance;
    }

    public int getAerialAttackFuelCost() {
        return aerialAttackFuelCost;
    }

    public void setAerialAttackFuelCost(int aerialAttackFuelCost) {
        this.aerialAttackFuelCost = aerialAttackFuelCost;
    }

    public int getCarrierHitsToDestroy() {
        return carrierHitsToDestroy;
    }

    public void setCarrierHitsToDestroy(int carrierHitsToDestroy) {
        this.carrierHitsToDestroy = carrierHitsToDestroy;
    }

    public Units getUnits() {
        return units;
    }

    public void setUnits(Units units) {
        this.units = units;
    }

    public static class Units {
        private Aereo aereo = new Aereo();
        private Naval naval = new Naval();
        private Drone drone = new Drone();

        public Aereo getAereo() {
            return aereo;
        }

        public void setAereo(Aereo aereo) {
            this.aereo = aereo;
        }

        public Naval getNaval() {
            return naval;
        }

        public void setNaval(Naval naval) {
            this.naval = naval;
        }

        public Drone getDrone() {
            return drone;
        }

        public void setDrone(Drone drone) {
            this.drone = drone;
        }
    }

    public static class Aereo {
        private int maxHp = UnitBalanceRegistry.DEFAULT_AERIAL_DRONE_MAX_HP;
        private int movementRange = UnitBalanceRegistry.DEFAULT_AERIAL_DRONE_MOVEMENT_RANGE;
        private int visionRange = UnitBalanceRegistry.DEFAULT_AERIAL_DRONE_VISION_RANGE;
        private int maxFuel = UnitBalanceRegistry.DEFAULT_AERIAL_DRONE_MAX_FUEL;
        private int ammo = UnitBalanceRegistry.DEFAULT_AERIAL_DRONE_AMMO;
        private double accuracy = UnitBalanceRegistry.DEFAULT_AERIAL_DRONE_ACCURACY;
        private int carrierHp = UnitBalanceRegistry.DEFAULT_AERIAL_CARRIER_MAX_HP;
        private int carrierMovementRange = UnitBalanceRegistry.DEFAULT_AERIAL_CARRIER_MOVEMENT_RANGE;
        private int carrierVisionRange = UnitBalanceRegistry.DEFAULT_AERIAL_DRONE_VISION_RANGE;

        public int getMaxHp() {
            return maxHp;
        }

        public void setMaxHp(int maxHp) {
            this.maxHp = maxHp;
        }

        public int getMovementRange() {
            return movementRange;
        }

        public void setMovementRange(int movementRange) {
            this.movementRange = movementRange;
        }

        public int getVisionRange() {
            return visionRange;
        }

        public void setVisionRange(int visionRange) {
            this.visionRange = visionRange;
        }

        public int getMaxFuel() {
            return maxFuel;
        }

        public void setMaxFuel(int maxFuel) {
            this.maxFuel = maxFuel;
        }

        public int getAmmo() {
            return ammo;
        }

        public void setAmmo(int ammo) {
            this.ammo = ammo;
        }

        public double getAccuracy() {
            return accuracy;
        }

        public void setAccuracy(double accuracy) {
            this.accuracy = accuracy;
        }

        public int getCarrierHp() {
            return carrierHp;
        }

        public void setCarrierHp(int carrierHp) {
            this.carrierHp = carrierHp;
        }

        public int getCarrierMovementRange() {
            return carrierMovementRange;
        }

        public void setCarrierMovementRange(int carrierMovementRange) {
            this.carrierMovementRange = carrierMovementRange;
        }

        public int getCarrierVisionRange() {
            return carrierVisionRange;
        }

        public void setCarrierVisionRange(int carrierVisionRange) {
            this.carrierVisionRange = carrierVisionRange;
        }
    }

    public static class Naval {
        private int maxHp = UnitBalanceRegistry.DEFAULT_NAVAL_DRONE_MAX_HP;
        private int movementRange = UnitBalanceRegistry.DEFAULT_NAVAL_DRONE_MOVEMENT_RANGE;
        private int visionRange = UnitBalanceRegistry.DEFAULT_NAVAL_DRONE_VISION_RANGE;
        private int maxFuel = UnitBalanceRegistry.DEFAULT_NAVAL_DRONE_MAX_FUEL;
        private int weaponAmmo = UnitBalanceRegistry.DEFAULT_NAVAL_DRONE_WEAPON_AMMO;
        private int missiles = UnitBalanceRegistry.DEFAULT_NAVAL_DRONE_MISSILES;
        private double accuracy = UnitBalanceRegistry.DEFAULT_NAVAL_DRONE_ACCURACY;
        private int carrierHp = UnitBalanceRegistry.DEFAULT_NAVAL_CARRIER_MAX_HP;
        private int carrierMovementRange = UnitBalanceRegistry.DEFAULT_NAVAL_CARRIER_MOVEMENT_RANGE;
        private int carrierVisionRange = UnitBalanceRegistry.DEFAULT_NAVAL_DRONE_VISION_RANGE;

        public int getMaxHp() {
            return maxHp;
        }

        public void setMaxHp(int maxHp) {
            this.maxHp = maxHp;
        }

        public int getMovementRange() {
            return movementRange;
        }

        public void setMovementRange(int movementRange) {
            this.movementRange = movementRange;
        }

        public int getVisionRange() {
            return visionRange;
        }

        public void setVisionRange(int visionRange) {
            this.visionRange = visionRange;
        }

        public int getMaxFuel() {
            return maxFuel;
        }

        public void setMaxFuel(int maxFuel) {
            this.maxFuel = maxFuel;
        }

        public int getWeaponAmmo() {
            return weaponAmmo;
        }

        public void setWeaponAmmo(int weaponAmmo) {
            this.weaponAmmo = weaponAmmo;
        }

        public int getMissiles() {
            return missiles;
        }

        public void setMissiles(int missiles) {
            this.missiles = missiles;
        }

        public double getAccuracy() {
            return accuracy;
        }

        public void setAccuracy(double accuracy) {
            this.accuracy = accuracy;
        }

        public int getCarrierHp() {
            return carrierHp;
        }

        public void setCarrierHp(int carrierHp) {
            this.carrierHp = carrierHp;
        }

        public int getCarrierMovementRange() {
            return carrierMovementRange;
        }

        public void setCarrierMovementRange(int carrierMovementRange) {
            this.carrierMovementRange = carrierMovementRange;
        }

        public int getCarrierVisionRange() {
            return carrierVisionRange;
        }

        public void setCarrierVisionRange(int carrierVisionRange) {
            this.carrierVisionRange = carrierVisionRange;
        }
    }

    public static class Drone {
        private int attackDamage = UnitBalanceRegistry.DEFAULT_DRONE_ATTACK_DAMAGE;

        public int getAttackDamage() {
            return attackDamage;
        }

        public void setAttackDamage(int attackDamage) {
            this.attackDamage = attackDamage;
        }
    }
}
