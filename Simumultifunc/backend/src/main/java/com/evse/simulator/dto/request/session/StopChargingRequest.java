package com.evse.simulator.dto.request.session;

/**
 * Request pour arrêter une session de charge.
 */
public record StopChargingRequest(
        String sessionId,
        String reason
) {
    public StopChargingRequest {
        if (reason == null) reason = "Local";
    }
}
