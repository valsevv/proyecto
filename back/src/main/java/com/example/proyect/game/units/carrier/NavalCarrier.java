package com.example.proyect.game.units.carrier;

import com.example.proyect.game.config.UnitBalanceRegistry;

public class NavalCarrier extends Carrier {

    public NavalCarrier() {
        super();

        setCarrierSpeed(UnitBalanceRegistry.getNavalCarrierMovementRange());
        initializeHealth(UnitBalanceRegistry.getNavalCarrierMaxHp());
    }

    private void initializeHealth(int maxHp) {
        setMaxHp(maxHp);
    }
}
