package com.evse.simulator.ocpp.v16.handlers.localauth;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.UpdateStatus;
import com.evse.simulator.ocpp.v16.model.types.UpdateType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handler pour SendLocalList (CS → CP).
 */
@Slf4j
@Component
public class SendLocalListHandler extends AbstractOcpp16IncomingHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.SEND_LOCAL_LIST;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "listVersion");
        requireField(payload, "updateType");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        Integer listVersion = getInteger(payload, "listVersion", true);
        String updateTypeStr = getString(payload, "updateType", true);
        UpdateType updateType = UpdateType.fromValue(updateTypeStr);

        // LocalAuthorizationList est optionnel
        List<Map<String, Object>> localAuthorizationList = null;
        Object listObj = payload.get("localAuthorizationList");
        if (listObj instanceof List) {
            localAuthorizationList = (List<Map<String, Object>>) listObj;
        }

        UpdateStatus status;

        if (updateType == null) {
            status = UpdateStatus.FAILED;
            logToSession(session, "SendLocalList FAILED - invalid updateType");
        } else {
            // Simuler l'acceptation de la liste
            int count = localAuthorizationList != null ? localAuthorizationList.size() : 0;

            status = UpdateStatus.ACCEPTED;
            log.info("[{}] SendLocalList: version={}, type={}, entries={}",
                    session.getId(), listVersion, updateType, count);
            logToSession(session, String.format(
                    "SendLocalList ACCEPTED - version=%d, type=%s, entries=%d",
                    listVersion, updateType.getValue(), count));
        }

        Map<String, Object> response = createResponse(status);
        logExit(session, response);
        return response;
    }
}
