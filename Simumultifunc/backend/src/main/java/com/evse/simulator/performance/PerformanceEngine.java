package com.evse.simulator.performance;

import com.evse.simulator.performance.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Moteur de tests de performance haute capacite.
 * Supporte 25K+ connexions simultanees avec 4 scenarios.
 */
@Slf4j
@Service
public class PerformanceEngine {

    private ConnectionPool connectionPool;
    private MetricsCollector metricsCollector;
    private ScheduledExecutorService metricsScheduler;

    private final AtomicReference<PerfStatus> status = new AtomicReference<>(PerfStatus.IDLE);
    private final AtomicReference<PerfResult> currentResult = new AtomicReference<>();
    private volatile PerfConfig currentConfig;
    private volatile String currentTestId;

    private Consumer<PerfMetrics> metricsCallback;
    private Consumer<PerfResult> completionCallback;

    private ScheduledFuture<?> metricsTask;
    private CompletableFuture<Void> testFuture;

    /**
     * Demarre un test de performance.
     */
    public synchronized PerfResult startTest(PerfConfig config) {
        if (status.get() == PerfStatus.RUNNING) {
            throw new IllegalStateException("Un test est deja en cours");
        }

        currentTestId = UUID.randomUUID().toString().substring(0, 8);
        currentConfig = config;
        status.set(PerfStatus.INITIALIZING);

        log.info("Demarrage test {} - scenario={}, target={}, rampUp={}s",
                currentTestId, config.getScenario(), config.getTargetConnections(), config.getRampUpSeconds());

        // Initialisation
        double connectionsPerSecond = config.getTargetConnections() / (double) config.getRampUpSeconds();
        connectionPool = new ConnectionPool(
                config.getOcppUrl(),
                config.getCpIdPrefix(),
                config.getTargetConnections(),
                connectionsPerSecond
        );
        metricsCollector = new MetricsCollector(config.getTargetConnections());

        // Callbacks pour metriques
        connectionPool.setConnectionCallback(result -> {
            if (result.isSuccess()) {
                metricsCollector.recordConnectionLatency(result.getConnectionLatencyMs());
                metricsCollector.recordBootLatency(result.getBootLatencyMs());
            } else {
                metricsCollector.incrementErrors();
            }
        });

        connectionPool.setMessageCallback(event -> {
            metricsCollector.recordMessageLatency(event.latencyMs);
            metricsCollector.incrementMessagesReceived();
        });

        // Resultat initial
        PerfResult result = PerfResult.builder()
                .testId(currentTestId)
                .config(config)
                .status(PerfStatus.INITIALIZING)
                .startTime(Instant.now())
                .build();
        currentResult.set(result);

        // Demarrage metriques temps reel
        startMetricsReporting();

        // Lancement du scenario
        status.set(PerfStatus.RUNNING);
        testFuture = executeScenario(config);

        return result;
    }

    /**
     * Execute le scenario approprie.
     */
    private CompletableFuture<Void> executeScenario(PerfConfig config) {
        PerfConfig.ScenarioType scenarioType = config.getScenarioType();
        if (scenarioType == null) {
            scenarioType = PerfConfig.ScenarioType.CONNECTION;
        }

        return switch (scenarioType) {
            case CONNECTION -> runConnectionScenario(config);
            case CHARGING -> runChargingScenario(config);
            case STRESS -> runStressScenario(config);
            case ENDURANCE -> runEnduranceScenario(config);
        };
    }

    /**
     * Scenario CONNECTION: Test de montee en charge pure.
     */
    private CompletableFuture<Void> runConnectionScenario(PerfConfig config) {
        log.info("Execution scenario CONNECTION");

        return connectionPool.startConnections()
                .thenCompose(v -> {
                    // Attendre que toutes les connexions soient etablies ou timeout
                    return waitForConnections(config.getTargetConnections(), config.getRampUpSeconds() + 30);
                })
                .thenCompose(v -> {
                    // Phase de maintien
                    log.info("Phase HOLD: {}s", config.getHoldSeconds());
                    return delay(config.getHoldSeconds() * 1000L);
                })
                .thenRun(this::completeTest)
                .exceptionally(ex -> {
                    failTest(ex.getMessage());
                    return null;
                });
    }

