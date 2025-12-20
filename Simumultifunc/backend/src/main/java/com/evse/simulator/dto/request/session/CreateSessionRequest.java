package com.evse.simulator.dto.request.session;

import jakarta.validation.constraints.NotBlank;

/**
 * Request pour créer une session de charge.
 */
public record CreateSessionRequest(
        @NotBlank(message = "CSMS URL is required")
        String csmsUrl,

        @NotBlank(message = "Charge Point ID is required")
        String cpId,

        Integer connectorId,
        String vehicleId,
        String chargerType,
        String idTag,
        double initialSoc,
        double targetSoc,
        String bearerToken
) {
    public CreateSessionRequest {
        if (connectorId == null) connectorId = 1;
        if (initialSoc <= 0) initialSoc = 20.0;
        if (targetSoc <= 0) targetSoc = 80.0;
    }
}
