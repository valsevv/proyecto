package com.example.proyect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "game")
public class GameProperties {

    private final Turn turn = new Turn();
    private final Vision vision = new Vision();
    private final Missile missile = new Missile();
    private final Attack attack = new Attack();
    private final Carrier carrier = new Carrier();

    public Turn getTurn() {
        return turn;
    }

    public Vision getVision() {
        return vision;
    }

    public Missile getMissile() {
        return missile;
    }

    public Attack getAttack() {
        return attack;
    }

    public Carrier getCarrier() {
        return carrier;
    }

    public static class Turn {
        private int actionsPerTurn = 10;

        public int getActionsPerTurn() {
            return actionsPerTurn;
        }

        public void setActionsPerTurn(int actionsPerTurn) {
            this.actionsPerTurn = actionsPerTurn;
        }
    }

    public static class Vision {
        private int naval = 3;
        private int aereo = 4;

        public int getNaval() {
            return naval;
        }

        public void setNaval(int naval) {
            this.naval = naval;
        }

        public int getAereo() {
            return aereo;
        }

        public void setAereo(int aereo) {
            this.aereo = aereo;
        }
    }

    public static class Missile {
        private int maxDistance = 15;
        private double damagePercentOnNaval = 0.5;

        public int getMaxDistance() {
            return maxDistance;
        }

        public void setMaxDistance(int maxDistance) {
            this.maxDistance = maxDistance;
        }

        public double getDamagePercentOnNaval() {
            return damagePercentOnNaval;
        }

        public void setDamagePercentOnNaval(double damagePercentOnNaval) {
            this.damagePercentOnNaval = damagePercentOnNaval;
        }
    }

    public static class Attack {
        private int aerialFuelCost = 2;

        public int getAerialFuelCost() {
            return aerialFuelCost;
        }

        public void setAerialFuelCost(int aerialFuelCost) {
            this.aerialFuelCost = aerialFuelCost;
        }
    }

    public static class Carrier {
        private int hitsToDestroy = 5;
        private int aerialHitsToDestroy = 6;
        private int navalHitsToDestroy = 3;

        public int getHitsToDestroy() {
            return hitsToDestroy;
        }

        public void setHitsToDestroy(int hitsToDestroy) {
            this.hitsToDestroy = hitsToDestroy;
        }

        public int getAerialHitsToDestroy() {
            return aerialHitsToDestroy;
        }

        public void setAerialHitsToDestroy(int aerialHitsToDestroy) {
            this.aerialHitsToDestroy = aerialHitsToDestroy;
        }

        public int getNavalHitsToDestroy() {
            return navalHitsToDestroy;
        }

        public void setNavalHitsToDestroy(int navalHitsToDestroy) {
            this.navalHitsToDestroy = navalHitsToDestroy;
        }
    }
}
