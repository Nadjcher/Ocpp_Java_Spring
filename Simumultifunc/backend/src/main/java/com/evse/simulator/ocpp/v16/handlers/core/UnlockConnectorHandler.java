package com.evse.simulator.ocpp.v16.handlers.core;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.UnlockStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler pour UnlockConnector (CS → CP).
 */
@Slf4j
@Component
public class UnlockConnectorHandler extends AbstractOcpp16IncomingHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.UNLOCK_CONNECTOR;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "connectorId");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        Integer connectorId = getInteger(payload, "connectorId", true);

        UnlockStatus status;

        // Vérifier que le connecteur existe
        if (connectorId < 1 || connectorId > 1) { // Simulateur avec 1 connecteur
            log.warn("[{}] UnlockConnector: Invalid connector {}", session.getId(), connectorId);
            status = UnlockStatus.NOT_SUPPORTED;
        } else {
            // Simuler le déverrouillage
            log.info("[{}] UnlockConnector: Connector {} unlocked", session.getId(), connectorId);
            status = UnlockStatus.UNLOCKED;
        }

        logToSession(session, String.format(
            "UnlockConnector %s - connector: %d",
            status.getValue(), connectorId));

        Map<String, Object> response = createResponse(status);
        logExit(session, response);
        return response;
    }
}
