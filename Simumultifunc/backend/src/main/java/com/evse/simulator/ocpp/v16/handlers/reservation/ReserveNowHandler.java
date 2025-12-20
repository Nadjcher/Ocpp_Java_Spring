package com.evse.simulator.ocpp.v16.handlers.reservation;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.ReservationStatus;
import com.evse.simulator.service.SessionStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Handler pour ReserveNow (CS → CP).
 * <p>
 * Traite les demandes de réservation envoyées par le CSMS.
 * </p>
 *
 * <h3>Flux OCPP 1.6:</h3>
 * <pre>
 * 1. CSMS envoie ReserveNow.req avec connectorId, expiryDate, idTag, reservationId
 * 2. CP répond immédiatement Accepted/Rejected/Occupied/Faulted/Unavailable
 * 3. Si Accepted, CP envoie StatusNotification(Reserved)
 * </pre>
 */
@Slf4j
@Component
public class ReserveNowHandler extends AbstractOcpp16IncomingHandler {

    private final OCPPService ocppService;
    private final SessionStateManager stateManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> reservationTimers = new ConcurrentHashMap<>();

    public ReserveNowHandler(@Lazy OCPPService ocppService, SessionStateManager stateManager) {
        this.ocppService = ocppService;
        this.stateManager = stateManager;
    }

    @Override
    public OCPPAction getAction() {
        return OCPPAction.RESERVE_NOW;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "connectorId");
        requireField(payload, "expiryDate");
        requireField(payload, "idTag");
        requireField(payload, "reservationId");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        Integer connectorId = getInteger(payload, "connectorId", true);
        String expiryDate = getString(payload, "expiryDate", true);
        String idTag = getString(payload, "idTag", true);
        Integer reservationId = getInteger(payload, "reservationId", true);
        String parentIdTag = getString(payload, "parentIdTag", false);

        ReservationStatus status = validateReservation(session, connectorId);

        if (status == ReservationStatus.ACCEPTED) {
            // Parser la date d'expiration
            LocalDateTime expiry = parseExpiryDate(expiryDate);

            // Stocker les infos de réservation
            session.setReservationId(reservationId);
            session.setIdTag(idTag);
            session.setReservationExpiry(expiry);

            // Passer le connecteur en Reserved
            stateManager.forceTransition(session, SessionState.RESERVED, "ReserveNow from CSMS");

            session.addLog(LogEntry.success("OCPP", String.format(
                    "ReserveNow ACCEPTED - reservationId=%d, idTag=%s, expires=%s (local: %s)",
                    reservationId, idTag, expiryDate, expiry)));

            // Envoyer StatusNotification(Reserved) de manière asynchrone
            sendReservedStatusNotification(session, connectorId);

            // Planifier l'expiration de la réservation
            scheduleReservationExpiry(session, expiry, reservationId);
        } else {
            session.addLog(LogEntry.warn("OCPP", String.format(
                    "ReserveNow %s - connector not available (state=%s)",
                    status.getValue(), session.getState())));
        }

        log.info("[{}] ReserveNow: reservationId={}, connectorId={}, idTag={}, status={}",
                session.getId(), reservationId, connectorId, idTag, status);

