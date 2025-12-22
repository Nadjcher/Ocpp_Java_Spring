package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler pour UnlockConnector (OCPP 1.6).
 */
@Component
@Slf4j
public class UnlockConnectorHandler implements Ocpp16IncomingHandler {

    @Override
    public String getAction() {
        return "UnlockConnector";
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        Integer connectorId = payload.get("connectorId") != null ?
                ((Number) payload.get("connectorId")).intValue() : null;

        log.info("[UnlockConnector] Session={}, connectorId={}", session.getId(), connectorId);

        if (connectorId == null || connectorId < 1) {
            response.put("status", "NotSupported");
            return response;
        }

        // Vérifier si le connecteur existe
        if (connectorId != session.getConnectorId()) {
            response.put("status", "NotSupported");
            return response;
        }

        // Simuler le déverrouillage
        SessionState state = session.getState();

        if (state == SessionState.CHARGING || state.hasActiveTransaction()) {
            // Ne peut pas déverrouiller pendant une charge
            response.put("status", "UnlockFailed");
            session.addLog(LogEntry.warn("UnlockConnector failed: charge en cours"));
        } else if (state == SessionState.PLUGGED || state == SessionState.PREPARING ||
                   state == SessionState.FINISHING) {
            // Simuler le débranchement
            session.setPlugged(false);
            session.setState(SessionState.AVAILABLE);
            session.setLastStateChange(LocalDateTime.now());

            response.put("status", "Unlocked");
            session.addLog(LogEntry.info("UnlockConnector", "Connecteur déverrouillé"));
        } else {
            // Déjà déverrouillé
            response.put("status", "Unlocked");
        }

        return response;
    }
}
