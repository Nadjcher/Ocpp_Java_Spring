package com.evse.simulator.service;

import com.evse.simulator.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * Service de diffusion WebSocket via STOMP.
 * <p>
 * Gère la diffusion temps réel des mises à jour vers les clients connectés.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketBroadcaster implements com.evse.simulator.domain.service.BroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    @Qualifier("websocketExecutor")
    private final Executor websocketExecutor;

    // =========================================================================
    // Destinations STOMP
    // =========================================================================

    private static final String TOPIC_SESSION = "/topic/sessions/";
    private static final String TOPIC_SESSION_LOGS = "/topic/sessions/%s/logs";
    private static final String TOPIC_SESSION_CHART = "/topic/sessions/%s/chart";
    private static final String TOPIC_SESSION_OCPP = "/topic/sessions/%s/ocpp";
    private static final String TOPIC_METRICS = "/topic/metrics";
    private static final String TOPIC_PERFORMANCE = "/topic/performance";
    private static final String TOPIC_ALL_SESSIONS = "/topic/sessions";
    private static final String TOPIC_ML_ANOMALY = "/topic/ml/anomalies";

    // =========================================================================
    // Diffusion des sessions
    // =========================================================================

    /**
     * Diffuse une mise à jour de session.
     *
     * @param session la session mise à jour
     */
    @Async("websocketExecutor")
    public void broadcastSession(Session session) {
        try {
            messagingTemplate.convertAndSend(TOPIC_SESSION + session.getId(), session);
            log.trace("Broadcast session update: {}", session.getId());
        } catch (Exception e) {
            log.error("Failed to broadcast session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Diffuse une liste de toutes les sessions.
     *
     * @param sessions liste des sessions
     */
    @Async("websocketExecutor")
    public void broadcastAllSessions(java.util.List<Session> sessions) {
        try {
            messagingTemplate.convertAndSend(TOPIC_ALL_SESSIONS, sessions);
            log.trace("Broadcast all sessions: {} sessions", sessions.size());
        } catch (Exception e) {
            log.error("Failed to broadcast all sessions: {}", e.getMessage());
        }
    }

    /**
     * Diffuse un log pour une session.
     *
     * @param sessionId ID de la session
     * @param log entrée de log
     */
    @Async("websocketExecutor")
    public void broadcastLog(String sessionId, LogEntry log) {
        try {
            String destination = String.format(TOPIC_SESSION_LOGS, sessionId);
            messagingTemplate.convertAndSend(destination, log);
        } catch (Exception e) {
            WebSocketBroadcaster.log.error("Failed to broadcast log for session {}: {}",
                    sessionId, e.getMessage());
        }
    }

    /**
     * Diffuse des données de graphique pour une session.
     *
     * @param sessionId ID de la session
     * @param chartData données du graphique
     */
    @Async("websocketExecutor")
    public void broadcastChartData(String sessionId, ChartData chartData) {
        try {
            String destination = String.format(TOPIC_SESSION_CHART, sessionId);
            messagingTemplate.convertAndSend(destination, chartData);
        } catch (Exception e) {
            log.error("Failed to broadcast chart data for session {}: {}",
                    sessionId, e.getMessage());
        }
    }

    /**
     * Diffuse un message OCPP pour une session.
     *
     * @param sessionId ID de la session
     * @param message message OCPP
     */
    @Async("websocketExecutor")
    public void broadcastOcppMessage(String sessionId, OCPPMessage message) {
        try {
            String destination = String.format(TOPIC_SESSION_OCPP, sessionId);
            messagingTemplate.convertAndSend(destination, message);
        } catch (Exception e) {
            log.error("Failed to broadcast OCPP message for session {}: {}",
                    sessionId, e.getMessage());
        }
    }

    // =========================================================================
    // Diffusion des métriques
    // =========================================================================

    /**
     * Diffuse les métriques globales.
     *
     * @param metrics métriques de performance
     */
    @Async("websocketExecutor")
    public void broadcastMetrics(PerformanceMetrics metrics) {
        try {
            messagingTemplate.convertAndSend(TOPIC_METRICS, metrics);
            log.trace("Broadcast metrics");
        } catch (Exception e) {
            log.error("Failed to broadcast metrics: {}", e.getMessage());
        }
    }

    /**
     * Diffuse les statistiques de performance.
     *
     * @param stats statistiques
     */
    @Async("websocketExecutor")
    public void broadcastPerformanceStats(Object stats) {
        try {
            messagingTemplate.convertAndSend(TOPIC_PERFORMANCE, stats);
        } catch (Exception e) {
            log.error("Failed to broadcast performance stats: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Diffusion ML
    // =========================================================================

    /**
     * Diffuse une anomalie ML détectée.
     *
     * @param anomaly données de l'anomalie
     */
    @Async("websocketExecutor")
    public void broadcastMLAnomaly(Object anomaly) {
        try {
            messagingTemplate.convertAndSend(TOPIC_ML_ANOMALY, anomaly);
            log.debug("Broadcast ML anomaly");
        } catch (Exception e) {
            log.error("Failed to broadcast ML anomaly: {}", e.getMessage());
        }
    }

    /**
     * Diffuse plusieurs anomalies ML.
     *
     * @param anomalies liste des anomalies
     */
    @Async("websocketExecutor")
    public void broadcastMLAnomalies(java.util.List<?> anomalies) {
        anomalies.forEach(this::broadcastMLAnomaly);
    }

    // =========================================================================
    // Diffusion vers un utilisateur spécifique
    // =========================================================================

    /**
     * Envoie un message à un utilisateur spécifique.
     *
     * @param userId ID de l'utilisateur
     * @param destination destination relative
     * @param payload payload du message
     */
    public void sendToUser(String userId, String destination, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId, destination, payload);
        } catch (Exception e) {
            log.error("Failed to send message to user {}: {}", userId, e.getMessage());
        }
    }

}