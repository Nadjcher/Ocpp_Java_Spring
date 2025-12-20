package com.evse.simulator.dto.request.ocpi;

import com.evse.simulator.ocpi.OCPIRole;
import com.evse.simulator.ocpi.OCPIVersion;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request pour créer un partenaire OCPI.
 */
public record CreatePartnerRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Country code is required")
        String countryCode,

        @NotBlank(message = "Party ID is required")
        String partyId,

        OCPIRole role,
        OCPIVersion version,

        String activeEnvironment,
        Map<String, EnvironmentConfigRequest> environments
) {
    public record EnvironmentConfigRequest(
            String baseUrl,
            String versionsUrl,
            String tokenA,
            String tokenB,
            String tokenC,
            CognitoConfigRequest cognito
    ) {}

    public record CognitoConfigRequest(
            String tokenUrl,
            String clientId,
            String clientSecret
    ) {}
}