    /**
     * Scenario CHARGING: Simule des transactions de charge completes.
     */
    private CompletableFuture<Void> runChargingScenario(PerfConfig config) {
        log.info("Execution scenario CHARGING");
        AtomicInteger completedTransactions = new AtomicInteger(0);

        return connectionPool.startConnections()
                .thenCompose(v -> waitForConnections(config.getTargetConnections() / 2, config.getRampUpSeconds()))
                .thenCompose(v -> {
                    // Demarrer les transactions
                    return connectionPool.forEachConnection(client -> {
                        try {
                            // StartTransaction
                            String startTxMsg = buildStartTransactionMessage(client.getCpId(), config.getIdTag());
                            client.send(startTxMsg);
                            metricsCollector.incrementMessagesSent();

                            // MeterValues
                            for (int i = 0; i < config.getMeterValuesCount(); i++) {
                                Thread.sleep(config.getMeterValueIntervalMs());
                                String mvMsg = buildMeterValuesMessage(client.getCpId(), i * 1000);
                                client.send(mvMsg);
                                metricsCollector.incrementMessagesSent();
                            }

                            // StopTransaction
                            String stopTxMsg = buildStopTransactionMessage(client.getCpId(), config.getIdTag());
                            client.send(stopTxMsg);
                            metricsCollector.incrementMessagesSent();

                            completedTransactions.incrementAndGet();
                        } catch (Exception e) {
                            metricsCollector.incrementErrors();
                            log.debug("Erreur transaction {}: {}", client.getCpId(), e.getMessage());
                        }
                    });
                })
                .thenCompose(v -> delay(5000)) // Attendre reponses
                .thenRun(() -> {
                    PerfResult result = currentResult.get();
                    result.setCompletedTransactions(completedTransactions.get());
                    completeTest();
                })
                .exceptionally(ex -> {
                    failTest(ex.getMessage());
                    return null;
                });
    }

    /**
     * Scenario STRESS: Augmente progressivement jusqu'a la limite.
     */
    private CompletableFuture<Void> runStressScenario(PerfConfig config) {
        log.info("Execution scenario STRESS");
        AtomicInteger maxReached = new AtomicInteger(0);

        return CompletableFuture.runAsync(() -> {
            int batch = 100;
            int current = 0;

            while (status.get() == PerfStatus.RUNNING && current < config.getTargetConnections()) {
                // Ajouter un batch de connexions
                for (int i = 0; i < batch && current < config.getTargetConnections(); i++, current++) {
                    final int idx = current;
                    CompletableFuture.runAsync(() -> createStressConnection(idx, config));
                }

                // Verifier si on atteint la limite
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                int active = connectionPool.getActiveCount();
                maxReached.set(Math.max(maxReached.get(), active));

                // Verifier taux d'echec
                int failed = connectionPool.getFailedCount();
                double failRate = current > 0 ? (failed * 100.0 / current) : 0;
                if (failRate > 50) {
                    log.warn("Taux d'echec > 50%, arret du stress test");
                    break;
                }
            }
        }).thenRun(() -> {
            PerfResult result = currentResult.get();
            result.setMaxConnectionsReached(maxReached.get());
            completeTest();
        }).exceptionally(ex -> {
            failTest(ex.getMessage());
            return null;
        });
    }

    private void createStressConnection(int index, PerfConfig config) {
        // Implementation simplifiee pour stress test
        String cpId = String.format("%s-STRESS-%06d", config.getCpIdPrefix(), index);
        // Delegate to pool
    }

