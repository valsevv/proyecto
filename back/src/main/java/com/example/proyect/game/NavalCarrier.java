package com.example.proyect.game;

public class NavalCarrier extends Carrier {

    private static final int DEFAULT_MAX_HP = 500;
    private static final int DEFAULT_SPEED = 1;

    public NavalCarrier() {
        super();

        // Ajustamos características específicas
        setCarrierSpeed(DEFAULT_SPEED);
        initializeHealth(DEFAULT_MAX_HP);
    }

    /**
     * Inicializa la vida máxima y actual del carrier.
     * Este método asume que Unit permite modificar maxHp internamente.
     */
    private void initializeHealth(int maxHp) {
        // Si Unit no tiene setter protegido para maxHp,
        // conviene agregarlo como protected en Unit.
        setMaxHp(maxHp);
    }
}
