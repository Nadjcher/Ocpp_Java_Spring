package com.evse.simulator.service;

import com.evse.simulator.config.LoadTestProperties;
import com.evse.simulator.domain.service.LoadTestService;
import com.evse.simulator.domain.service.MetricsService;
import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Implémentation du service de tests de charge.
 * <p>
 * Gère la création massive de sessions pour tester les performances.
 * </p>
 */
@Service
@Slf4j
public class LoadTestServiceImpl implements LoadTestService {

    private final SessionService sessionService;
    private final OCPPService ocppService;
    private final MetricsService metricsService;
    private final LoadTestProperties loadTestProperties;

    // État du test de charge
    private volatile boolean loadTestRunning = false;
    private volatile int loadTestTarget = 0;
    private volatile long loadTestStartTime = 0;
    private ScheduledFuture<?> loadTestTask;

    private final ScheduledExecutorService scheduler;

    public LoadTestServiceImpl(SessionService sessionService,
                               OCPPService ocppService,
                               MetricsService metricsService,
                               LoadTestProperties loadTestProperties) {
        this.sessionService = sessionService;
        this.ocppService = ocppService;
        this.metricsService = metricsService;
        this.loadTestProperties = loadTestProperties;
        this.scheduler = Executors.newScheduledThreadPool(loadTestProperties.getThreadPoolSize());
    }

    @Override
    public String startLoadTest(int targetSessions, int rampUpSeconds, Session sessionTemplate) {
        if (loadTestRunning) {
            throw new IllegalStateException("Un test de charge est déjà en cours");
        }

        if (targetSessions > loadTestProperties.getMaxSessions()) {
            throw new IllegalArgumentException(
                    String.format("Le nombre de sessions cibles (%d) dépasse le maximum autorisé (%d)",
                            targetSessions, loadTestProperties.getMaxSessions()));
        }

        if (targetSessions <= 0) {
            throw new IllegalArgumentException("Le nombre de sessions doit être positif");
        }

        loadTestRunning = true;
        loadTestTarget = targetSessions;
        loadTestStartTime = System.currentTimeMillis();

        // Reset des compteurs de métriques
        metricsService.resetCounters();

        String testId = UUID.randomUUID().toString();
        log.info("Démarrage du test de charge {}: {} sessions, {} secondes de montée en charge",
                testId, targetSessions, rampUpSeconds);

        // Calculer le délai entre chaque création de session
        int delayMs = rampUpSeconds > 0 ?
                (rampUpSeconds * 1000) / targetSessions : 10;

        loadTestTask = scheduler.scheduleAtFixedRate(() -> {
            if (!loadTestRunning) {
                return;
            }

            int currentSessions = (int) sessionService.countSessions();
            if (currentSessions >= targetSessions) {
                log.info("Test de charge: cible atteinte - {} sessions", targetSessions);
                return;
            }

            try {
                createAndConnectSession(sessionTemplate, currentSessions + 1);
            } catch (Exception e) {
                log.error("Erreur lors de la création de session dans le test de charge: {}",
                        e.getMessage());
                metricsService.incrementErrors();
            }

        }, 0, delayMs, TimeUnit.MILLISECONDS);

        return testId;
    }

    @Override
    public void stopLoadTest() {
        if (!loadTestRunning) {
            log.warn("Aucun test de charge en cours");
            return;
        }

        loadTestRunning = false;
        if (loadTestTask != null) {
            loadTestTask.cancel(false);
            loadTestTask = null;
        }

        long duration = System.currentTimeMillis() - loadTestStartTime;
        log.info("Test de charge arrêté après {}ms. Sessions: {}, Messages envoyés: {}, Erreurs: {}",
                duration,
                sessionService.countSessions(),
                metricsService.getMessagesSent(),
                metricsService.getErrorsCount());
    }

    @Override
    public boolean isLoadTestRunning() {
        return loadTestRunning;
    }

    @Override
    public LoadTestStatus getLoadTestStatus() {
        if (!loadTestRunning && loadTestStartTime == 0) {
            return null;
        }

        int currentSessions = (int) sessionService.countSessions();
        int connectedSessions = sessionService.getConnectedSessions().size();
        double progress = loadTestTarget > 0 ?
                (currentSessions * 100.0 / loadTestTarget) : 0;

        return LoadTestStatus.builder()
                .running(loadTestRunning)
                .targetSessions(loadTestTarget)
                .currentSessions(currentSessions)
                .connectedSessions(connectedSessions)
                .startTime(loadTestStartTime)
                .durationMs(System.currentTimeMillis() - loadTestStartTime)
                .messagesSent(metricsService.getMessagesSent())
                .messagesReceived(metricsService.getMessagesReceived())
                .errors(metricsService.getErrorsCount())
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

    private void createAndConnectSession(Session template, int index) {
        Session session = Session.builder()
                .title(template.getTitle() + " #" + index)
                .url(template.getUrl())
                .cpId(template.getCpId() + "_" + index)
                .bearerToken(template.getBearerToken())
                .vehicleProfile(template.getVehicleProfile())
                .chargerType(template.getChargerType())
                .idTag(template.getIdTag())
                .soc(template.getSoc())
                .targetSoc(template.getTargetSoc())
                .build();

        Session created = sessionService.createSession(session);

        // Connecter automatiquement
        ocppService.connect(created.getId())
                .thenCompose(connected -> {
                    if (connected) {
                        return ocppService.sendBootNotification(created.getId());
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .exceptionally(ex -> {
                    log.warn("Échec de connexion pour la session du test de charge: {}",
                            ex.getMessage());
                    metricsService.incrementErrors();
                    return null;
                });
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
                .messageCount(session.getOcppMessages().size())
                .build();
    }
}
