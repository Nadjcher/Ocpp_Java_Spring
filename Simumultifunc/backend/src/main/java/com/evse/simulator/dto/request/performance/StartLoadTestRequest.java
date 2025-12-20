package com.evse.simulator.dto.request.performance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request pour démarrer un test de charge.
 */
public record StartLoadTestRequest(
        @NotBlank(message = "CSMS URL is required")
        String csmsUrl,

        @Min(value = 1, message = "At least 1 connection required")
        int connections,

        @Min(value = 1, message = "Duration must be positive")
        int durationSeconds,

        int rampUpSeconds,
        int messageIntervalMs,
        String chargerType,
        String idTagPrefix,
        String cpIdPrefix
) {
    public StartLoadTestRequest {
        if (rampUpSeconds <= 0) rampUpSeconds = 10;
        if (messageIntervalMs <= 0) messageIntervalMs = 1000;
        if (chargerType == null) chargerType = "AC_TRI";
        if (idTagPrefix == null) idTagPrefix = "TAG";
        if (cpIdPrefix == null) cpIdPrefix = "CP";
    }
}
