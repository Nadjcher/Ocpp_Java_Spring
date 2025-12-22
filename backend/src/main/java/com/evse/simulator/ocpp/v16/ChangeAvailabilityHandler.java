package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler pour ChangeAvailability (OCPP 1.6).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChangeAvailabilityHandler implements Ocpp16IncomingHandler {

    private final ReservationService reservationService;

    @Override
    public String getAction() {
        return "ChangeAvailability";
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        Integer connectorId = payload.get("connectorId") != null ?
                ((Number) payload.get("connectorId")).intValue() : null;
        String type = (String) payload.get("type");

        log.info("[ChangeAvailability] Session={}, connectorId={}, type={}",
                session.getId(), connectorId, type);

        if (type == null || (!"Operative".equals(type) && !"Inoperative".equals(type))) {
            response.put("status", "Rejected");
            return response;
        }

        // connectorId 0 = tous les connecteurs
        if (connectorId != null && connectorId != 0 && connectorId != session.getConnectorId()) {
            response.put("status", "Rejected");
            return response;
        }

        SessionState currentState = session.getState();

        if ("Inoperative".equals(type)) {
            // Passer en indisponible
            if (currentState.hasActiveTransaction()) {
                // Transaction en cours, le changement sera effectif après
                response.put("status", "Scheduled");
                session.addLog(LogEntry.info("ChangeAvailability",
                        "Inoperative planifié après fin de charge"));
            } else {
                session.setState(SessionState.UNAVAILABLE);
                session.setLastStateChange(LocalDateTime.now());
                reservationService.sendStatusNotification(session.getId(), ConnectorStatus.UNAVAILABLE);
                response.put("status", "Accepted");
                session.addLog(LogEntry.warn("ChangeAvailability", "Connecteur indisponible"));
            }
        } else {
            // Passer en disponible
            if (currentState == SessionState.UNAVAILABLE) {
                session.setState(SessionState.AVAILABLE);
                session.setLastStateChange(LocalDateTime.now());
                reservationService.sendStatusNotification(session.getId(), ConnectorStatus.AVAILABLE);
            }
            response.put("status", "Accepted");
            session.addLog(LogEntry.success("ChangeAvailability", "Connecteur disponible"));
        }

        return response;
    }
}
