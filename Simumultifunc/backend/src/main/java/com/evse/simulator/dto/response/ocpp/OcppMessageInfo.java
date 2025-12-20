package com.evse.simulator.dto.response.ocpp;

import java.time.Instant;
import java.util.Map;

/**
 * Informations sur un message OCPP.
 */
public record OcppMessageInfo(
        String messageId,
        String sessionId,
        String action,
        String direction,
        Instant timestamp,
        Map<String, Object> payload,
        String status,
        long latencyMs
) {}
