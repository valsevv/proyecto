package com.example.proyect.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.proyect.config.GameBalanceProperties;
import com.example.proyect.game.config.UnitBalanceRegistry;

@RestController
@RequestMapping("/api/game")
public class GameConfigController {

    private final GameBalanceProperties gameBalanceProperties;

    public GameConfigController(GameBalanceProperties gameBalanceProperties) {
        this.gameBalanceProperties = gameBalanceProperties;
    }

    @GetMapping("/config")
    public Map<String, Object> getGameConfig() {
        return Map.ofEntries(
            Map.entry("actionsPerTurn", gameBalanceProperties.getActionsPerTurn()),
                Map.entry("aerialVisionRange", UnitBalanceRegistry.getAerialDroneVisionRange()),
                Map.entry("navalVisionRange", UnitBalanceRegistry.getNavalDroneVisionRange()),
                Map.entry("aerialDroneMovementRange", UnitBalanceRegistry.getAerialDroneMovementRange()),
                Map.entry("navalDroneMovementRange", UnitBalanceRegistry.getNavalDroneMovementRange()),
                Map.entry("aerialDroneMaxFuel", UnitBalanceRegistry.getAerialDroneMaxFuel()),
                Map.entry("navalDroneMaxFuel", UnitBalanceRegistry.getNavalDroneMaxFuel()),
                Map.entry("aerialDroneMaxHp", UnitBalanceRegistry.getAerialDroneMaxHp()),
                Map.entry("navalDroneMaxHp", UnitBalanceRegistry.getNavalDroneMaxHp()),
                Map.entry("droneAttackDamage", UnitBalanceRegistry.getDroneAttackDamage()),
                Map.entry("aerialDroneAmmo", UnitBalanceRegistry.getAerialDroneAmmo()),
                Map.entry("navalDroneMissiles", UnitBalanceRegistry.getNavalDroneMissiles()),
                Map.entry("aerialCarrierMaxHp", UnitBalanceRegistry.getAerialCarrierMaxHp()),
                Map.entry("navalCarrierMaxHp", UnitBalanceRegistry.getNavalCarrierMaxHp()),
                Map.entry("aerialCarrierMovementRange", UnitBalanceRegistry.getAerialCarrierMovementRange()),
                Map.entry("navalCarrierMovementRange", UnitBalanceRegistry.getNavalCarrierMovementRange()),
                Map.entry("missileMaxDistance", gameBalanceProperties.getMissileMaxDistance()),
                Map.entry("aerialAttackFuelCost", gameBalanceProperties.getAerialAttackFuelCost()),
                Map.entry("carrierHitsToDestroy", gameBalanceProperties.getCarrierHitsToDestroy()));
    }
}
