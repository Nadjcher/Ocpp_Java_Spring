package com.evse.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Métriques de performance du simulateur.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceMetrics {

    /**
     * Horodatage des métriques.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // =========================================================================
    // Métriques de sessions
    // =========================================================================

    /**
     * Nombre total de sessions.
     */
    @Builder.Default
    private int totalSessions = 0;

    /**
     * Nombre de sessions actives (connectées).
     */
    @Builder.Default
    private int activeSessions = 0;

    /**
     * Nombre de sessions en charge.
     */
    @Builder.Default
    private int chargingSessions = 0;

    /**
     * Nombre de sessions en erreur.
     */
    @Builder.Default
    private int errorSessions = 0;

    // =========================================================================
    // Métriques de messages
    // =========================================================================

    /**
     * Nombre total de messages envoyés.
     */
    @Builder.Default
    private long messagesSent = 0;

    /**
     * Nombre total de messages reçus.
     */
    @Builder.Default
    private long messagesReceived = 0;

    /**
     * Messages par seconde (sortants).
     */
    @Builder.Default
    private double messagesPerSecond = 0.0;

    /**
     * Latence moyenne en ms.
     */
    @Builder.Default
    private double averageLatencyMs = 0.0;

    /**
     * Latence max en ms.
     */
    @Builder.Default
    private double maxLatencyMs = 0.0;

    /**
     * Latence min en ms.
     */
    @Builder.Default
    private double minLatencyMs = 0.0;

    /**
     * Latence P95 en ms.
     */
    @Builder.Default
    private double p95LatencyMs = 0.0;

    /**
     * Latence P99 en ms.
     */
    @Builder.Default
    private double p99LatencyMs = 0.0;

    // =========================================================================
    // Métriques de charge
    // =========================================================================

    /**
     * Puissance totale de charge en kW.
     */
    @Builder.Default
    private double totalPowerKw = 0.0;

    /**
     * Énergie totale délivrée en kWh.
     */
    @Builder.Default
    private double totalEnergyKwh = 0.0;

    /**
     * Puissance moyenne par session en kW.
     */
    @Builder.Default
    private double averagePowerKw = 0.0;

    // =========================================================================
    // Métriques système
    // =========================================================================

    /**
     * Utilisation CPU en %.
     */
    @Builder.Default
    private double cpuUsage = 0.0;

    /**
     * Mémoire utilisée en MB.
     */
    @Builder.Default
    private double memoryUsedMb = 0.0;

    /**
     * Mémoire totale en MB.
     */
    @Builder.Default
    private double memoryTotalMb = 0.0;

    /**
     * Nombre de threads actifs.
     */
    @Builder.Default
    private int activeThreads = 0;

    /**
     * Connexions WebSocket actives.
     */
    @Builder.Default
    private int activeWebSockets = 0;

    // =========================================================================
    // Métriques de test de charge
    // =========================================================================

    /**
     * Test de charge en cours.
     */
    @Builder.Default
    private boolean loadTestRunning = false;

    /**
     * Nombre de sessions cibles pour le test.
     */
    @Builder.Default
    private int targetSessions = 0;

    /**
     * Progression du test (0-100).
     */
    @Builder.Default
    private double loadTestProgress = 0.0;

    /**
     * Durée du test en secondes.
     */
    @Builder.Default
    private long loadTestDurationSeconds = 0;

    /**
     * Taux d'erreur en %.
     */
    @Builder.Default
    private double errorRate = 0.0;

    /**
     * Throughput (transactions/seconde).
     */
    @Builder.Default
    private double throughput = 0.0;

    // =========================================================================
    // Statistiques par action OCPP
    // =========================================================================

    /**
     * Compteurs par action OCPP.
     */
    @Builder.Default
    private Map<String, Long> actionCounts = new HashMap<>();

    /**
     * Latences moyennes par action OCPP.
     */
    @Builder.Default
    private Map<String, Double> actionLatencies = new HashMap<>();

    /**
     * Taux de succès par action OCPP.
     */
    @Builder.Default
    private Map<String, Double> actionSuccessRates = new HashMap<>();

    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

    /**
     * Incrémente le compteur d'une action.
     */
    public void incrementActionCount(String action) {
        actionCounts.merge(action, 1L, Long::sum);
    }

    /**
     * Met à jour la latence d'une action.
     */
    public void updateActionLatency(String action, double latencyMs) {
        actionLatencies.merge(action, latencyMs,
                (old, newVal) -> (old + newVal) / 2);
    }

    /**
     * Calcule le taux d'utilisation mémoire.
     */
    public double getMemoryUsagePercent() {
        if (memoryTotalMb <= 0) return 0;
        return (memoryUsedMb / memoryTotalMb) * 100;
    }

    /**
     * Calcule le taux de sessions actives.
     */
    public double getActiveSessionsPercent() {
        if (totalSessions <= 0) return 0;
        return ((double) activeSessions / totalSessions) * 100;
    }

    /**
     * Crée un snapshot des métriques actuelles.
     */
    public static PerformanceMetrics snapshot() {
        Runtime runtime = Runtime.getRuntime();

        return PerformanceMetrics.builder()
                .timestamp(LocalDateTime.now())
                .memoryUsedMb((runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0))
                .memoryTotalMb(runtime.maxMemory() / (1024.0 * 1024.0))
                .activeThreads(Thread.activeCount())
                .build();
    }
}