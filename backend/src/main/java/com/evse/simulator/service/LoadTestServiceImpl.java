package com.evse.simulator.service;

import com.evse.simulator.config.LoadTestProperties;
import com.evse.simulator.domain.service.LoadTestService;
import com.evse.simulator.domain.service.MetricsService;
import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.ConnectorStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implémentation du service de tests de charge optimisée pour 25k+ connexions.
 * <p>
 * Gère la création massive de sessions avec:
 * - Exécution par batches parallèles
 * - Compteurs thread-safe
 * - Nettoyage automatique
 * - Métriques temps réel
 * </p>
 */
@Service
@Slf4j
public class LoadTestServiceImpl implements LoadTestService {

    private final SessionService sessionService;
    private final OCPPService ocppService;
    private final MetricsService metricsService;
    private final LoadTestProperties loadTestProperties;

    // Compteurs thread-safe pour 25k connexions
    private final AtomicBoolean loadTestRunning = new AtomicBoolean(false);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger successfulConnections = new AtomicInteger(0);
    private final AtomicInteger failedConnections = new AtomicInteger(0);

    private volatile int loadTestTarget = 0;
    private volatile int maxAchieved = 0;
    private volatile long loadTestStartTime = 0;
    private volatile String currentRunId = null;

    private ScheduledFuture<?> loadTestTask;
    private final List<String> createdSessionIds = Collections.synchronizedList(new ArrayList<>());

    // Pool optimisé pour 25k connexions
    private final ExecutorService connectionExecutor;
    private final ScheduledExecutorService scheduler;

    public LoadTestServiceImpl(SessionService sessionService,
                               OCPPService ocppService,
                               MetricsService metricsService,
                               LoadTestProperties loadTestProperties) {
        this.sessionService = sessionService;
        this.ocppService = ocppService;
        this.metricsService = metricsService;
        this.loadTestProperties = loadTestProperties;

        // Thread pool optimisé pour connexions massives
        int poolSize = Math.min(loadTestProperties.getThreadPoolSize(),
                               Runtime.getRuntime().availableProcessors() * 10);
        this.connectionExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "perf-conn-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.scheduler = Executors.newScheduledThreadPool(10);

        log.info("LoadTestService initialized with {} connection threads", poolSize);
    }

    // Liste des sessions pré-configurées (pour le mode CSV avec cpId/idTag exacts)
    private volatile List<Map<String, Object>> sessionConfigList = null;

    @Override
    public synchronized String startLoadTestWithList(List<Map<String, Object>> sessionConfigs, int rampUpSeconds, int holdSeconds) {
        if (loadTestRunning.get()) {
            throw new IllegalStateException("Un test de charge est déjà en cours");
        }

        if (sessionConfigs == null || sessionConfigs.isEmpty()) {
            throw new IllegalArgumentException("La liste de sessions ne peut pas être vide");
        }

        int targetSessions = sessionConfigs.size();
        log.info("Starting load test with {} pre-configured sessions", targetSessions);

        // Extraire l'URL du premier élément (devrait être la même pour tous)
        String url = (String) sessionConfigs.get(0).get("url");
        Session template = Session.builder()
            .url(url)
            .build();

        // IMPORTANT: Stocker la liste AVANT d'appeler startLoadTest
        // car resetCounters() dans startLoadTest efface sessionConfigList
        List<Map<String, Object>> configListCopy = new ArrayList<>(sessionConfigs);

        // Appeler startLoadTest qui va faire resetCounters()
        String runId = startLoadTest(targetSessions, rampUpSeconds, holdSeconds, template);

        // IMPORTANT: Re-stocker la liste APRÈS resetCounters() a été appelé
        this.sessionConfigList = configListCopy;

        log.info("Session config list restored with {} entries, first cpId: {}",
                sessionConfigList.size(),
                sessionConfigList.get(0).get("cpId"));

        return runId;
    }

