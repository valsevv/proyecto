package com.example.proyect.game;

public abstract class Carrier extends Unit {

    public Carrier() {
        super();
        setMovementRange(0);
    }

    /**
     * Permite que las subclases definan su velocidad específica.
     * En tu modelo, la velocidad se traduce en movementRange.
     */
    protected void setCarrierSpeed(int speed) {
        if (speed < 0) {
            throw new IllegalArgumentException("La velocidad no puede ser negativa");
        }
        setMovementRange(speed);
    }
}
