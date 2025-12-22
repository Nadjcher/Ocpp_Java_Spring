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
import java.util.HashMap;
import java.util.Map;

/**
 * Handler pour le message CancelReservation (OCPP 1.6).
 *
 * Le CSMS envoie ce message pour annuler une réservation existante.
 *
 * Statuts de réponse possibles:
 * - Accepted: Réservation annulée avec succès
 * - Rejected: Aucune réservation avec cet ID n'existe
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CancelReservationHandler implements Ocpp16IncomingHandler {

    private final SessionService sessionService;
    private final ReservationService reservationService;

    @Override
    public String getAction() {
        return "CancelReservation";
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Extraire le paramètre obligatoire
            Integer reservationId = getInteger(payload, "reservationId");

            log.info("[CancelReservation] Session={}, reservationId={}",
                    session.getId(), reservationId);

            if (reservationId == null) {
                log.warn("[CancelReservation] Rejected: missing reservationId");
                session.addLog(LogEntry.warn("CancelReservation rejected: missing reservationId"));
                response.put("status", "Rejected");
                return response;
            }

            // Vérifier si cette session a bien cette réservation
            if (session.getReservationId() == null ||
                !session.getReservationId().equals(reservationId)) {

                log.warn("[CancelReservation] Rejected: no matching reservation. " +
                         "Expected={}, Requested={}",
                         session.getReservationId(), reservationId);

                session.addLog(LogEntry.warn("CancelReservation rejected: réservation #" +
                        reservationId + " introuvable"));
                response.put("status", "Rejected");
                return response;
            }

            // Vérifier que la session est bien en état Reserved
            if (session.getState() != SessionState.RESERVED) {
                log.warn("[CancelReservation] Rejected: session not in RESERVED state. Current={}",
                        session.getState());

                // Si la réservation a été utilisée (véhicule branché), on ne peut pas l'annuler
                if (session.getState().hasActiveTransaction()) {
                    session.addLog(LogEntry.warn("CancelReservation rejected: charge en cours"));
                    response.put("status", "Rejected");
                    return response;
                }
            }

            // Annuler la réservation
            reservationService.cancelReservation(session.getId(), reservationId);

            // Nettoyer les données de réservation
            session.setReservationId(null);
            session.setReservationExpiry(null);

            // Remettre en état Available (ou BOOT_ACCEPTED si c'était l'état précédent)
            session.setState(SessionState.AVAILABLE);
            session.setLastStateChange(LocalDateTime.now());

            // Envoyer StatusNotification avec le statut Available
            reservationService.sendStatusNotification(session.getId(), ConnectorStatus.AVAILABLE);

            log.info("[CancelReservation] Accepted: session={}, reservationId={}",
                    session.getId(), reservationId);

            session.addLog(LogEntry.success("CancelReservation",
                    "Réservation #" + reservationId + " annulée"));

            response.put("status", "Accepted");

        } catch (Exception e) {
            log.error("[CancelReservation] Error processing request", e);
            session.addLog(LogEntry.error("CancelReservation error: " + e.getMessage()));
            response.put("status", "Rejected");
        }

        return response;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return null;
    }
}
