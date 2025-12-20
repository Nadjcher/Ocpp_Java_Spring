package com.evse.simulator.dto.response.ocpi;

import java.time.Instant;
import java.util.List;

/**
 * Résumé d'un partenaire OCPI.
 */
public record PartnerSummary(
        String id,
        String name,
        String countryCode,
        String partyId,
        String role,
        String version,
        boolean active,
        String activeEnvironment,
        List<String> environments,
        Instant lastSync
) {}