    @Override
    public synchronized String startLoadTest(int targetSessions, int rampUpSeconds, int holdSeconds, Session sessionTemplate) {
        // Vérifier si un test est déjà en cours (sans modifier l'état)
        if (loadTestRunning.get()) {
            throw new IllegalStateException("Un test de charge est déjà en cours");
        }

        // Validation
        if (targetSessions > loadTestProperties.getMaxSessions()) {
            targetSessions = loadTestProperties.getMaxSessions();
            log.warn("Target réduit au maximum: {}", targetSessions);
        }

        if (targetSessions <= 0) {
            throw new IllegalArgumentException("Le nombre de sessions doit être positif");
        }

        // IMPORTANT: Initialiser TOUTES les valeurs AVANT de marquer comme running
        // pour éviter les race conditions avec les polls de status
        resetCounters();
        loadTestTarget = targetSessions;
        loadTestStartTime = System.currentTimeMillis();
        currentRunId = "run-" + loadTestStartTime;
        metricsService.resetCounters();

        // MAINTENANT marquer comme running (après que toutes les valeurs sont définies)
        loadTestRunning.set(true);

        log.info("═══════════════════════════════════════════════════════════");
        log.info("STARTING LOAD TEST: target={}, rampUp={}s, hold={}s", targetSessions, rampUpSeconds, holdSeconds);
        log.info("Template URL: {}", sessionTemplate != null ? sessionTemplate.getUrl() : "NULL (no template!)");
        log.info("═══════════════════════════════════════════════════════════");

        // Calculer le délai entre chaque batch
        int batchSize = Math.min(targetSessions, Math.max(10, targetSessions / 100)); // 1% par batch, min 10, max = target
        int numBatches = Math.max(1, (targetSessions + batchSize - 1) / batchSize); // Éviter division par zéro
        int delayMs = rampUpSeconds > 0 ? Math.max(100, (rampUpSeconds * 1000) / numBatches) : 100;

        final int finalTarget = targetSessions;
        final int finalBatchSize = batchSize;
        final int finalHoldSeconds = holdSeconds;
        final Session template = sessionTemplate;

        // Lancer le test en arrière-plan
        CompletableFuture.runAsync(() -> {
            executeBatchLoadTest(finalTarget, finalBatchSize, delayMs, finalHoldSeconds, template);
        }, connectionExecutor).whenComplete((v, ex) -> {
            if (ex != null) {
                log.error("Load test failed: {}", ex.getMessage());
            }
            logTestCompletion();
        });

        return currentRunId;
    }

