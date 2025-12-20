package com.evse.simulator.dto.response.ocpi;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Résultat d'un test OCPI.
 */
public record OcpiTestResult(
        String testId,
        String partnerId,
        String testType,
        String status,
        Instant startTime,
        Instant endTime,
        long durationMs,
        int totalSteps,
        int passedSteps,
        int failedSteps,
        List<StepResult> steps,
        Map<String, Object> summary
) {
    public record StepResult(
            int stepIndex,
            String module,
            String operation,
            String status,
            int httpStatus,
            long latencyMs,
            String requestBody,
            String responseBody,
            String errorMessage
    ) {}
}
