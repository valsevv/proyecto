package com.example.proyect.game;

/**
 * Server-side state for a single drone.
 */
public class DroneState {

    // Position
    private double x;
    private double y;

    // Combat stats
    private int health;
    private int maxHealth;
    private int attackDamage;
    private int attackRange;

    // Defaults
    private static final int DEFAULT_HEALTH = 100;
    private static final int DEFAULT_ATTACK_DAMAGE = 25;
    private static final int DEFAULT_ATTACK_RANGE = 3; // hex tiles

    public DroneState(double x, double y) {
        this.x = x;
        this.y = y;
        this.health = DEFAULT_HEALTH;
        this.maxHealth = DEFAULT_HEALTH;
        this.attackDamage = DEFAULT_ATTACK_DAMAGE;
        this.attackRange = DEFAULT_ATTACK_RANGE;
    }

    // Position getters/setters
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    // Combat getters/setters
    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = Math.max(0, health); }

    public int getMaxHealth() { return maxHealth; }

    public int getAttackDamage() { return attackDamage; }
    public void setAttackDamage(int attackDamage) { this.attackDamage = attackDamage; }

    public int getAttackRange() { return attackRange; }
    public void setAttackRange(int attackRange) { this.attackRange = attackRange; }

    // Combat helpers
    public boolean isAlive() {
        return health > 0;
    }

    public void takeDamage(int damage) {
        setHealth(health - damage);
    }
}
