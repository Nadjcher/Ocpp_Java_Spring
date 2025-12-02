package com.evse.simulator.service;

import com.evse.simulator.domain.service.LoadTestService;
import com.evse.simulator.domain.service.MetricsService;
import com.evse.simulator.model.PerformanceMetrics;
import com.evse.simulator.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service de performance et de métriques (Facade).
 * <p>
 * Cette classe délègue aux services spécialisés {@link MetricsService}
 * et {@link LoadTestService} pour la rétrocompatibilité.
 * </p>
 *
 * @deprecated Utilisez directement {@link MetricsService} et {@link LoadTestService}
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Deprecated(since = "2.0", forRemoval = true)
@SuppressWarnings("deprecation")
public class PerformanceService implements com.evse.simulator.domain.service.PerformanceService {

    private final MetricsService metricsService;
    private final LoadTestService loadTestService;

    // =========================================================================
    // Metrics Delegation
    // =========================================================================

    @Override
    public void collectAndBroadcastMetrics() {
        metricsService.collectAndBroadcastMetrics();
    }

    @Override
    public PerformanceMetrics collectMetrics() {
        return metricsService.collectMetrics();
    }

    @Override
    public void recordLatency(long latencyMs) {
        metricsService.recordLatency(latencyMs);
    }

    @Override
    public void incrementMessagesSent() {
        metricsService.incrementMessagesSent();
    }

    @Override
    public void incrementMessagesReceived() {
        metricsService.incrementMessagesReceived();
    }

    @Override
    public void incrementErrors() {
        metricsService.incrementErrors();
    }

    @Override
    public void resetCounters() {
        metricsService.resetCounters();
    }

    // =========================================================================
    // Load Test Delegation
    // =========================================================================

    @Override
    public String startLoadTest(int targetSessions, int rampUpSeconds, Session sessionTemplate) {
        return loadTestService.startLoadTest(targetSessions, rampUpSeconds, sessionTemplate);
    }

    @Override
    public void stopLoadTest() {
        loadTestService.stopLoadTest();
    }

    /**
     * Récupère l'état du test de charge.
     */
    public LoadTestService.LoadTestStatus getLoadTestStatus() {
        return loadTestService.getLoadTestStatus();
    }

    /**
     * Récupère les statistiques par session.
     */
    public List<LoadTestService.SessionStats> getSessionStats() {
        return loadTestService.getSessionStats();
    }
}
