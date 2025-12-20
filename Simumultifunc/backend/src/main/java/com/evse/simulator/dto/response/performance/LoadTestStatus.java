package com.evse.simulator.dto.response.performance;

import java.time.Instant;
import java.util.Map;

/**
 * Statut d'un test de charge.
 */
public record LoadTestStatus(
        String testId,
        String status,
        Instant startTime,
        Instant endTime,
        int targetConnections,
        int activeConnections,
        int successfulConnections,
        int failedConnections,
        long messagesSent,
        long messagesReceived,
        double avgLatencyMs,
        double p50LatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        double throughputPerSecond,
        Map<String, Long> errorCounts
) {}