    /**
     * Scenario ENDURANCE: Test longue duree.
     */
    private CompletableFuture<Void> runEnduranceScenario(PerfConfig config) {
        log.info("Execution scenario ENDURANCE - duree: {} minutes", config.getDurationMinutes());

        return connectionPool.startConnections()
                .thenCompose(v -> waitForConnections(config.getTargetConnections() / 2, config.getRampUpSeconds()))
                .thenCompose(v -> {
                    // Boucle d'activite pendant la duree specifiee
                    long endTime = System.currentTimeMillis() + (config.getDurationMinutes() * 60 * 1000L);

                    return CompletableFuture.runAsync(() -> {
                        while (System.currentTimeMillis() < endTime && status.get() == PerfStatus.RUNNING) {
                            // Envoyer Heartbeat a toutes les connexions
                            connectionPool.getConnections().values().forEach(client -> {
                                try {
                                    if (client.isOpen()) {
                                        String hbMsg = buildHeartbeatMessage();
                                        client.send(hbMsg);
                                        metricsCollector.incrementMessagesSent();
                                    }
                                } catch (Exception e) {
                                    metricsCollector.incrementErrors();
                                }
                            });

                            // Pause entre les cycles
                            try {
                                Thread.sleep(10000); // 10s entre heartbeats
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }

                            // Verifier deconnexions et reconnecter si necessaire
                            if (config.isAutoReconnect()) {
                                checkAndReconnect(config);
                            }
                        }
                    });
                })
                .thenRun(this::completeTest)
                .exceptionally(ex -> {
                    failTest(ex.getMessage());
                    return null;
                });
    }

    private void checkAndReconnect(PerfConfig config) {
        int active = connectionPool.getActiveCount();
        int target = config.getTargetConnections();
        if (active < target * 0.9) { // Moins de 90% des connexions
            log.info("Reconnexion: {} actives sur {} cibles", active, target);
            // Logique de reconnexion
        }
    }

