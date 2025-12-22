package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.service.ReservationService;
import com.evse.simulator.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler pour le message ReserveNow (OCPP 1.6).
 *
 * Le CSMS envoie ce message pour réserver un connecteur pour un utilisateur spécifique.
 *
 * Statuts de réponse possibles:
 * - Accepted: Réservation acceptée
 * - Faulted: Le connecteur est en erreur
 * - Occupied: Le connecteur est déjà occupé (charge en cours ou autre réservation)
 * - Rejected: Réservation refusée (ex: idTag invalide)
 * - Unavailable: Le connecteur est indisponible
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReserveNowHandler implements Ocpp16IncomingHandler {

    private final SessionService sessionService;
    private final ReservationService reservationService;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    };

    @Override
    public String getAction() {
        return "ReserveNow";
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Extraire les paramètres obligatoires
            Integer connectorId = getInteger(payload, "connectorId");
            String expiryDateStr = (String) payload.get("expiryDate");
            String idTag = (String) payload.get("idTag");
            Integer reservationId = getInteger(payload, "reservationId");

            // Paramètre optionnel
            String parentIdTag = (String) payload.get("parentIdTag");

            log.info("[ReserveNow] Session={}, connectorId={}, reservationId={}, idTag={}, expiryDate={}",
                    session.getId(), connectorId, reservationId, idTag, expiryDateStr);

            // Validation des paramètres
            if (connectorId == null || expiryDateStr == null || idTag == null || reservationId == null) {
                log.warn("[ReserveNow] Rejected: missing required parameters");
                session.addLog(LogEntry.warn("ReserveNow rejected: missing parameters"));
                response.put("status", "Rejected");
                return response;
            }

            // Parser la date d'expiration
            LocalDateTime expiryDate = parseDateTime(expiryDateStr);
            if (expiryDate == null) {
                log.warn("[ReserveNow] Rejected: invalid expiryDate format: {}", expiryDateStr);
                session.addLog(LogEntry.warn("ReserveNow rejected: invalid expiryDate"));
                response.put("status", "Rejected");
                return response;
            }

            // Vérifier si la réservation est déjà expirée
            if (expiryDate.isBefore(LocalDateTime.now())) {
                log.warn("[ReserveNow] Rejected: expiryDate already passed: {}", expiryDate);
                session.addLog(LogEntry.warn("ReserveNow rejected: expiryDate already passed"));
                response.put("status", "Rejected");
                return response;
            }

            // Vérifier le connecteur (0 = tous les connecteurs, sinon connecteur spécifique)
            if (connectorId != 0 && connectorId != session.getConnectorId()) {
                log.warn("[ReserveNow] Rejected: invalid connectorId {}", connectorId);
                session.addLog(LogEntry.warn("ReserveNow rejected: invalid connectorId " + connectorId));
                response.put("status", "Rejected");
                return response;
            }

            // Déterminer le statut de réponse basé sur l'état actuel
            String status = determineReservationStatus(session, connectorId, reservationId, idTag);

            if ("Accepted".equals(status)) {
                // Appliquer la réservation
                session.setReservationId(reservationId);
                session.setReservationExpiry(expiryDate);
                session.setIdTag(idTag); // Le badge associé à la réservation

                // Passer en état RESERVED
                SessionState previousState = session.getState();
                session.setState(SessionState.RESERVED);
                session.setLastStateChange(LocalDateTime.now());

                // Planifier l'expiration de la réservation
                reservationService.scheduleReservationExpiry(session.getId(), reservationId, expiryDate);

                // Envoyer StatusNotification avec le statut Reserved
                reservationService.sendStatusNotification(session.getId(), ConnectorStatus.RESERVED);

                long durationMinutes = java.time.Duration.between(LocalDateTime.now(), expiryDate).toMinutes();
                log.info("[ReserveNow] Accepted: session={}, reservationId={}, idTag={}, duration={}min",
                        session.getId(), reservationId, idTag, durationMinutes);

                session.addLog(LogEntry.success("ReserveNow",
                        String.format("Réservation #%d acceptée pour %s (expire dans %d min)",
                                reservationId, idTag, durationMinutes)));
            } else {
                log.info("[ReserveNow] {}: session={}, reason=state is {}",
                        status, session.getId(), session.getState());
                session.addLog(LogEntry.warn("ReserveNow " + status + ": état actuel " + session.getState()));
            }

            response.put("status", status);

        } catch (Exception e) {
            log.error("[ReserveNow] Error processing request", e);
            session.addLog(LogEntry.error("ReserveNow error: " + e.getMessage()));
            response.put("status", "Rejected");
        }

        return response;
    }

    /**
     * Détermine le statut de réponse à la réservation selon l'état du connecteur.
     */
    private String determineReservationStatus(Session session, int connectorId, int reservationId, String idTag) {
        SessionState state = session.getState();

        // Vérifier si le connecteur est en erreur
        if (state == SessionState.FAULTED) {
            return "Faulted";
        }

        // Vérifier si le connecteur est indisponible
        if (state == SessionState.UNAVAILABLE) {
            return "Unavailable";
        }

        // Vérifier si une charge est en cours
        if (state.hasActiveTransaction() || state == SessionState.CHARGING) {
            return "Occupied";
        }

        // Vérifier si une autre réservation existe déjà
        if (state == SessionState.RESERVED) {
            // Si c'est la même réservation, accepter (mise à jour)
            if (session.getReservationId() != null && session.getReservationId().equals(reservationId)) {
                return "Accepted";
            }
            // Sinon, occupé par une autre réservation
            return "Occupied";
        }

        // Vérifier les états transitoires (préparation, autorisation en cours...)
        if (state == SessionState.PREPARING || state == SessionState.AUTHORIZING ||
            state == SessionState.STARTING || state == SessionState.PLUGGED) {
            return "Occupied";
        }

        // États valides pour la réservation: AVAILABLE, BOOT_ACCEPTED, PARKED, FINISHING
        if (state == SessionState.AVAILABLE || state == SessionState.BOOT_ACCEPTED ||
            state == SessionState.PARKED || state == SessionState.FINISHING ||
            state == SessionState.CONNECTED) {
            return "Accepted";
        }

        // Par défaut, rejeter
        return "Rejected";
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return null;
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null) return null;

        // Nettoyer la chaîne (enlever le 'Z' final si présent et parser en UTC)
        String cleanDate = dateStr.replace("Z", "");

        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Essayer le format suivant
            }
        }

        // Dernier essai avec la chaîne nettoyée
        try {
            return LocalDateTime.parse(cleanDate);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