    /**
     * Exécute le test de charge par batches pour optimiser les performances.
     */
    private void executeBatchLoadTest(int target, int batchSize, int delayMs, int holdSeconds, Session template) {
        try {
            // Phase 1: Création des connexions
            for (int i = 0; i < target && loadTestRunning.get(); i += batchSize) {
                int batchEnd = Math.min(i + batchSize, target);
                int batchNum = (i / batchSize) + 1;
                int totalBatches = (target + batchSize - 1) / batchSize;

                log.info("Batch {}/{}: Creating sessions {}-{}", batchNum, totalBatches, i + 1, batchEnd);

                // Créer le batch en parallèle
                List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

                for (int j = i; j < batchEnd; j++) {
                    final int index = j;
                    batchFutures.add(CompletableFuture.runAsync(() -> {
                        try {
                            createAndConnectSessionOptimized(template, index);
                            int success = successfulConnections.incrementAndGet();
                            int current = activeConnections.incrementAndGet();
                            if (current > maxAchieved) {
                                maxAchieved = current;
                            }
                            if (success <= 3 || success % 100 == 0) {
                                log.info("Session {} connected successfully (total: {})", index, success);
                            }
                        } catch (Exception e) {
                            int failed = failedConnections.incrementAndGet();
                            // Log first 5 failures in detail, then every 100th
                            if (failed <= 5 || failed % 100 == 0) {
                                log.warn("Session {} failed (total failures: {}): {}", index, failed, e.getMessage());
                            }
                        }
                    }, connectionExecutor));
                }

                // Attendre la fin du batch
                try {
                    CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                        .get(60, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    log.warn("Batch {} timed out, continuing...", batchNum);
                }

                // Pause entre les batches
                if (i + batchSize < target && loadTestRunning.get()) {
                    Thread.sleep(delayMs);
                }
            }

            // Phase 2: Hold - maintenir les connexions actives avec MeterValues pendant holdSeconds
            if (holdSeconds > 0 && loadTestRunning.get() && successfulConnections.get() > 0) {
                log.info("═══════════════════════════════════════════════════════════");
                log.info("HOLD PHASE: Charging with {} sessions for {} seconds...",
                        activeConnections.get(), holdSeconds);
                log.info("═══════════════════════════════════════════════════════════");

                long holdEndTime = System.currentTimeMillis() + (holdSeconds * 1000L);
                int meterValuesIntervalSec = 10; // Envoyer MeterValues toutes les 10 secondes
                long lastMeterValuesSent = System.currentTimeMillis();

                while (System.currentTimeMillis() < holdEndTime && loadTestRunning.get()) {
                    long now = System.currentTimeMillis();
                    long remaining = (holdEndTime - now) / 1000;

                    // Envoyer MeterValues toutes les X secondes
                    if ((now - lastMeterValuesSent) >= (meterValuesIntervalSec * 1000L)) {
                        sendMeterValuesToAllSessions();
                        lastMeterValuesSent = now;
                        log.info("Hold phase: {}s remaining, {} active, {} msg sent, {} errors",
                                remaining, activeConnections.get(),
                                metricsService.getMessagesSent(), metricsService.getErrorsCount());
                    }

                    Thread.sleep(1000); // Check every second
                }

                // Envoyer StopTransaction pour terminer proprement les charges
                log.info("Hold phase completed. Stopping all transactions...");
                stopAllTransactions();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Load test interrupted");
        } catch (Exception e) {
            log.error("Load test error: {}", e.getMessage());
        } finally {
            loadTestRunning.set(false);
        }
    }

    private void logTestCompletion() {
        long duration = System.currentTimeMillis() - loadTestStartTime;
        double rate = successfulConnections.get() * 1000.0 / Math.max(duration, 1);

        log.info("═══════════════════════════════════════════════════════════");
        log.info("LOAD TEST COMPLETED");
        log.info("Target: {} | Success: {} | Failed: {} | Max: {}",
            loadTestTarget, successfulConnections.get(), failedConnections.get(), maxAchieved);
        log.info("Duration: {}ms | Rate: {}/sec", duration, String.format("%.1f", rate));
        log.info("═══════════════════════════════════════════════════════════");
    }

    @Override
    public void stopLoadTest() {
        if (!loadTestRunning.getAndSet(false)) {
            log.warn("Aucun test de charge en cours");
            return;
        }

        if (loadTestTask != null) {
            loadTestTask.cancel(false);
            loadTestTask = null;
        }

        log.info("Stopping load test and cleaning up {} sessions...", createdSessionIds.size());

        // Nettoyer les sessions de perf en parallèle
        cleanupPerfSessions();

        long duration = System.currentTimeMillis() - loadTestStartTime;
        log.info("Test de charge arrêté après {}ms. Success: {}, Failed: {}, Messages: {}, Errors: {}",
                duration,
                successfulConnections.get(),
                failedConnections.get(),
                metricsService.getMessagesSent(),
                metricsService.getErrorsCount());
    }

    /**
     * Nettoie toutes les sessions de performance créées.
     * Envoie StopTransaction au CSMS avant de déconnecter.
     */
    private void cleanupPerfSessions() {
        List<String> toDelete = new ArrayList<>(createdSessionIds);
        createdSessionIds.clear();

        // Aussi chercher les sessions perf- restantes
        List<Session> perfSessions = sessionService.getAllSessions().stream()
            .filter(s -> s.getId() != null && s.getId().startsWith("perf-"))
            .toList();

        perfSessions.stream().map(Session::getId).forEach(toDelete::add);

        log.info("Cleaning up {} perf sessions (sending StopTransaction to CSMS)...", toDelete.size());

        // Arrêter proprement chaque session en parallèle
        toDelete.parallelStream().forEach(id -> {
            try {
                Session session = sessionService.getSession(id);
                if (session != null && session.getTransactionId() != null && !session.getTransactionId().toString().isEmpty()) {
                    // Session en charge - envoyer StopTransaction
                    try {
                        log.debug("Sending StopTransaction for session {} (txId={})", id, session.getTransactionId());
                        ocppService.sendStopTransaction(id).get(5, TimeUnit.SECONDS);
                        metricsService.incrementMessagesSent();
                    } catch (Exception e) {
                        log.debug("StopTransaction failed for {}: {}", id, e.getMessage());
                    }
                }

                // Déconnecter et supprimer
                ocppService.disconnect(id);
                sessionService.deleteSession(id);
                activeConnections.decrementAndGet();
            } catch (Exception e) {
                log.debug("Failed to cleanup session {}: {}", id, e.getMessage());
            }
        });

        log.info("Cleanup complete. Active connections: {}", activeConnections.get());
    }

    @Override
    public boolean isLoadTestRunning() {
        return loadTestRunning.get();
    }

    @Override
    public String getCurrentRunId() {
        return currentRunId;
    }

    @Override
    public LoadTestStatus getLoadTestStatus() {
        if (!loadTestRunning.get() && loadTestStartTime == 0) {
            return LoadTestStatus.builder()
                .running(false)
                .targetSessions(0)
                .currentSessions(0)
                .connectedSessions(0)
                .build();
        }

        int currentSessions = successfulConnections.get();
        int completed = successfulConnections.get() + failedConnections.get();
        double progress = loadTestTarget > 0 ? (completed * 100.0 / loadTestTarget) : 0;

        return LoadTestStatus.builder()
                .running(loadTestRunning.get())
                .targetSessions(loadTestTarget)
                .currentSessions(currentSessions)
                .connectedSessions(activeConnections.get())
                .startTime(loadTestStartTime)
                .durationMs(System.currentTimeMillis() - loadTestStartTime)
                .messagesSent(metricsService.getMessagesSent())
                .messagesReceived(metricsService.getMessagesReceived())
                .errors(failedConnections.get() + metricsService.getErrorsCount())
                .progress(progress)
                .build();
    }

    @Override
    public List<SessionStats> getSessionStats() {
        return sessionService.getAllSessions().stream()
                .map(this::toSessionStats)
                .toList();
    }

    // =========================================================================
    // Private Methods
    // =========================================================================

    private void resetCounters() {
        activeConnections.set(0);
        successfulConnections.set(0);
        failedConnections.set(0);
        maxAchieved = 0;
        createdSessionIds.clear();
        sessionConfigList = null; // Clear la liste des configs pour le prochain test
    }

    /**
     * Envoie MeterValues à toutes les sessions actives en parallèle.
     */
    private void sendMeterValuesToAllSessions() {
        List<String> sessionIds = new ArrayList<>(createdSessionIds);
        if (sessionIds.isEmpty()) {
            return;
        }

        int batchSize = Math.min(100, sessionIds.size());
        AtomicInteger sent = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        // Envoyer par batch pour ne pas surcharger
        for (int i = 0; i < sessionIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, sessionIds.size());
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int j = i; j < end; j++) {
                String sessionId = sessionIds.get(j);
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        long start = System.nanoTime();
                        ocppService.sendMeterValues(sessionId).get(5, TimeUnit.SECONDS);
                        metricsService.incrementMessagesSent();
                        metricsService.recordLatency((System.nanoTime() - start) / 1_000_000);
                        sent.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        metricsService.incrementErrors();
                    }
                }, connectionExecutor));
            }

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("MeterValues batch timeout: {}", e.getMessage());
            }
        }

        log.debug("MeterValues sent to {} sessions ({} errors)", sent.get(), errors.get());
    }

    /**
     * Arrête toutes les transactions proprement.
     */
    private void stopAllTransactions() {
        List<String> sessionIds = new ArrayList<>(createdSessionIds);
        if (sessionIds.isEmpty()) {
            return;
        }

        log.info("Stopping {} transactions...", sessionIds.size());
        AtomicInteger stopped = new AtomicInteger(0);

        // Envoyer StopTransaction en parallèle
        List<CompletableFuture<Void>> futures = sessionIds.stream()
            .map(sessionId -> CompletableFuture.runAsync(() -> {
                try {
                    // StatusNotification (Finishing)
                    ocppService.sendStatusNotification(sessionId, ConnectorStatus.FINISHING).get(5, TimeUnit.SECONDS);
                    metricsService.incrementMessagesSent();

                    // StopTransaction
                    ocppService.sendStopTransaction(sessionId).get(10, TimeUnit.SECONDS);
                    metricsService.incrementMessagesSent();

                    // StatusNotification (Available)
                    ocppService.sendStatusNotification(sessionId, ConnectorStatus.AVAILABLE).get(5, TimeUnit.SECONDS);
                    metricsService.incrementMessagesSent();

                    stopped.incrementAndGet();
                } catch (Exception e) {
                    log.debug("Error stopping transaction {}: {}", sessionId, e.getMessage());
                }
            }, connectionExecutor))
            .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Stop transactions timeout: {}", e.getMessage());
        }

        log.info("Stopped {} transactions", stopped.get());
    }

    /**
     * Crée et connecte une session de manière optimisée pour les tests massifs.
     * Démarre le flux complet de charge: Boot → StatusNotification → Authorize → StartTransaction → Charging
     */
    private void createAndConnectSessionOptimized(Session template, int index) {
        String sessionId = "perf-" + loadTestStartTime + "-" + String.format("%05d", index);

        // Utiliser les valeurs exactes de la liste si disponibles
        String cpId;
        String idTag;
        String url;

        if (sessionConfigList != null && index < sessionConfigList.size()) {
            // Mode CSV: utiliser cpId et idTag exacts de la config
            Map<String, Object> config = sessionConfigList.get(index);
            cpId = (String) config.get("cpId");
            idTag = config.containsKey("idTag") ? (String) config.get("idTag") : "TEST-TAG";
            url = config.containsKey("url") ? (String) config.get("url") : (template != null ? template.getUrl() : null);

            if (index <= 3 || index % 100 == 0) {
                log.info("Creating session {} with exact cpId={}, idTag={}", index, cpId, idTag);
            }
        } else {
            // Mode standard: générer cpId avec index
            cpId = (template != null && template.getCpId() != null ? template.getCpId() : "PERF")
                          + "-" + String.format("%05d", index);
            idTag = template != null ? template.getIdTag() : "PERF-TAG";
            url = template != null ? template.getUrl() : null;
        }

        Session session = Session.builder()
                .id(sessionId)
                .title("Perf #" + index)
                .url(url)
                .cpId(cpId)
                .bearerToken(template != null ? template.getBearerToken() : null)
                .vehicleProfile(template != null ? template.getVehicleProfile() : "PERF_TEST")
                .chargerType(template != null ? template.getChargerType() : ChargerType.AC_TRI)
                .idTag(idTag)
                .soc(template != null ? template.getSoc() : 50.0)
                .targetSoc(template != null ? template.getTargetSoc() : 80.0)
                .build();

        Session created = sessionService.createSession(session);
        createdSessionIds.add(created.getId());

        // Connecter et démarrer le flux complet de charge OCPP
        try {
            Boolean connected = ocppService.connect(created.getId()).get(10, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(connected)) {
                long start = System.nanoTime();

                // 1. BootNotification
                ocppService.sendBootNotification(created.getId()).get(10, TimeUnit.SECONDS);
                metricsService.incrementMessagesSent();

                // 2. StatusNotification (Available)
                ocppService.sendStatusNotification(created.getId(), ConnectorStatus.AVAILABLE).get(5, TimeUnit.SECONDS);
                metricsService.incrementMessagesSent();

                // 3. Authorize
                ocppService.sendAuthorize(created.getId()).get(5, TimeUnit.SECONDS);
                metricsService.incrementMessagesSent();

                // 4. StatusNotification (Preparing)
                ocppService.sendStatusNotification(created.getId(), ConnectorStatus.PREPARING).get(5, TimeUnit.SECONDS);
                metricsService.incrementMessagesSent();

                // 5. StartTransaction
                ocppService.sendStartTransaction(created.getId()).get(10, TimeUnit.SECONDS);
                metricsService.incrementMessagesSent();

                // 6. StatusNotification (Charging)
                ocppService.sendStatusNotification(created.getId(), ConnectorStatus.CHARGING).get(5, TimeUnit.SECONDS);
                metricsService.incrementMessagesSent();

                long latencyMs = (System.nanoTime() - start) / 1_000_000;
                metricsService.recordLatency(latencyMs);

                if (index <= 3 || index % 100 == 0) {
                    log.info("Session {} charging started successfully", sessionId);
                }
            } else {
                throw new RuntimeException("Connection failed");
            }
        } catch (Exception e) {
            // Cleanup en cas d'échec
            try {
                ocppService.disconnect(created.getId());
                sessionService.deleteSession(created.getId());
                createdSessionIds.remove(created.getId());
            } catch (Exception ignored) {}
            throw new RuntimeException("Session creation failed: " + e.getMessage(), e);
        }
    }

    private SessionStats toSessionStats(Session session) {
        return SessionStats.builder()
                .sessionId(session.getId())
                .cpId(session.getCpId())
                .state(session.getState().name())
                .connected(session.isConnected())
                .charging(session.isCharging())
                .soc(session.getSoc())
                .powerKw(session.getCurrentPowerKw())
                .energyKwh(session.getEnergyDeliveredKwh())
                .messageCount(session.getOcppMessages() != null ? session.getOcppMessages().size() : 0)
                .build();
    }
}
