package com.example.proyect.config;

import org.springframework.stereotype.Component;

import com.example.proyect.game.config.UnitBalanceRegistry;

import jakarta.annotation.PostConstruct;

@Component
public class UnitBalanceInitializer {

    private final GameBalanceProperties gameBalanceProperties;

    public UnitBalanceInitializer(GameBalanceProperties gameBalanceProperties) {
        this.gameBalanceProperties = gameBalanceProperties;
    }

    @PostConstruct
    public void initialize() {
        UnitBalanceRegistry.configure(
            gameBalanceProperties.getUnits().getAereo().getMaxHp(),
            gameBalanceProperties.getUnits().getAereo().getMovementRange(),
            gameBalanceProperties.getUnits().getAereo().getVisionRange(),
            gameBalanceProperties.getUnits().getAereo().getMaxFuel(),
            gameBalanceProperties.getUnits().getAereo().getAmmo(),
            gameBalanceProperties.getUnits().getAereo().getAccuracy(),
            gameBalanceProperties.getUnits().getNaval().getMaxHp(),
            gameBalanceProperties.getUnits().getDrone().getAttackDamage(),
            gameBalanceProperties.getUnits().getNaval().getMovementRange(),
            gameBalanceProperties.getUnits().getNaval().getVisionRange(),
            gameBalanceProperties.getUnits().getNaval().getMaxFuel(),
            gameBalanceProperties.getUnits().getNaval().getWeaponAmmo(),
            gameBalanceProperties.getUnits().getNaval().getMissiles(),
            gameBalanceProperties.getUnits().getNaval().getAccuracy(),
            gameBalanceProperties.getUnits().getAereo().getCarrierHp(),
            gameBalanceProperties.getUnits().getAereo().getCarrierMovementRange(),
            gameBalanceProperties.getUnits().getNaval().getCarrierHp(),
            gameBalanceProperties.getUnits().getNaval().getCarrierMovementRange()
        );
    }
}
