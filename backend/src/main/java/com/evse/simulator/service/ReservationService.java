package com.evse.simulator.service;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.SessionState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Service de gestion des réservations OCPP 1.6.
 *
 * Gère:
 * - La planification de l'expiration des réservations
 * - L'annulation des réservations
 * - L'envoi des StatusNotification liés aux réservations
 */
@Service
@Slf4j
public class ReservationService {

    private final SessionService sessionService;
    private final com.evse.simulator.domain.service.OCPPService ocppService;

    // Map des tâches d'expiration planifiées: sessionId -> ScheduledFuture
    private final Map<String, ScheduledFuture<?>> expiryTasks = new ConcurrentHashMap<>();

    // Scheduler pour les expirations
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
            r -> {
                Thread t = new Thread(r, "reservation-expiry");
                t.setDaemon(true);
                return t;
            });

    public ReservationService(SessionService sessionService,
                               @Lazy com.evse.simulator.domain.service.OCPPService ocppService) {
        this.sessionService = sessionService;
        this.ocppService = ocppService;
    }

    /**
     * Planifie l'expiration automatique d'une réservation.
     *
     * @param sessionId     ID de la session
     * @param reservationId ID de la réservation
     * @param expiryDate    Date/heure d'expiration
     */
    public void scheduleReservationExpiry(String sessionId, int reservationId, LocalDateTime expiryDate) {
        // Annuler toute tâche existante pour cette session
        cancelExpiryTask(sessionId);

        // Calculer le délai jusqu'à l'expiration
        long delayMillis = Duration.between(LocalDateTime.now(), expiryDate).toMillis();

        if (delayMillis <= 0) {
            // Déjà expiré, traiter immédiatement
            log.warn("[Reservation] Reservation {} for session {} already expired, processing now",
                    reservationId, sessionId);
            handleReservationExpiry(sessionId, reservationId);
            return;
        }

        log.info("[Reservation] Scheduling expiry for reservation {} in {} seconds",
                reservationId, delayMillis / 1000);

        // Planifier l'expiration
        ScheduledFuture<?> task = scheduler.schedule(
                () -> handleReservationExpiry(sessionId, reservationId),
                delayMillis,
                TimeUnit.MILLISECONDS
        );

        expiryTasks.put(sessionId, task);
    }

    /**
     * Annule une réservation et sa tâche d'expiration.
     *
     * @param sessionId     ID de la session
     * @param reservationId ID de la réservation
     */
    public void cancelReservation(String sessionId, int reservationId) {
        log.info("[Reservation] Cancelling reservation {} for session {}", reservationId, sessionId);
        cancelExpiryTask(sessionId);
    }

    /**
     * Gère l'expiration d'une réservation.
     */
    private void handleReservationExpiry(String sessionId, int reservationId) {
        log.info("[Reservation] Handling expiry for reservation {} on session {}",
                reservationId, sessionId);

        try {
            Session session = sessionService.findSession(sessionId).orElse(null);

            if (session == null) {
                log.warn("[Reservation] Session {} not found, cannot process expiry", sessionId);
                return;
            }

            // Vérifier que c'est bien la même réservation et que la session est en état Reserved
            if (session.getReservationId() == null ||
                !session.getReservationId().equals(reservationId)) {
                log.info("[Reservation] Reservation {} no longer active on session {}",
                        reservationId, sessionId);
                return;
            }

            if (session.getState() != SessionState.RESERVED) {
                log.info("[Reservation] Session {} not in RESERVED state ({}), skipping expiry",
                        sessionId, session.getState());
                return;
            }

            // La réservation expire
            log.info("[Reservation] Reservation {} expired for session {}", reservationId, sessionId);

            // Nettoyer les données de réservation
            session.setReservationId(null);
            session.setReservationExpiry(null);

            // Remettre en état Available
            session.setState(SessionState.AVAILABLE);
            session.setLastStateChange(LocalDateTime.now());

            // Logger l'expiration
            session.addLog(LogEntry.warn("Reservation",
                    "Réservation #" + reservationId + " expirée"));

            // Envoyer StatusNotification Available
            sendStatusNotification(sessionId, ConnectorStatus.AVAILABLE);

            // Notifier via broadcast si disponible
            sessionService.broadcastSessionUpdate(session);

        } catch (Exception e) {
            log.error("[Reservation] Error handling expiry for reservation {}", reservationId, e);
        } finally {
            // Retirer la tâche de la map
            expiryTasks.remove(sessionId);
        }
    }

    /**
     * Annule la tâche d'expiration pour une session.
     */
    private void cancelExpiryTask(String sessionId) {
        ScheduledFuture<?> task = expiryTasks.remove(sessionId);
        if (task != null && !task.isDone()) {
            task.cancel(false);
            log.debug("[Reservation] Cancelled expiry task for session {}", sessionId);
        }
    }

    /**
     * Envoie un StatusNotification via le service OCPP.
     *
     * @param sessionId ID de la session
     * @param status    Statut du connecteur
     */
    public void sendStatusNotification(String sessionId, ConnectorStatus status) {
        try {
            if (ocppService != null) {
                ocppService.sendStatusNotification(sessionId, status);
                log.debug("[Reservation] Sent StatusNotification {} for session {}",
                        status, sessionId);
            }
        } catch (Exception e) {
            log.error("[Reservation] Failed to send StatusNotification for session {}", sessionId, e);
        }
    }

    /**
     * Vérifie si une session a une réservation active.
     */
    public boolean hasActiveReservation(String sessionId) {
        return sessionService.findSession(sessionId)
                .map(s -> s.getReservationId() != null && s.getState() == SessionState.RESERVED)
                .orElse(false);
    }

    /**
     * Vérifie si un idTag correspond à la réservation de la session.
     */
    public boolean isReservationForIdTag(String sessionId, String idTag) {
        return sessionService.findSession(sessionId)
                .filter(s -> s.getState() == SessionState.RESERVED)
                .filter(s -> s.getIdTag() != null)
                .map(s -> s.getIdTag().equalsIgnoreCase(idTag))
                .orElse(false);
    }

    /**
     * Consomme une réservation (quand le bon utilisateur se branche).
     * La réservation est terminée et la charge peut commencer.
     */
    public void consumeReservation(String sessionId, String idTag) {
        sessionService.findSession(sessionId).ifPresent(session -> {
            if (session.getState() == SessionState.RESERVED &&
                session.getIdTag() != null &&
                session.getIdTag().equalsIgnoreCase(idTag)) {

                log.info("[Reservation] Consuming reservation {} for idTag {} on session {}",
                        session.getReservationId(), idTag, sessionId);

                // Annuler la tâche d'expiration
                cancelExpiryTask(sessionId);

                // La réservation est consommée, nettoyer
                Integer reservationId = session.getReservationId();
                session.setReservationId(null);
                session.setReservationExpiry(null);

                session.addLog(LogEntry.success("Reservation",
                        "Réservation #" + reservationId + " utilisée par " + idTag));
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        log.info("[Reservation] Shutting down reservation service");
        scheduler.shutdownNow();
    }
}
