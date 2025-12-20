package com.evse.simulator.performance.model;

import lombok.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Résultat complet d'un test de performance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfResult {
    private String testId;
    private PerfConfig config;
    private PerfStatus status;
    private Instant startTime;
    private Instant endTime;
    private long durationMs;
    private Duration duration;
    private PerfMetrics metrics;
    private PerfMetrics finalMetrics;
    private List<ConnectionResult> connectionResults;
    private String summary;
    private String error;
    private int completedTransactions;
    private int maxConnectionsReached;
    private int successfulConnections;
    private int failedConnections;
    private long totalMessagesSent;
    private long totalMessagesReceived;
    private long totalErrors;

    /**
     * Creates an error result.
     */
    public static PerfResult error(String errorMessage) {
        return PerfResult.builder()
                .status(PerfStatus.FAILED)
                .error(errorMessage)
                .summary("Test failed: " + errorMessage)
                .build();
    }
}
