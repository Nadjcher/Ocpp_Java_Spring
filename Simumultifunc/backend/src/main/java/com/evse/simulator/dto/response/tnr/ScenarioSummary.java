package com.evse.simulator.dto.response.tnr;

/**
 * Résumé d'un scénario TNR.
 */
public record ScenarioSummary(
        String id,
        String name,
        String description,
        String category,
        int stepCount,
        String lastStatus,
        String lastRunAt
) {}
