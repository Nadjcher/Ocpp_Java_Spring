package com.evse.simulator.dto.response.session;

import com.evse.simulator.dto.response.smartcharging.ChargingProfileInfo;

/**
 * Détails complets d'une session de charge.
 */
public record SessionDetail(
        String id,
        String cpId,
        int connectorId,
        String status,
        int soc,
        int targetSoc,
        double energyDeliveredKwh,
        double currentPowerKw,
        double maxPowerKw,
        long durationSeconds,
        String vehicleProfile,
        String chargerType,
        String idTag,
        String transactionId,
        boolean connected,
        boolean authorized,
        boolean charging,
        ChargingProfileInfo activeProfile
) {}
