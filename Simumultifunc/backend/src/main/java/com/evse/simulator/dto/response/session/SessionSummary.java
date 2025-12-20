package com.evse.simulator.dto.response.session;

/**
 * Résumé d'une session de charge.
 */
public record SessionSummary(
        String id,
        String cpId,
        int connectorId,
        String status,
        int soc,
        double currentPowerKw,
        double energyDeliveredKwh,
        boolean connected
) {}
