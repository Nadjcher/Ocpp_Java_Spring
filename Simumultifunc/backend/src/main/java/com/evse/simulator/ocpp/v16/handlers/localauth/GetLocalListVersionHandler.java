package com.evse.simulator.ocpp.v16.handlers.localauth;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler pour GetLocalListVersion (CS → CP).
 */
@Slf4j
@Component
public class GetLocalListVersionHandler extends AbstractOcpp16IncomingHandler {

    // Version de la liste locale (simulée)
    private static final int LOCAL_LIST_VERSION = 1;

    @Override
    public OCPPAction getAction() {
        return OCPPAction.GET_LOCAL_LIST_VERSION;
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        Map<String, Object> response = new HashMap<>();
        response.put("listVersion", LOCAL_LIST_VERSION);

        log.info("[{}] GetLocalListVersion: version={}", session.getId(), LOCAL_LIST_VERSION);
        logToSession(session, String.format("GetLocalListVersion: version=%d", LOCAL_LIST_VERSION));

        logExit(session, response);
        return response;
    }
}
