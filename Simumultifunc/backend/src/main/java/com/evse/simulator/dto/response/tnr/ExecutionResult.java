package com.evse.simulator.dto.response.tnr;

import java.util.List;

/**
 * Résultat d'exécution d'un scénario TNR.
 */
public record ExecutionResult(
        String executionId,
        String scenarioId,
        String scenarioName,
        String status,
        int totalSteps,
        int passedSteps,
        int failedSteps,
        long avgLatencyMs,
        String timestamp,
        List<StepResultSummary> steps
) {}
