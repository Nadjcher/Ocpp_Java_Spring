package com.evse.simulator.dto.request.session;

/**
 * Request pour démarrer une session de charge.
 */
public record StartChargingRequest(
        String sessionId,
        String idTag,
        Integer connectorId
) {
    public StartChargingRequest {
        if (connectorId == null) connectorId = 1;
    }
}
