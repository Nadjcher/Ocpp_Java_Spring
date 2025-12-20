package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service de routage des messages OCPP 1.6 entrants.
 * <p>
 * Ce service peut être injecté dans les composants qui ont besoin de router
 * les messages OCPP vers les handlers appropriés.
 * </p>
 */
@Slf4j
@Service
public class Ocpp16MessageRouter {

    private final Ocpp16IncomingHandlerRegistry registry;

    public Ocpp16MessageRouter(Ocpp16IncomingHandlerRegistry registry) {
        this.registry = registry;
    }

    /**
     * Route un message CALL entrant vers le handler approprié.
     *
     * @param session La session concernée
     * @param action  Le nom de l'action OCPP
     * @param payload Le payload du message
     * @return La réponse à envoyer au CSMS
     */
    public Map<String, Object> routeIncomingCall(Session session, String action,
                                                  Map<String, Object> payload) {
        try {
            log.info("[{}] Routing incoming CALL: {}", session.getId(), action);

            // Vérifier si l'action est supportée
            if (!registry.isSupported(action)) {
                log.warn("[{}] Action not supported: {}", session.getId(), action);
                return createNotImplementedResponse(action);
            }

            // Router vers le handler
            return registry.handleMessage(session, action, payload);

        } catch (Ocpp16Exception e) {
            log.error("[{}] Handler error for {}: {}", session.getId(), action, e.getMessage());
            return createErrorResponse(e);
        } catch (Exception e) {
            log.error("[{}] Unexpected error for {}: {}",
                    session.getId(), action, e.getMessage(), e);
            return createInternalErrorResponse(e.getMessage());
        }
    }

    /**
     * Vérifie si une action est supportée par les handlers.
     *
     * @param action Le nom de l'action
     * @return true si l'action est supportée
     */
    public boolean isActionSupported(String action) {
        return registry.isSupported(action);
    }

    /**
     * Retourne le registry sous-jacent.
     *
     * @return Le registry
     */
    public Ocpp16IncomingHandlerRegistry getRegistry() {
        return registry;
    }

    private Map<String, Object> createNotImplementedResponse(String action) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "NotImplemented");
        return response;
    }

    private Map<String, Object> createErrorResponse(Ocpp16Exception e) {
        Map<String, Object> response = new HashMap<>();
        // Pour la plupart des actions, on retourne status="Rejected"
        response.put("status", "Rejected");
        return response;
    }

    private Map<String, Object> createInternalErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "Rejected");
        return response;
    }
}
