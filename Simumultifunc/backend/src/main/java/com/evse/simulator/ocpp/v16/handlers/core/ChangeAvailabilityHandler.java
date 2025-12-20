package com.evse.simulator.ocpp.v16.handlers.core;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.AvailabilityStatus;
import com.evse.simulator.ocpp.v16.model.types.AvailabilityType;
import com.evse.simulator.service.SessionStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler pour ChangeAvailability (CS → CP).
 */
@Slf4j
@Component
public class ChangeAvailabilityHandler extends AbstractOcpp16IncomingHandler {

    private final SessionStateManager stateManager;

    public ChangeAvailabilityHandler(SessionStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public OCPPAction getAction() {
        return OCPPAction.CHANGE_AVAILABILITY;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "connectorId");
        requireField(payload, "type");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        Integer connectorId = getInteger(payload, "connectorId", true);
        String typeStr = getString(payload, "type", true);
        AvailabilityType type = AvailabilityType.fromValue(typeStr);

        AvailabilityStatus status;

        // Si transaction en cours, programmer le changement
        if (session.isCharging() || session.getTransactionId() != null) {
            // Le changement sera effectué après la transaction
            status = AvailabilityStatus.SCHEDULED;
            logToSession(session, String.format(
                "ChangeAvailability SCHEDULED - type: %s (transaction en cours)", typeStr));
        } else {
            // Appliquer immédiatement
            if (type == AvailabilityType.INOPERATIVE) {
                stateManager.forceTransition(session, SessionState.UNAVAILABLE, "ChangeAvailability from CSMS");
            } else {
                stateManager.forceTransition(session, SessionState.AVAILABLE, "ChangeAvailability from CSMS");
            }
            status = AvailabilityStatus.ACCEPTED;
            logToSession(session, String.format(
                "ChangeAvailability ACCEPTED - type: %s", typeStr));
        }

        Map<String, Object> response = createResponse(status);
        logExit(session, response);
        return response;
    }
}
