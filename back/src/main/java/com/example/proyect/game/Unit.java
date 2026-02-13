package com.example.proyect.game;

/**
 * Clase abstracta base para cualquier entidad del tablero
 * que tenga vida, posición y pertenezca a un jugador.
 */
public abstract class Unit {

    private String id;
    private long ownerPlayerId;
    private int maxHp;
    private int actualHp;
    private HexCoord position;
    private UnitStatus status;



    private int movementRange;


    public Unit() {
        this.maxHp = 100;
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

        maxHp -= amount;

        if (maxHp <= 0) {
            maxHp = 0;
            status = UnitStatus.DESTROYED;
        }
    }

    // =========================
    // Tipos auxiliares
    // =========================

    public enum UnitStatus {
        ALIVE,
        DESTROYED
    }

    public static final class HexCoord {

        private final int q;
        private final int r;

        public HexCoord(int q, int r) {
            this.q = q;
            this.r = r;
        }

        public int getQ() {
            return q;
        }

        public int getR() {
            return r;
        }

        @Override
        public String toString() {
            return "(" + q + "," + r + ")";
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof HexCoord)) return false;
            HexCoord other = (HexCoord) obj;
            return q == other.q && r == other.r;
        }

        @Override
        public int hashCode() {
            return 31 * q + r;
        }
    }

    public int getActualHp() {
        return actualHp;
    }

    public void setActualHp(int actualHp) {
        this.actualHp = actualHp;
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
        this.actualHp = maxHp;
    }
}
