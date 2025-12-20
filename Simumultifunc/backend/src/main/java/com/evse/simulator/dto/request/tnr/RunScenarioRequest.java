package com.evse.simulator.dto.request.tnr;

import java.util.Map;

/**
 * Request pour exécuter un scénario TNR.
 */
public record RunScenarioRequest(
        String scenarioId,
        String mode,
        Map<String, Object> parameters,
        boolean stopOnError,
        boolean captureResponses
) {
    public RunScenarioRequest {
        if (mode == null) mode = "replay";
        if (stopOnError == false) stopOnError = true;
    }
}
