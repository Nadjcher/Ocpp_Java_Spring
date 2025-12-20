package com.evse.simulator.dto.response.vehicle;

import java.util.List;

/**
 * Détails complets d'un véhicule.
 */
public record VehicleDetail(
        String id,
        String displayName,
        String brand,
        int batteryCapacityKwh,
        int maxDcPowerKw,
        int maxAcPowerKw,
        int maxVoltage,
        int maxCurrentA,
        List<ChargeCurvePoint> dcChargingCurve
) {}
