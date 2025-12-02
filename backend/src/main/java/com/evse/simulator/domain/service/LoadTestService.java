package com.evse.simulator.domain.service;

import com.evse.simulator.model.Session;

import java.util.List;

/**
 * Service de gestion des tests de charge.
 */
public interface LoadTestService {

    /**
     * Démarre un test de charge.
     *
     * @param targetSessions nombre de sessions cibles
     * @param rampUpSeconds temps de montée en charge en secondes
     * @param sessionTemplate modèle de session à utiliser
     * @return ID unique du test
     */
    String startLoadTest(int targetSessions, int rampUpSeconds, Session sessionTemplate);

    /**
     * Démarre un test de charge avec configuration map (compatibilité frontend).
     *
     * @param config configuration du test
     * @return ID unique du test
     */
    default String startLoadTest(java.util.Map<String, Object> config) {
        int sessions = config.containsKey("sessions")
            ? ((Number) config.get("sessions")).intValue() : 10;
        int rampUp = config.containsKey("rampUp")
            ? ((Number) config.get("rampUp")).intValue() : 10;
        return startLoadTest(sessions, rampUp, null);
    }

    /**
     * Arrête le test de charge en cours.
     */
    void stopLoadTest();

    /**
     * Vérifie si un test de charge est en cours.
     *
     * @return true si un test est en cours
     */
    boolean isLoadTestRunning();

    /**
     * Alias pour isLoadTestRunning() (compatibilité frontend).
     */
    default boolean isRunning() {
        return isLoadTestRunning();
    }

    /**
     * Retourne l'ID du test en cours.
     *
     * @return ID du test ou null
     */
    default String getCurrentRunId() {
        LoadTestStatus status = getLoadTestStatus();
        return status != null && status.isRunning() ? "run-" + status.getStartTime() : null;
    }

    /**
     * Récupère l'état du test de charge.
     *
     * @return état du test ou null si pas de test
     */
    LoadTestStatus getLoadTestStatus();

    /**
     * Récupère les statistiques par session.
     *
     * @return liste des statistiques
     */
    List<SessionStats> getSessionStats();

    /**
     * État d'un test de charge.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class LoadTestStatus {
        private boolean running;
        private int targetSessions;
        private int currentSessions;
        private int connectedSessions;
        private long startTime;
        private long durationMs;
        private long messagesSent;
        private long messagesReceived;
        private long errors;
        private double progress;
    }

    /**
     * Statistiques d'une session.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class SessionStats {
        private String sessionId;
        private String cpId;
        private String state;
        private boolean connected;
        private boolean charging;
        private double soc;
        private double powerKw;
        private double energyKwh;
        private int messageCount;
    }
}
