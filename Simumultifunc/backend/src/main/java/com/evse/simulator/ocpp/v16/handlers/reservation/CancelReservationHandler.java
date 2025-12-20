package com.evse.simulator.ocpp.v16.handlers.reservation;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.CancelReservationStatus;
import com.evse.simulator.service.SessionStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler pour CancelReservation (CS → CP).
 */
@Slf4j
@Component
public class CancelReservationHandler extends AbstractOcpp16IncomingHandler {

    private final SessionStateManager stateManager;

    public CancelReservationHandler(SessionStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public OCPPAction getAction() {
        return OCPPAction.CANCEL_RESERVATION;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "reservationId");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        Integer reservationId = getInteger(payload, "reservationId", true);

        CancelReservationStatus status;

        // Vérifier si le connecteur est réservé avec le bon reservationId
        if (session.getState() == SessionState.RESERVED) {
            // Vérifier que le reservationId correspond
            Integer currentReservationId = session.getReservationId();
            if (currentReservationId != null && !currentReservationId.equals(reservationId)) {
                log.warn("[{}] CancelReservation: reservationId mismatch (expected={}, received={})",
                        session.getId(), currentReservationId, reservationId);
                status = CancelReservationStatus.REJECTED;
                logToSession(session, String.format(
                        "CancelReservation REJECTED - reservationId mismatch (expected=%d, received=%d)",
                        currentReservationId, reservationId));
            } else {
                // Nettoyer les infos de réservation
                session.setReservationId(null);
                session.setReservationExpiry(null);
                // Note: on garde l'idTag car il pourrait être utilisé ensuite

                // Annuler la réservation
                stateManager.forceTransition(session, SessionState.AVAILABLE, "CancelReservation from CSMS");
                status = CancelReservationStatus.ACCEPTED;
                logToSession(session, String.format(
                        "CancelReservation ACCEPTED - reservationId=%d", reservationId));
            }
        } else {
            // Pas de réservation active
            status = CancelReservationStatus.REJECTED;
            logToSession(session, String.format(
                    "CancelReservation REJECTED - no active reservation with id=%d", reservationId));
        }

        log.info("[{}] CancelReservation: reservationId={}, status={}",
                session.getId(), reservationId, status);

        Map<String, Object> response = createResponse(status);
        logExit(session, response);
        return response;
    }
}
