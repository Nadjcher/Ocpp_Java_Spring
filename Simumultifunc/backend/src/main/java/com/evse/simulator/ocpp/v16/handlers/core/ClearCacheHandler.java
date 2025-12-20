package com.evse.simulator.ocpp.v16.handlers.core;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.model.types.ClearCacheStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler pour ClearCache (CS → CP).
 * <p>
 * Vide le cache d'autorisation local du Charge Point.
 * </p>
 */
@Slf4j
@Component
public class ClearCacheHandler extends AbstractOcpp16IncomingHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.CLEAR_CACHE;
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        // Dans le simulateur, on accepte toujours
        // (pas de cache local réel à vider)
        ClearCacheStatus status = ClearCacheStatus.ACCEPTED;

        logToSession(session, "ClearCache ACCEPTED - Authorization cache cleared");
        log.info("[{}] ClearCache: Authorization cache cleared", session.getId());

        Map<String, Object> response = createResponse(status);
        logExit(session, response);
        return response;
    }
}
