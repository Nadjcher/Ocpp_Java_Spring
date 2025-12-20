package com.evse.simulator.ocpp.v16.handlers.firmware;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler pour GetDiagnostics (CS → CP).
 */
@Slf4j
@Component
public class GetDiagnosticsHandler extends AbstractOcpp16IncomingHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.GET_DIAGNOSTICS;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "location");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        String location = getString(payload, "location", true);
        Integer retries = getInteger(payload, "retries", false);
        Integer retryInterval = getInteger(payload, "retryInterval", false);
        String startTime = getString(payload, "startTime", false);
        String stopTime = getString(payload, "stopTime", false);

        Map<String, Object> response = new HashMap<>();

        // Simuler le téléchargement de diagnostics
        // Retourner un nom de fichier fictif
        String fileName = String.format("diagnostics_%s_%d.zip",
                session.getCpId(),
                System.currentTimeMillis());

        response.put("fileName", fileName);

        log.info("[{}] GetDiagnostics: location={}, fileName={}",
                session.getId(), location, fileName);
        logToSession(session, String.format(
                "GetDiagnostics: uploading to %s as %s", location, fileName));

        logExit(session, response);
        return response;
    }
}
