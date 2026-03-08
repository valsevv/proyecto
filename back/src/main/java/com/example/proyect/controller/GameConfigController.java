package com.example.proyect.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public class GameConfigController {

    @Value("${game.actions-per-turn:10}")
    private int actionsPerTurn;

    @Value("${game.units.aereo.vision-range:${game.vision-range-aereo:6}}")
    private int aerialVisionRange;

    @Value("${game.units.naval.vision-range:${game.vision-range-naval:4}}")
    private int navalVisionRange;

    @Value("${game.units.aereo.movement-range:2}")
    private int aerialDroneMovementRange;

    @Value("${game.units.naval.movement-range:2}")
    private int navalDroneMovementRange;

    @Value("${game.units.aereo.max-fuel:10}")
    private int aerialDroneMaxFuel;

    @Value("${game.units.naval.max-fuel:10}")
    private int navalDroneMaxFuel;

    @Value("${game.units.aereo.ammo:1}")
    private int aerialDroneAmmo;

    @Value("${game.units.naval.missiles:2}")
    private int navalDroneMissiles;

    @Value("${game.units.aereo.carrier-movement-range:3}")
    private int aerialCarrierMovementRange;

    @Value("${game.units.naval.carrier-movement-range:1}")
    private int navalCarrierMovementRange;

    @Value("${game.missile.max-distance:15}")
    private int missileMaxDistance;

    @GetMapping("/config")
    public Map<String, Object> getGameConfig() {
        return Map.ofEntries(
                Map.entry("actionsPerTurn", actionsPerTurn),
                Map.entry("aerialVisionRange", aerialVisionRange),
                Map.entry("navalVisionRange", navalVisionRange),
                Map.entry("aerialDroneMovementRange", aerialDroneMovementRange),
                Map.entry("navalDroneMovementRange", navalDroneMovementRange),
                Map.entry("aerialDroneMaxFuel", aerialDroneMaxFuel),
                Map.entry("navalDroneMaxFuel", navalDroneMaxFuel),
                Map.entry("aerialDroneAmmo", aerialDroneAmmo),
                Map.entry("navalDroneMissiles", navalDroneMissiles),
                Map.entry("aerialCarrierMovementRange", aerialCarrierMovementRange),
                Map.entry("navalCarrierMovementRange", navalCarrierMovementRange),
                Map.entry("missileMaxDistance", missileMaxDistance));
    }
}
