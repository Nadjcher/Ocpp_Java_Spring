package com.evse.simulator.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Response pour le statut de santé de l'application.
 */
public record HealthStatusResponse(
        String status,
        Instant timestamp,
        Map<String, ComponentHealth> components
) {
    public record ComponentHealth(
            String status,
            String message,
            Map<String, Object> details
    ) {}
}
