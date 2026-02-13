package com.example.proyect.game;

public class AerialCarrier extends Carrier {

    private static final int DEFAULT_MAX_HP = 400;
    private static final int DEFAULT_SPEED = 3;

    public AerialCarrier() {
        super();

        setCarrierSpeed(DEFAULT_SPEED);
        initializeHealth(DEFAULT_MAX_HP);
    }

    private void initializeHealth(int maxHp) {
        setMaxHp(maxHp);
    }
}
