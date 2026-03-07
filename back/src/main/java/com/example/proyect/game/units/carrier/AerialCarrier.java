package com.example.proyect.game.units.carrier;

import com.example.proyect.game.config.UnitBalanceRegistry;

public class AerialCarrier extends Carrier {

    public AerialCarrier() {
        super();

        setCarrierSpeed(UnitBalanceRegistry.getAerialCarrierMovementRange());
        initializeHealth(UnitBalanceRegistry.getAerialCarrierMaxHp());
    }

    private void initializeHealth(int maxHp) {
        setMaxHp(maxHp);
    }
}