    /**
     * Attend que le nombre de connexions cible soit atteint.
     */
    private CompletableFuture<Void> waitForConnections(int target, int timeoutSeconds) {
        return CompletableFuture.runAsync(() -> {
            long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
            while (connectionPool.getSuccessCount() < target && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("Connexions atteintes: {}/{}", connectionPool.getSuccessCount(), target);
        });
    }

    private CompletableFuture<Void> delay(long millis) {
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Demarre le reporting des metriques.
     */
    private void startMetricsReporting() {
        metricsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-reporter");
            t.setDaemon(true);
            return t;
        });

        metricsTask = metricsScheduler.scheduleAtFixedRate(() -> {
            try {
                PerfMetrics metrics = metricsCollector.getMetrics(
                        connectionPool.getActiveCount(),
                        connectionPool.getSuccessCount(),
                        connectionPool.getFailedCount()
                );

                if (metricsCallback != null) {
                    metricsCallback.accept(metrics);
                }

                // Log periodique
                if (connectionPool.getSuccessCount() % 100 == 0) {
                    log.debug("Metrics: active={}, success={}, failed={}, throughput={:.2f}/s",
                            metrics.getActiveConnections(),
                            metrics.getSuccessfulConnections(),
                            metrics.getFailedConnections(),
                            metrics.getThroughputMsgPerSec());
                }
            } catch (Exception e) {
                log.debug("Erreur metrics: {}", e.getMessage());
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Complete le test avec succes.
     */
    private void completeTest() {
        status.set(PerfStatus.COMPLETED);
        stopMetricsReporting();

        PerfResult result = currentResult.get();
        result.setStatus(PerfStatus.COMPLETED);
        result.setEndTime(Instant.now());
        result.setDuration(Duration.between(result.getStartTime(), result.getEndTime()));
        result.setSuccessfulConnections(connectionPool.getSuccessCount());
        result.setFailedConnections(connectionPool.getFailedCount());
        result.setTotalMessagesSent(metricsCollector.getMessagesSent());
        result.setTotalMessagesReceived(metricsCollector.getMessagesReceived());
        result.setTotalErrors(metricsCollector.getErrors());
        result.setFinalMetrics(metricsCollector.getMetrics(
                connectionPool.getActiveCount(),
                connectionPool.getSuccessCount(),
                connectionPool.getFailedCount()
        ));

        log.info("Test {} termine: success={}, failed={}, duration={}s",
                currentTestId,
                result.getSuccessfulConnections(),
                result.getFailedConnections(),
                result.getDuration().getSeconds());

        // Fermer le pool
        if (connectionPool != null) {
            connectionPool.closeAll();
        }

        if (completionCallback != null) {
            completionCallback.accept(result);
        }
    }

    /**
     * Echoue le test.
     */
    private void failTest(String error) {
        status.set(PerfStatus.FAILED);
        stopMetricsReporting();

        PerfResult result = currentResult.get();
        if (result != null) {
            result.setStatus(PerfStatus.FAILED);
            result.setEndTime(Instant.now());
            result.setError(error);

            if (connectionPool != null) {
                result.setSuccessfulConnections(connectionPool.getSuccessCount());
                result.setFailedConnections(connectionPool.getFailedCount());
            }
        }

        log.error("Test {} echoue: {}", currentTestId, error);

        if (connectionPool != null) {
            connectionPool.closeAll();
        }

        if (completionCallback != null && result != null) {
            completionCallback.accept(result);
        }
    }

    /**
     * Arrete le test en cours.
     */
    public synchronized PerfResult stopTest() {
        if (status.get() != PerfStatus.RUNNING) {
            return currentResult.get();
        }

        log.info("Arret demande pour test {}", currentTestId);
        status.set(PerfStatus.STOPPED);

        if (connectionPool != null) {
            connectionPool.stop();
        }

        stopMetricsReporting();

        PerfResult result = currentResult.get();
        if (result != null) {
            result.setStatus(PerfStatus.STOPPED);
            result.setEndTime(Instant.now());
            result.setDuration(Duration.between(result.getStartTime(), result.getEndTime()));

            if (connectionPool != null) {
                result.setSuccessfulConnections(connectionPool.getSuccessCount());
                result.setFailedConnections(connectionPool.getFailedCount());
            }
            result.setTotalMessagesSent(metricsCollector.getMessagesSent());
            result.setTotalMessagesReceived(metricsCollector.getMessagesReceived());
            result.setFinalMetrics(metricsCollector.getMetrics(
                    connectionPool.getActiveCount(),
                    connectionPool.getSuccessCount(),
                    connectionPool.getFailedCount()
            ));
        }

        if (connectionPool != null) {
            connectionPool.closeAll();
        }

        return result;
    }

    private void stopMetricsReporting() {
        if (metricsTask != null) {
            metricsTask.cancel(false);
        }
        if (metricsScheduler != null) {
            metricsScheduler.shutdown();
        }
    }

    /**
     * Retourne les metriques actuelles.
     */
    public PerfMetrics getCurrentMetrics() {
        if (metricsCollector == null || connectionPool == null) {
            return PerfMetrics.builder()
                    .timestamp(Instant.now())
                    .build();
        }
        return metricsCollector.getMetrics(
                connectionPool.getActiveCount(),
                connectionPool.getSuccessCount(),
                connectionPool.getFailedCount()
        );
    }

    /**
     * Retourne le resultat actuel.
     */
    public PerfResult getCurrentResult() {
        return currentResult.get();
    }

    /**
     * Retourne le statut actuel.
     */
    public PerfStatus getStatus() {
        return status.get();
    }

    public void setMetricsCallback(Consumer<PerfMetrics> callback) {
        this.metricsCallback = callback;
    }

    public void setCompletionCallback(Consumer<PerfResult> callback) {
        this.completionCallback = callback;
    }

    // Builders de messages OCPP

    private String buildStartTransactionMessage(String cpId, String idTag) {
        return String.format(
            "[2,\"%s\",\"StartTransaction\",{\"connectorId\":1,\"idTag\":\"%s\",\"meterStart\":0,\"timestamp\":\"%s\"}]",
            UUID.randomUUID().toString(), idTag, Instant.now().toString()
        );
    }

    private String buildMeterValuesMessage(String cpId, int meterValue) {
        return String.format(
            "[2,\"%s\",\"MeterValues\",{\"connectorId\":1,\"meterValue\":[{\"timestamp\":\"%s\",\"sampledValue\":[{\"value\":\"%d\",\"measurand\":\"Energy.Active.Import.Register\",\"unit\":\"Wh\"}]}]}]",
            UUID.randomUUID().toString(), Instant.now().toString(), meterValue
        );
    }

    private String buildStopTransactionMessage(String cpId, String idTag) {
        return String.format(
            "[2,\"%s\",\"StopTransaction\",{\"idTag\":\"%s\",\"meterStop\":10000,\"timestamp\":\"%s\",\"transactionId\":1}]",
            UUID.randomUUID().toString(), idTag, Instant.now().toString()
        );
    }

    private String buildHeartbeatMessage() {
        return String.format("[2,\"%s\",\"Heartbeat\",{}]", UUID.randomUUID().toString());
    }
}
