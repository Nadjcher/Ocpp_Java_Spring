package com.evse.simulator.dto.request;

/**
 * Request pour arrêter une charge (legacy).
 */
public record StopChargingRequest(
        String sessionId,
        String reason
) {
    public StopChargingRequest {
        if (reason == null) reason = "Local";
    }
}
