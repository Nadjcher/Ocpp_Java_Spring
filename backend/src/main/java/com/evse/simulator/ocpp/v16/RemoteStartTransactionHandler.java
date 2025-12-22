package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.service.ReservationService;
import com.evse.simulator.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler pour RemoteStartTransaction (OCPP 1.6).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RemoteStartTransactionHandler implements Ocpp16IncomingHandler {

    private final SessionService sessionService;
    private final ReservationService reservationService;
    private final com.evse.simulator.domain.service.OCPPService ocppService;

    @Override
    public String getAction() {
        return "RemoteStartTransaction";
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        try {
            String idTag = (String) payload.get("idTag");
            Integer connectorId = payload.get("connectorId") != null ?
                    ((Number) payload.get("connectorId")).intValue() : null;

            log.info("[RemoteStart] Session={}, idTag={}, connectorId={}",
                    session.getId(), idTag, connectorId);

            if (idTag == null || idTag.isBlank()) {
                response.put("status", "Rejected");
                session.addLog(LogEntry.warn("RemoteStartTransaction rejected: missing idTag"));
                return response;
            }

            // Vérifier si on peut démarrer
            SessionState state = session.getState();

            // Si réservé, vérifier que c'est le bon idTag
            if (state == SessionState.RESERVED) {
                if (!reservationService.isReservationForIdTag(session.getId(), idTag)) {
                    response.put("status", "Rejected");
                    session.addLog(LogEntry.warn("RemoteStartTransaction rejected: réservé pour un autre utilisateur"));
                    return response;
                }
                // Consommer la réservation
                reservationService.consumeReservation(session.getId(), idTag);
            }

            // Vérifier que le connecteur est disponible
            if (!canRemoteStart(state)) {
                response.put("status", "Rejected");
                session.addLog(LogEntry.warn("RemoteStartTransaction rejected: état " + state));
                return response;
            }

            // Accepter la demande - le démarrage réel se fait de manière asynchrone
            response.put("status", "Accepted");
            session.setIdTag(idTag);

            session.addLog(LogEntry.success("RemoteStartTransaction accepted pour " + idTag));

            // Déclencher le flux authorize -> startTransaction en asynchrone
            new Thread(() -> {
                try {
                    // Simuler le branchement si nécessaire
                    if (state == SessionState.AVAILABLE || state == SessionState.BOOT_ACCEPTED) {
                        session.setState(SessionState.PLUGGED);
                    }

                    // Authorize
                    ocppService.sendAuthorize(session.getId()).get();

                    // StartTransaction si autorisé
                    if (session.isAuthorized()) {
                        ocppService.sendStartTransaction(session.getId());
                    }
                } catch (Exception e) {
                    log.error("[RemoteStart] Failed to complete start sequence", e);
                }
            }).start();

        } catch (Exception e) {
            log.error("[RemoteStart] Error", e);
            response.put("status", "Rejected");
        }

        return response;
    }

    private boolean canRemoteStart(SessionState state) {
        return state == SessionState.AVAILABLE ||
               state == SessionState.BOOT_ACCEPTED ||
               state == SessionState.PARKED ||
               state == SessionState.PLUGGED ||
               state == SessionState.RESERVED ||
               state == SessionState.PREPARING ||
               state == SessionState.FINISHING;
    }
}
