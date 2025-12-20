package com.evse.simulator.performance.model;

import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Métriques de performance d'un test.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfMetrics {
    private Instant timestamp;
    private int activeConnections;
    private int successfulConnections;
    private int failedConnections;
    private long messagesSent;
    private long messagesReceived;
    private long totalMessagesSent;
    private long totalMessagesReceived;
    private long totalErrors;
    private double avgLatencyMs;
    private double minLatencyMs;
    private double maxLatencyMs;
    private double p50LatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;
    private double throughputPerSecond;
    private double throughputMsgPerSec;
    private double connectionsPerSec;
    // Connection latency metrics
    private double connectionLatencyAvgMs;
    private double connectionLatencyP50Ms;
    private double connectionLatencyP95Ms;
    private double connectionLatencyP99Ms;
    private double connectionLatencyMaxMs;
    // Message latency metrics
    private double messageLatencyAvgMs;
    private double messageLatencyP50Ms;
    private double messageLatencyP95Ms;
    private double messageLatencyP99Ms;
    // Resource metrics
    private long memoryUsedMb;
    private long memoryMaxMb;
    private int threadCount;
    private int targetConnections;
    // Progress
    private double progressPercent;
    private Map<String, Long> messageCounts;
    private Map<String, Long> errorCounts;

    /**
     * Gets throughput in messages per second.
     */
    public double getThroughputMsgPerSec() {
        return throughputMsgPerSec > 0 ? throughputMsgPerSec : throughputPerSecond;
    }

    /**
     * Gets P95 connection latency.
     */
    public double getConnectionLatencyP95Ms() {
        return connectionLatencyP95Ms > 0 ? connectionLatencyP95Ms : p95LatencyMs;
    }

    /**
     * Gets progress percentage.
     */
    public double getProgressPercent() {
        return progressPercent;
    }
}
