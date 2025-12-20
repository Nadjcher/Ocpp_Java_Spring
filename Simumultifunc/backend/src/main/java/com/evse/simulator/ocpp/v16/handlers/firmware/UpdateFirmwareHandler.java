package com.evse.simulator.ocpp.v16.handlers.firmware;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler pour UpdateFirmware (CS → CP).
 * <p>
 * Note: UpdateFirmware ne retourne pas de payload (réponse vide).
 * </p>
 */
@Slf4j
@Component
public class UpdateFirmwareHandler extends AbstractOcpp16IncomingHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.UPDATE_FIRMWARE;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "location");
        requireField(payload, "retrieveDate");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        String location = getString(payload, "location", true);
        String retrieveDate = getString(payload, "retrieveDate", true);
        Integer retries = getInteger(payload, "retries", false);
        Integer retryInterval = getInteger(payload, "retryInterval", false);

        log.info("[{}] UpdateFirmware: location={}, retrieveDate={}",
                session.getId(), location, retrieveDate);
        logToSession(session, String.format(
                "UpdateFirmware: scheduled from %s at %s", location, retrieveDate));

        // UpdateFirmware.conf est vide
        Map<String, Object> response = createEmptyResponse();
        logExit(session, response);
        return response;
    }
}
