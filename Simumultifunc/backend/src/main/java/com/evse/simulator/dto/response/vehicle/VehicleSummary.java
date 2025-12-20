package com.evse.simulator.dto.response.vehicle;

/**
 * Résumé d'un véhicule.
 */
public record VehicleSummary(
        String id,
        String displayName,
        String brand,
        int batteryCapacityKwh,
        int maxDcPowerKw,
        int maxAcPowerKw
) {}
