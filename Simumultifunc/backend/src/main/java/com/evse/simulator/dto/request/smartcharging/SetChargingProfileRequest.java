package com.evse.simulator.dto.request.smartcharging;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request pour appliquer un profil de charge.
 */
public record SetChargingProfileRequest(
        @NotNull(message = "Purpose is required")
        String purpose,

        @Min(value = 0, message = "Limit must be positive")
        double limitKw,

        @Min(value = 0, message = "Duration must be positive")
        int durationSec,

        int phases
) {
    public SetChargingProfileRequest {
        if (phases <= 0) phases = 3;
        if (durationSec <= 0) durationSec = 3600;
    }
}
