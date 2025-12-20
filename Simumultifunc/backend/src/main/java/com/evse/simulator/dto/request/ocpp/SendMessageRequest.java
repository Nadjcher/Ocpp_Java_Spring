package com.evse.simulator.dto.request.ocpp;

import java.util.Map;

/**
 * Request générique pour envoyer un message OCPP.
 */
public record SendMessageRequest(
        String sessionId,
        String action,
        Map<String, Object> payload
) {}
