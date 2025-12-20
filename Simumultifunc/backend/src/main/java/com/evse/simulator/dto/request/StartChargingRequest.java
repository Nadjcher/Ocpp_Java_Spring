package com.evse.simulator.dto.request;

/**
 * Request pour démarrer une charge (legacy).
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
