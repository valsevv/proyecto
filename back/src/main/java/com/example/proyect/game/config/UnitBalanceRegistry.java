package com.example.proyect.game.config;

public final class UnitBalanceRegistry {

    public static final int DEFAULT_AERIAL_DRONE_MAX_HP = 100;
    public static final int DEFAULT_AERIAL_DRONE_MOVEMENT_RANGE = 2;
    public static final int DEFAULT_AERIAL_DRONE_VISION_RANGE = 4;
    public static final int DEFAULT_AERIAL_DRONE_MAX_FUEL = 10;
    public static final int DEFAULT_AERIAL_DRONE_AMMO = 1;

    public static final int DEFAULT_NAVAL_DRONE_MAX_HP = 100;
    public static final int DEFAULT_NAVAL_DRONE_MOVEMENT_RANGE = 2;
    public static final int DEFAULT_NAVAL_DRONE_VISION_RANGE = 3;
    public static final int DEFAULT_NAVAL_DRONE_MAX_FUEL = 10;
    public static final int DEFAULT_NAVAL_DRONE_WEAPON_AMMO = 10;
    public static final int DEFAULT_NAVAL_DRONE_MISSILES = 2;

    public static final int DEFAULT_AERIAL_CARRIER_MAX_HP = 6;
    public static final int DEFAULT_AERIAL_CARRIER_MOVEMENT_RANGE = 3;
    public static final int DEFAULT_NAVAL_CARRIER_MAX_HP = 3;
    public static final int DEFAULT_NAVAL_CARRIER_MOVEMENT_RANGE = 1;

    private static volatile int aerialDroneMaxHp = DEFAULT_AERIAL_DRONE_MAX_HP;
    private static volatile int aerialDroneMovementRange = DEFAULT_AERIAL_DRONE_MOVEMENT_RANGE;
    private static volatile int aerialDroneVisionRange = DEFAULT_AERIAL_DRONE_VISION_RANGE;
    private static volatile int aerialDroneMaxFuel = DEFAULT_AERIAL_DRONE_MAX_FUEL;
    private static volatile int aerialDroneAmmo = DEFAULT_AERIAL_DRONE_AMMO;

    private static volatile int navalDroneMaxHp = DEFAULT_NAVAL_DRONE_MAX_HP;
    private static volatile int navalDroneMovementRange = DEFAULT_NAVAL_DRONE_MOVEMENT_RANGE;
    private static volatile int navalDroneVisionRange = DEFAULT_NAVAL_DRONE_VISION_RANGE;
    private static volatile int navalDroneMaxFuel = DEFAULT_NAVAL_DRONE_MAX_FUEL;
    private static volatile int navalDroneWeaponAmmo = DEFAULT_NAVAL_DRONE_WEAPON_AMMO;
    private static volatile int navalDroneMissiles = DEFAULT_NAVAL_DRONE_MISSILES;

    private static volatile int aerialCarrierMaxHp = DEFAULT_AERIAL_CARRIER_MAX_HP;
    private static volatile int aerialCarrierMovementRange = DEFAULT_AERIAL_CARRIER_MOVEMENT_RANGE;
    private static volatile int navalCarrierMaxHp = DEFAULT_NAVAL_CARRIER_MAX_HP;
    private static volatile int navalCarrierMovementRange = DEFAULT_NAVAL_CARRIER_MOVEMENT_RANGE;

    private UnitBalanceRegistry() {
    }

    public static void configure(
            int configuredAerialDroneMaxHp,
            int configuredAerialDroneMovementRange,
            int configuredAerialDroneVisionRange,
            int configuredAerialDroneMaxFuel,
            int configuredAerialDroneAmmo,
            int configuredNavalDroneMaxHp,
            int configuredNavalDroneMovementRange,
            int configuredNavalDroneVisionRange,
            int configuredNavalDroneMaxFuel,
            int configuredNavalDroneWeaponAmmo,
            int configuredNavalDroneMissiles,
            int configuredAerialCarrierMaxHp,
            int configuredAerialCarrierMovementRange,
            int configuredNavalCarrierMaxHp,
            int configuredNavalCarrierMovementRange
    ) {
        aerialDroneMaxHp = requirePositive(configuredAerialDroneMaxHp, "aerialDroneMaxHp");
        aerialDroneMovementRange = requireNonNegative(configuredAerialDroneMovementRange, "aerialDroneMovementRange");
        aerialDroneVisionRange = requireNonNegative(configuredAerialDroneVisionRange, "aerialDroneVisionRange");
        aerialDroneMaxFuel = requireNonNegative(configuredAerialDroneMaxFuel, "aerialDroneMaxFuel");
        aerialDroneAmmo = requireNonNegative(configuredAerialDroneAmmo, "aerialDroneAmmo");

        navalDroneMaxHp = requirePositive(configuredNavalDroneMaxHp, "navalDroneMaxHp");
        navalDroneMovementRange = requireNonNegative(configuredNavalDroneMovementRange, "navalDroneMovementRange");
        navalDroneVisionRange = requireNonNegative(configuredNavalDroneVisionRange, "navalDroneVisionRange");
        navalDroneMaxFuel = requireNonNegative(configuredNavalDroneMaxFuel, "navalDroneMaxFuel");
        navalDroneWeaponAmmo = requireNonNegative(configuredNavalDroneWeaponAmmo, "navalDroneWeaponAmmo");
        navalDroneMissiles = requireNonNegative(configuredNavalDroneMissiles, "navalDroneMissiles");

        aerialCarrierMaxHp = requirePositive(configuredAerialCarrierMaxHp, "aerialCarrierMaxHp");
        aerialCarrierMovementRange = requireNonNegative(configuredAerialCarrierMovementRange, "aerialCarrierMovementRange");
        navalCarrierMaxHp = requirePositive(configuredNavalCarrierMaxHp, "navalCarrierMaxHp");
        navalCarrierMovementRange = requireNonNegative(configuredNavalCarrierMovementRange, "navalCarrierMovementRange");
    }

    private static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
        return value;
    }

    private static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
        return value;
    }

    public static int getAerialDroneMaxHp() { return aerialDroneMaxHp; }
    public static int getAerialDroneMovementRange() { return aerialDroneMovementRange; }
    public static int getAerialDroneVisionRange() { return aerialDroneVisionRange; }
    public static int getAerialDroneMaxFuel() { return aerialDroneMaxFuel; }
    public static int getAerialDroneAmmo() { return aerialDroneAmmo; }

    public static int getNavalDroneMaxHp() { return navalDroneMaxHp; }
    public static int getNavalDroneMovementRange() { return navalDroneMovementRange; }
    public static int getNavalDroneVisionRange() { return navalDroneVisionRange; }
    public static int getNavalDroneMaxFuel() { return navalDroneMaxFuel; }
    public static int getNavalDroneWeaponAmmo() { return navalDroneWeaponAmmo; }
    public static int getNavalDroneMissiles() { return navalDroneMissiles; }

    public static int getAerialCarrierMaxHp() { return aerialCarrierMaxHp; }
    public static int getAerialCarrierMovementRange() { return aerialCarrierMovementRange; }
    public static int getNavalCarrierMaxHp() { return navalCarrierMaxHp; }
    public static int getNavalCarrierMovementRange() { return navalCarrierMovementRange; }
}
