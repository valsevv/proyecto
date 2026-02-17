package com.example.proyect.game.units;

/**
 * Clase abstracta base para cualquier entidad del tablero
 * que tenga vida, posición y pertenezca a un jugador.
 */
public abstract class Unit {

    private String id;
    private long ownerPlayerId;
    private int maxHp;
    private int currentHp;
    private HexCoord position;
    private UnitStatus status;

    private int movementRange;

    public Unit() {
        this.maxHp = 100;
        this.currentHp = 100;
        this.status = UnitStatus.ALIVE;
    } //SE DEBERIA PONER TAMBIEN EL OWNERPLAYEID NO?


    // getters y setters


    public String getId() {
        return id;
    }

    public long getOwnerPlayerId() {
        return ownerPlayerId;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public HexCoord getPosition() {
        return position;
    }

    public UnitStatus getStatus() {
        return status;
    }

    public boolean isAlive() {
        return status == UnitStatus.ALIVE;
    }

    public boolean isDestroyed() {
        return status == UnitStatus.DESTROYED;
    }

    // =========================

    public void setId(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id requerido");
        this.id = id;
    }

    public void setOwnerPlayerId(long ownerPlayerId) {
        this.ownerPlayerId = ownerPlayerId;
    }

    public void setPosition(HexCoord position) {
        if (position == null)
            throw new IllegalArgumentException("position requerida");
        this.position = position;
    }

    // =========================
    // Comportamiento de dominio
    // =========================

    public void receiveDamage(int amount) {
        if (amount <= 0 || isDestroyed()) return;

        currentHp -= amount;

        if (currentHp <= 0) {
            currentHp = 0;
            status = UnitStatus.DESTROYED;
        }
    }

    public void heal(int amount) {
        if (amount <= 0 || isDestroyed()) return;

        currentHp = Math.min(currentHp + amount, maxHp);
    }

    // =========================
    // Tipos auxiliares
    // =========================

    public enum UnitStatus {
        ALIVE,
        DESTROYED
    }

    public static final class HexCoord {

        private final double x;
        private final double y;

        public HexCoord(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof HexCoord)) return false;
            HexCoord other = (HexCoord) obj;
            return Double.compare(x, other.x) == 0 && Double.compare(y, other.y) == 0;
        }

        @Override
        public int hashCode() {
            long xBits = Double.doubleToLongBits(x);
            long yBits = Double.doubleToLongBits(y);
            return (int) (xBits ^ (xBits >>> 32) ^ yBits ^ (yBits >>> 32));
        }
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public void setCurrentHp(int currentHp) {
        this.currentHp = currentHp;
    }

    public int getMovementRange() {
        return movementRange;
    }

    public void setMovementRange(int movementRange) {
        this.movementRange = movementRange;
    }

    protected void setMaxHp(int maxHp) {
        if (maxHp <= 0)
            throw new IllegalArgumentException("maxHp debe ser mayor a 0");

        this.maxHp = maxHp;
        this.currentHp = maxHp;
    }
}
