package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Routeur pour les messages OCPP 1.6 entrants (CSMS → CP).
 * Gère les actions initiées par le Central System.
 */
@Component
@Slf4j
public class Ocpp16MessageRouter {

    private final Map<String, Ocpp16IncomingHandler> handlers = new HashMap<>();

    public Ocpp16MessageRouter(
            ReserveNowHandler reserveNowHandler,
            CancelReservationHandler cancelReservationHandler,
            RemoteStartTransactionHandler remoteStartHandler,
            RemoteStopTransactionHandler remoteStopHandler,
            GetConfigurationHandler getConfigurationHandler,
            ChangeConfigurationHandler changeConfigurationHandler,
            ResetHandler resetHandler,
            UnlockConnectorHandler unlockConnectorHandler,
            ChangeAvailabilityHandler changeAvailabilityHandler,
            TriggerMessageHandler triggerMessageHandler
    ) {
        // Enregistrer tous les handlers
        registerHandler(reserveNowHandler);
        registerHandler(cancelReservationHandler);
        registerHandler(remoteStartHandler);
        registerHandler(remoteStopHandler);
        registerHandler(getConfigurationHandler);
        registerHandler(changeConfigurationHandler);
        registerHandler(resetHandler);
        registerHandler(unlockConnectorHandler);
        registerHandler(changeAvailabilityHandler);
        registerHandler(triggerMessageHandler);

        log.info("Ocpp16MessageRouter initialized with {} handlers", handlers.size());
    }

    private void registerHandler(Ocpp16IncomingHandler handler) {
        handlers.put(handler.getAction(), handler);
        log.debug("Registered handler for action: {}", handler.getAction());
    }

    /**
     * Vérifie si une action est supportée par ce routeur.
     */
    public boolean isActionSupported(String action) {
        return handlers.containsKey(action);
    }

    /**
     * Route un message CALL entrant vers le handler approprié.
     *
     * @param session Session concernée
     * @param action  Action OCPP
     * @param payload Payload du message
     * @return Réponse à envoyer au CSMS
     */
    public Map<String, Object> routeIncomingCall(Session session, String action, Map<String, Object> payload) {
        Ocpp16IncomingHandler handler = handlers.get(action);

        if (handler == null) {
            log.warn("No handler found for action: {}", action);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "NotImplemented");
            return response;
        }

        log.debug("Routing {} to {}", action, handler.getClass().getSimpleName());
        return handler.handle(session, payload);
    }
}
