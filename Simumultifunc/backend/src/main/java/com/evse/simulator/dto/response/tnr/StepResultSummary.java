package com.evse.simulator.dto.response.tnr;

/**
 * Résumé du résultat d'un step TNR.
 */
public record StepResultSummary(
        int stepIndex,
        String action,
        String status,
        long latencyMs,
        String errorMessage
) {}