        Map<String, Object> response = createResponse(status);
        logExit(session, response);
        return response;
    }

    /**
     * Envoie StatusNotification(Reserved) après acceptation de la réservation.
     */
    private void sendReservedStatusNotification(Session session, Integer connectorId) {
        CompletableFuture.runAsync(() -> {
            try {
                // Petit délai pour permettre l'envoi de la réponse ReserveNow
                Thread.sleep(200);

                log.info("[{}] ReserveNow: Sending StatusNotification(Reserved)", session.getId());
                ocppService.sendStatusNotification(session.getId(), ConnectorStatus.RESERVED);

                session.addLog(LogEntry.info("OCPP", "StatusNotification(Reserved) sent"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[{}] ReserveNow: StatusNotification interrupted", session.getId());
            } catch (Exception e) {
                log.error("[{}] ReserveNow: Failed to send StatusNotification: {}",
                        session.getId(), e.getMessage());
            }
        });
    }

    private ReservationStatus validateReservation(Session session, Integer connectorId) {
        SessionState state = session.getState();

        // Vérifier si le connecteur est disponible pour réservation
        // Accepter les états: AVAILABLE, BOOT_ACCEPTED, PARKED, PLUGGED
        if (state == SessionState.AVAILABLE ||
            state == SessionState.BOOT_ACCEPTED ||
            state == SessionState.PARKED ||
            state == SessionState.PLUGGED) {
            return ReservationStatus.ACCEPTED;
        }

        // Déjà réservé
        if (state == SessionState.RESERVED) {
            return ReservationStatus.OCCUPIED;
        }

        // Connecteur déjà occupé par une charge
        if (state == SessionState.CHARGING ||
            state == SessionState.PREPARING ||
            state == SessionState.STARTING ||
            state == SessionState.AUTHORIZING ||
            state == SessionState.AUTHORIZED) {
            return ReservationStatus.OCCUPIED;
        }

        // Connecteur indisponible
        if (state == SessionState.UNAVAILABLE) {
            return ReservationStatus.UNAVAILABLE;
        }

        // Connecteur en erreur
        if (state == SessionState.FAULTED) {
            return ReservationStatus.FAULTED;
        }

        // Par défaut, accepter si connecté
        if (session.isConnected()) {
            return ReservationStatus.ACCEPTED;
        }

        return ReservationStatus.REJECTED;
    }

    /**
     * Parse la date d'expiration au format ISO 8601 et convertit en heure locale.
     * OCPP envoie les dates en UTC, il faut les convertir en heure locale du système.
     */
    private LocalDateTime parseExpiryDate(String expiryDate) {
        try {
            ZonedDateTime zdt;

            // Format avec Z (UTC)
            if (expiryDate.endsWith("Z")) {
                zdt = ZonedDateTime.parse(expiryDate);
            }
            // Format avec offset timezone (+01:00, -05:00, etc.)
            // Note: on vérifie lastIndexOf("-") > 10 pour éviter de matcher les tirets de la date
            else if (expiryDate.contains("+") || expiryDate.lastIndexOf("-") > 10) {
                zdt = ZonedDateTime.parse(expiryDate);
            }
            // Pas de timezone, on assume que c'est déjà en heure locale
            else {
                LocalDateTime local = LocalDateTime.parse(expiryDate);
                log.debug("Parsed expiryDate without timezone: {} -> {}", expiryDate, local);
                return local;
            }

            // Convertir du fuseau d'origine vers le fuseau local du système
            LocalDateTime localExpiry = zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

            log.info("Parsed expiryDate: {} (UTC) -> {} (local {})",
                    expiryDate, localExpiry, ZoneId.systemDefault());

            return localExpiry;
        } catch (Exception e) {
            log.warn("Failed to parse expiryDate '{}': {}, using default 15 minutes",
                    expiryDate, e.getMessage());
            return LocalDateTime.now().plusMinutes(15);
        }
    }

    /**
     * Planifie l'expiration automatique de la réservation.
     */
    private void scheduleReservationExpiry(Session session, LocalDateTime expiry, Integer reservationId) {
        // Annuler tout timer précédent pour cette session
        ScheduledFuture<?> previousTimer = reservationTimers.remove(session.getId());
        if (previousTimer != null) {
            previousTimer.cancel(false);
        }

        // Calculer le délai jusqu'à l'expiration
        Duration delay = Duration.between(LocalDateTime.now(), expiry);
        long delayMillis = delay.toMillis();

        if (delayMillis <= 0) {
            log.warn("[{}] Reservation expiry is in the past, cancelling immediately", session.getId());
            expireReservation(session, reservationId);
            return;
        }

        log.info("[{}] Reservation #{} scheduled to expire in {} seconds",
                session.getId(), reservationId, delay.getSeconds());

        session.addLog(LogEntry.info("OCPP", String.format(
                "Reservation expires in %d minutes %d seconds",
                delay.toMinutes(), delay.getSeconds() % 60)));

        // Planifier l'expiration
        ScheduledFuture<?> future = scheduler.schedule(
                () -> expireReservation(session, reservationId),
                delayMillis,
                TimeUnit.MILLISECONDS
        );

        reservationTimers.put(session.getId(), future);
    }

    /**
     * Expire une réservation et remet le connecteur disponible.
     */
    private void expireReservation(Session session, Integer reservationId) {
        log.info("[{}] expireReservation called for reservation #{}, current state={}",
                session.getId(), reservationId, session.getState());

        // Vérifier que la réservation est toujours active
        if (session.getState() != SessionState.RESERVED) {
            log.warn("[{}] Reservation #{} already ended (state={}), skipping expiration",
                    session.getId(), reservationId, session.getState());
            return;
        }

        // Vérifier que c'est bien la même réservation
        if (session.getReservationId() == null || !session.getReservationId().equals(reservationId)) {
            log.debug("[{}] Reservation #{} was replaced by #{}",
                    session.getId(), reservationId, session.getReservationId());
            return;
        }

        log.info("[{}] Reservation #{} expired", session.getId(), reservationId);

        // Nettoyer les infos de réservation
        session.setReservationId(null);
        session.setReservationExpiry(null);

        // Remettre le connecteur disponible
        stateManager.forceTransition(session, SessionState.AVAILABLE, "Reservation expired");

        session.addLog(LogEntry.warn("OCPP", String.format(
                "Reservation #%d expired - connector now Available", reservationId)));

        // Envoyer StatusNotification(Available)
        try {
            ocppService.sendStatusNotification(session.getId(), ConnectorStatus.AVAILABLE);
            session.addLog(LogEntry.info("OCPP", "StatusNotification(Available) sent"));
        } catch (Exception e) {
            log.error("[{}] Failed to send StatusNotification after reservation expiry: {}",
                    session.getId(), e.getMessage());
        }

        // Retirer le timer
        reservationTimers.remove(session.getId());
    }

    /**
     * Annule manuellement une réservation (appelé par CancelReservation).
     */
    public void cancelReservation(Session session) {
        ScheduledFuture<?> timer = reservationTimers.remove(session.getId());
        if (timer != null) {
            timer.cancel(false);
        }

        if (session.getReservationId() != null) {
            log.info("[{}] Reservation #{} cancelled", session.getId(), session.getReservationId());
            session.setReservationId(null);
            session.setReservationExpiry(null);
        }
    }
}
