package com.evse.simulator.performance;

import com.evse.simulator.performance.model.PerfMetrics;
import lombok.extern.slf4j.Slf4j;
import org.HdrHistogram.Histogram;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collecteur de metriques haute precision avec HdrHistogram.
 * Calcule les percentiles P50, P95, P99 en temps reel.
 */
@Slf4j
public class MetricsCollector {

    // Histogrammes pour les latences (en microsecondes pour plus de precision)
    private final Histogram connectionLatencyHisto;
    private final Histogram bootLatencyHisto;
    private final Histogram messageLatencyHisto;

    // Compteurs atomiques
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);

    // Pour calcul throughput
    private final AtomicLong lastMessageCount = new AtomicLong(0);
    private volatile long lastThroughputTime = System.currentTimeMillis();
    private volatile double currentThroughput = 0;

    private final AtomicLong lastConnectionCount = new AtomicLong(0);
    private volatile long lastConnectionTime = System.currentTimeMillis();
    private volatile double currentConnectionsPerSec = 0;

    // Configuration
    private final int targetConnections;

    public MetricsCollector(int targetConnections) {
        this.targetConnections = targetConnections;

        // Histogrammes: plage 1us a 60s, 3 chiffres significatifs
        this.connectionLatencyHisto = new Histogram(1, 60_000_000, 3);
        this.bootLatencyHisto = new Histogram(1, 60_000_000, 3);
        this.messageLatencyHisto = new Histogram(1, 60_000_000, 3);
    }

    /**
     * Enregistre une latence de connexion (en ms).
     */
    public void recordConnectionLatency(double latencyMs) {
        try {
            connectionLatencyHisto.recordValue((long) (latencyMs * 1000));
        } catch (Exception e) {
            log.trace("Latence hors plage: {}ms", latencyMs);
        }
    }

    /**
     * Enregistre une latence de boot (en ms).
     */
    public void recordBootLatency(double latencyMs) {
        try {
            bootLatencyHisto.recordValue((long) (latencyMs * 1000));
        } catch (Exception e) {
            log.trace("Latence boot hors plage: {}ms", latencyMs);
        }
    }

    /**
     * Enregistre une latence de message (en ms).
     */
    public void recordMessageLatency(double latencyMs) {
        try {
            messageLatencyHisto.recordValue((long) (latencyMs * 1000));
        } catch (Exception e) {
            log.trace("Latence message hors plage: {}ms", latencyMs);
        }
    }

    public void incrementMessagesSent() {
        messagesSent.incrementAndGet();
    }

    public void incrementMessagesReceived() {
        messagesReceived.incrementAndGet();
    }

    public void incrementErrors() {
        errors.incrementAndGet();
    }

    public void addMessagesSent(long count) {
        messagesSent.addAndGet(count);
    }

    public void addMessagesReceived(long count) {
        messagesReceived.addAndGet(count);
    }

    /**
     * Met a jour les calculs de throughput.
     */
    public void updateThroughput(int currentConnections) {
        long now = System.currentTimeMillis();
        long currentMessages = messagesReceived.get();

        // Throughput messages
        long timeDelta = now - lastThroughputTime;
        if (timeDelta > 0) {
            long messageDelta = currentMessages - lastMessageCount.get();
            currentThroughput = (messageDelta * 1000.0) / timeDelta;
            lastMessageCount.set(currentMessages);
            lastThroughputTime = now;
        }

        // Connexions par seconde
        long connTimeDelta = now - lastConnectionTime;
        if (connTimeDelta > 0) {
            long connDelta = currentConnections - lastConnectionCount.get();
            currentConnectionsPerSec = (connDelta * 1000.0) / connTimeDelta;
            lastConnectionCount.set(currentConnections);
            lastConnectionTime = now;
        }
    }

    /**
     * Genere un snapshot des metriques actuelles.
     */
    public PerfMetrics getMetrics(int activeConnections, int successfulConnections, int failedConnections) {
        updateThroughput(successfulConnections);

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        double progress = targetConnections > 0
            ? (successfulConnections * 100.0) / targetConnections
            : 0;

        return PerfMetrics.builder()
                .timestamp(Instant.now())
                .activeConnections(activeConnections)
                .successfulConnections(successfulConnections)
                .failedConnections(failedConnections)
                .totalMessagesSent(messagesSent.get())
                .totalMessagesReceived(messagesReceived.get())
                .totalErrors(errors.get())
                // Latences connexion
                .connectionLatencyAvgMs(microToMilli(connectionLatencyHisto.getMean()))
                .connectionLatencyP50Ms(microToMilli(connectionLatencyHisto.getValueAtPercentile(50)))
                .connectionLatencyP95Ms(microToMilli(connectionLatencyHisto.getValueAtPercentile(95)))
                .connectionLatencyP99Ms(microToMilli(connectionLatencyHisto.getValueAtPercentile(99)))
                .connectionLatencyMaxMs(microToMilli(connectionLatencyHisto.getMaxValue()))
                // Latences messages
                .messageLatencyAvgMs(microToMilli(messageLatencyHisto.getMean()))
                .messageLatencyP50Ms(microToMilli(messageLatencyHisto.getValueAtPercentile(50)))
                .messageLatencyP95Ms(microToMilli(messageLatencyHisto.getValueAtPercentile(95)))
                .messageLatencyP99Ms(microToMilli(messageLatencyHisto.getValueAtPercentile(99)))
                // Throughput
                .throughputMsgPerSec(currentThroughput)
                .connectionsPerSec(currentConnectionsPerSec)
                // Ressources
                .memoryUsedMb(usedMemory)
                .memoryMaxMb(maxMemory)
                .threadCount(Thread.activeCount())
                // Progression
                .progressPercent(progress)
                .targetConnections(targetConnections)
                .build();
    }

    /**
     * Remet les compteurs a zero.
     */
    public void reset() {
        connectionLatencyHisto.reset();
        bootLatencyHisto.reset();
        messageLatencyHisto.reset();
        messagesSent.set(0);
        messagesReceived.set(0);
        errors.set(0);
        lastMessageCount.set(0);
        lastConnectionCount.set(0);
        currentThroughput = 0;
        currentConnectionsPerSec = 0;
    }

    /**
     * Retourne les statistiques detaillees sous forme de texte.
     */
    public String getDetailedStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Statistiques de performance ===\n");
        sb.append(String.format("Messages envoyes: %d\n", messagesSent.get()));
        sb.append(String.format("Messages recus: %d\n", messagesReceived.get()));
        sb.append(String.format("Erreurs: %d\n", errors.get()));
        sb.append(String.format("Throughput: %.2f msg/s\n", currentThroughput));
        sb.append("\n--- Latences connexion (ms) ---\n");
        sb.append(String.format("  Moyenne: %.2f\n", microToMilli(connectionLatencyHisto.getMean())));
        sb.append(String.format("  P50: %.2f\n", microToMilli(connectionLatencyHisto.getValueAtPercentile(50))));
        sb.append(String.format("  P95: %.2f\n", microToMilli(connectionLatencyHisto.getValueAtPercentile(95))));
        sb.append(String.format("  P99: %.2f\n", microToMilli(connectionLatencyHisto.getValueAtPercentile(99))));
        sb.append(String.format("  Max: %.2f\n", microToMilli(connectionLatencyHisto.getMaxValue())));
        sb.append("\n--- Latences messages (ms) ---\n");
        sb.append(String.format("  Moyenne: %.2f\n", microToMilli(messageLatencyHisto.getMean())));
        sb.append(String.format("  P50: %.2f\n", microToMilli(messageLatencyHisto.getValueAtPercentile(50))));
        sb.append(String.format("  P95: %.2f\n", microToMilli(messageLatencyHisto.getValueAtPercentile(95))));
        sb.append(String.format("  P99: %.2f\n", microToMilli(messageLatencyHisto.getValueAtPercentile(99))));
        return sb.toString();
    }

    private double microToMilli(double microSeconds) {
        return microSeconds / 1000.0;
    }

    private double microToMilli(long microSeconds) {
        return microSeconds / 1000.0;
    }

    // Getters
    public long getMessagesSent() {
        return messagesSent.get();
    }

    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    public long getErrors() {
        return errors.get();
    }

    public double getThroughput() {
        return currentThroughput;
    }
}
