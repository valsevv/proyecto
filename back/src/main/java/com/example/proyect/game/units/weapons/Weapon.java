package com.example.proyect.game.units.weapons;

/**
 * Weapon (arma) para juego por turnos.
 * Mantiene munición, daño, disparos por turno, precisión y alcance.
 *
 * Nota: la resolución del impacto (random) debería vivir en GameSession/CombatResolver,
 * no dentro de Weapon.
 */
public class Weapon {

    private int ammo;
    private int damage;
    private int shotsPerTurn;
    private double accuracy; // 0.0 .. 1.0
    private int range;       // alcance en celdas hex

    public Weapon() {
        // valores por defecto razonables (podés cambiarlos)
        this.ammo = 0;
        this.damage = 0;
        this.shotsPerTurn = 1;
        this.accuracy = 1.0;
        this.range = 1;
    }

    public Weapon(int ammo, int damage, int shotsPerTurn, double accuracy, int range) {
        setAmmo(ammo);
        setDamage(damage);
        setShotsPerTurn(shotsPerTurn);
        setAccuracy(accuracy);
        setRange(range);
    }

    // =========================
    // Getters
    // =========================

    public int getAmmo() {
        return ammo;
    }

    public int getDamage() {
        return damage;
    }

    public int getShotsPerTurn() {
        return shotsPerTurn;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public int getRange() {
        return range;
    }

    // =========================
    // Setters controlados
    // =========================

    public void setAmmo(int ammo) {
        if (ammo < 0) throw new IllegalArgumentException("ammo no puede ser negativo");
        this.ammo = ammo;
    }

    public void setDamage(int damage) {
        if (damage < 0) throw new IllegalArgumentException("damage no puede ser negativo");
        this.damage = damage;
    }

    public void setShotsPerTurn(int shotsPerTurn) {
        if (shotsPerTurn <= 0) throw new IllegalArgumentException("shotsPerTurn debe ser > 0");
        this.shotsPerTurn = shotsPerTurn;
    }

    public void setAccuracy(double accuracy) {
        if (accuracy < 0.0 || accuracy > 1.0)
            throw new IllegalArgumentException("accuracy debe estar entre 0.0 y 1.0");
        this.accuracy = accuracy;
    }

    public void setRange(int range) {
        if (range <= 0) throw new IllegalArgumentException("range debe ser > 0");
        this.range = range;
    }

    // =========================
    // Comportamiento de dominio
    // =========================

    public boolean hasAmmo() {
        return ammo > 0;
    }

    public void consumeAmmo(int amount) {
        if (amount <= 0) return;
        if (ammo - amount < 0) throw new IllegalStateException("Munición insuficiente");
        ammo -= amount;
    }
}
