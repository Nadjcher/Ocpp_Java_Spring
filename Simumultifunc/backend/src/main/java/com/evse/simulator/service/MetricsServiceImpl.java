package com.evse.simulator.service;

import com.evse.simulator.config.MetricsProperties;
import com.evse.simulator.domain.service.BroadcastService;
import com.evse.simulator.domain.service.MetricsService;
import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.PerformanceMetrics;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implémentation du service de métriques.
 * <p>
 * Collecte les métriques en temps réel et les diffuse via WebSocket.
 * </p>
 */
@Service
@Slf4j
public class MetricsServiceImpl implements MetricsService {

    private final SessionService sessionService;
    private final OCPPService ocppService;
    private final BroadcastService broadcaster;
    private final MetricsProperties metricsProperties;

    // Compteurs de messages
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong errorsCount = new AtomicLong(0);

    // Latences (ring buffer pour les dernières mesures)
    private final Queue<Long> latencies = new ConcurrentLinkedQueue<>();

    // Timestamp de début pour calcul du throughput
    private volatile long metricsStartTime = System.currentTimeMillis();

    public MetricsServiceImpl(SessionService sessionService,
                              OCPPService ocppService,
                              BroadcastService broadcaster,
                              MetricsProperties metricsProperties) {
        this.sessionService = sessionService;
        this.ocppService = ocppService;
        this.broadcaster = broadcaster;
        this.metricsProperties = metricsProperties;
    }

    @Override
    @Scheduled(fixedDelayString = "${metrics.broadcast-interval:1000}")
    public void collectAndBroadcastMetrics() {
        PerformanceMetrics metrics = collectMetrics();
        broadcaster.broadcastMetrics(metrics);
    }

    @Override
    public PerformanceMetrics collectMetrics() {
        List<Session> allSessions = sessionService.getAllSessions();

        // Compter par état
        int totalSessions = allSessions.size();
        int activeSessions = (int) allSessions.stream().filter(Session::isConnected).count();
        int chargingSessions = (int) allSessions.stream().filter(Session::isCharging).count();
        int errorSessions = (int) allSessions.stream()
                .filter(s -> s.getState() == SessionState.FAULTED).count();

        // Calculer les totaux de charge
        double totalPowerKw = allSessions.stream()
                .filter(Session::isCharging)
                .mapToDouble(Session::getCurrentPowerKw)
                .sum();

        double totalEnergyKwh = allSessions.stream()
                .mapToDouble(Session::getEnergyDeliveredKwh)
                .sum();

        double avgPowerKw = chargingSessions > 0 ? totalPowerKw / chargingSessions : 0;

        // Calculer les latences
        LatencyStats latencyStats = calculateLatencyStats();

        // Métriques système
        Runtime runtime = Runtime.getRuntime();
        double memoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0);
        double memoryTotalMb = runtime.maxMemory() / (1024.0 * 1024.0);

        // Calculer le throughput (messages/seconde)
        long elapsed = System.currentTimeMillis() - metricsStartTime;
        double throughput = elapsed > 0 ? messagesSent.get() / (elapsed / 1000.0) : 0;

        // Calculer le taux d'erreur
        double errorRate = messagesSent.get() > 0 ?
                (errorsCount.get() * 100.0 / messagesSent.get()) : 0;

        return PerformanceMetrics.builder()
                .timestamp(LocalDateTime.now())
                .totalSessions(totalSessions)
                .activeSessions(activeSessions)
                .chargingSessions(chargingSessions)
                .errorSessions(errorSessions)
                .messagesSent(messagesSent.get())
                .messagesReceived(messagesReceived.get())
                .averageLatencyMs(latencyStats.avg)
                .maxLatencyMs(latencyStats.max)
                .minLatencyMs(latencyStats.min)
                .p95LatencyMs(latencyStats.p95)
                .p99LatencyMs(latencyStats.p99)
                .totalPowerKw(totalPowerKw)
                .totalEnergyKwh(totalEnergyKwh)
                .averagePowerKw(avgPowerKw)
                .cpuUsage(getProcessCpuUsage())
                .memoryUsedMb(memoryUsedMb)
                .memoryTotalMb(memoryTotalMb)
                .activeThreads(Thread.activeCount())
                .activeWebSockets(ocppService.getActiveConnectionsCount())
                .errorRate(errorRate)
                .throughput(throughput)
                .build();
    }

    @Override
    public void recordLatency(long latencyMs) {
        latencies.offer(latencyMs);
        while (latencies.size() > metricsProperties.getLatencySamples()) {
            latencies.poll();
        }
    }

    @Override
    public void incrementMessagesSent() {
        messagesSent.incrementAndGet();
    }

    @Override
    public void incrementMessagesReceived() {
        messagesReceived.incrementAndGet();
    }

    @Override
    public void incrementErrors() {
        errorsCount.incrementAndGet();
    }

    @Override
    public void resetCounters() {
        messagesSent.set(0);
        messagesReceived.set(0);
        errorsCount.set(0);
        latencies.clear();
        metricsStartTime = System.currentTimeMillis();
        log.info("Metrics counters reset");
    }

    @Override
    public long getMessagesSent() {
        return messagesSent.get();
    }

    @Override
    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    @Override
    public long getErrorsCount() {
        return errorsCount.get();
    }

    // =========================================================================
    // Private Methods
    // =========================================================================

    private LatencyStats calculateLatencyStats() {
        List<Long> latencyList = new ArrayList<>(latencies);
        if (latencyList.isEmpty()) {
            return new LatencyStats(0, 0, 0, 0, 0);
        }

        Collections.sort(latencyList);
        double avg = latencyList.stream().mapToLong(l -> l).average().orElse(0);
        double max = latencyList.stream().mapToLong(l -> l).max().orElse(0);
        double min = latencyList.stream().mapToLong(l -> l).min().orElse(0);
        double p95 = latencyList.get(Math.min((int) (latencyList.size() * 0.95), latencyList.size() - 1));
        double p99 = latencyList.get(Math.min((int) (latencyList.size() * 0.99), latencyList.size() - 1));

        return new LatencyStats(avg, min, max, p95, p99);
    }

    private double getProcessCpuUsage() {
        try {
            java.lang.management.OperatingSystemMXBean os =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                return sunOs.getProcessCpuLoad() * 100;
            }
            return os.getSystemLoadAverage();
        } catch (Exception e) {
            return -1;
        }
    }

    private record LatencyStats(double avg, double min, double max, double p95, double p99) {}
}
