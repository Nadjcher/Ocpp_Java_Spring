package com.evse.simulator.dto.request.ocpi;

import java.util.List;
import java.util.Map;

/**
 * Request pour exécuter un test OCPI.
 */
public record RunTestRequest(
        String partnerId,
        String testType,
        String scenarioId,
        Map<String, Object> parameters,
        List<String> modules,
        boolean stopOnError
) {
    public RunTestRequest {
        if (stopOnError == false) stopOnError = true;
    }
}
