package com.evse.simulator.domain.service;

import com.evse.simulator.model.PerformanceMetrics;
import com.evse.simulator.model.Session;

/**
 * Interface de gestion des métriques de performance.
 *
 * @deprecated Cette interface est dépréciée. Utilisez {@link MetricsService}
 *             pour les métriques et {@link LoadTestService} pour les tests de charge.
 */
@Deprecated(since = "2.0", forRemoval = true)
public interface PerformanceService {

    // Metrics Collection - use MetricsService instead
    void collectAndBroadcastMetrics();
    PerformanceMetrics collectMetrics();

    // Counters - use MetricsService instead
    void recordLatency(long latencyMs);
    void incrementMessagesSent();
    void incrementMessagesReceived();
    void incrementErrors();
    void resetCounters();

    // Load Testing - use LoadTestService instead
    String startLoadTest(int targetSessions, int rampUpSeconds, Session sessionTemplate);
    void stopLoadTest();
}
