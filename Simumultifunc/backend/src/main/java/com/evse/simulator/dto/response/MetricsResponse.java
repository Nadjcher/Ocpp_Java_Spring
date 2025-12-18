package com.evse.simulator.dto.response;

import com.evse.simulator.model.PerformanceMetrics;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Réponse contenant les métriques de performance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetricsResponse {

    // Sessions
    private int totalSessions;
    private int activeSessions;
    private int chargingSessions;
    private int errorSessions;

    // Messages
    private long messagesSent;
    private long messagesReceived;
    private double throughput;
    private double errorRate;

    // Latences
    private double averageLatencyMs;
    private double minLatencyMs;
    private double maxLatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;

    // Charge
    private double totalPowerKw;
    private double totalEnergyKwh;
    private double averagePowerKw;

    // Système
    private double cpuUsage;
    private double memoryUsedMb;
    private double memoryTotalMb;
    private int activeThreads;
    private int activeWebSockets;

    // Test de charge
    private boolean loadTestRunning;
    private int targetSessions;
    private double loadTestProgress;
    private long loadTestDurationSeconds;

    // Horodatage
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Convertit les métriques en DTO.
     */
    public static MetricsResponse fromMetrics(PerformanceMetrics metrics) {
        return MetricsResponse.builder()
                .totalSessions(metrics.getTotalSessions())
                .activeSessions(metrics.getActiveSessions())
                .chargingSessions(metrics.getChargingSessions())
                .errorSessions(metrics.getErrorSessions())
                .messagesSent(metrics.getMessagesSent())
                .messagesReceived(metrics.getMessagesReceived())
                .throughput(metrics.getThroughput())
                .errorRate(metrics.getErrorRate())
                .averageLatencyMs(metrics.getAverageLatencyMs())
                .minLatencyMs(metrics.getMinLatencyMs())
                .maxLatencyMs(metrics.getMaxLatencyMs())
                .p95LatencyMs(metrics.getP95LatencyMs())
                .p99LatencyMs(metrics.getP99LatencyMs())
                .totalPowerKw(metrics.getTotalPowerKw())
                .totalEnergyKwh(metrics.getTotalEnergyKwh())
                .averagePowerKw(metrics.getAveragePowerKw())
                .cpuUsage(metrics.getCpuUsage())
                .memoryUsedMb(metrics.getMemoryUsedMb())
                .memoryTotalMb(metrics.getMemoryTotalMb())
                .activeThreads(metrics.getActiveThreads())
                .activeWebSockets(metrics.getActiveWebSockets())
                .loadTestRunning(metrics.isLoadTestRunning())
                .targetSessions(metrics.getTargetSessions())
                .loadTestProgress(metrics.getLoadTestProgress())
                .loadTestDurationSeconds(metrics.getLoadTestDurationSeconds())
                .timestamp(metrics.getTimestamp())
                .build();
    }
}