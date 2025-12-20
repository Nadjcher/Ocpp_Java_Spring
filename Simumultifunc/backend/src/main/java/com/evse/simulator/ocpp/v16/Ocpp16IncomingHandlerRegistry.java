package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry et router pour les handlers OCPP 1.6 entrants (CS → CP).
 * <p>
 * Centralise l'enregistrement de tous les handlers et route les messages
 * vers le handler approprié en fonction de l'action OCPP.
 * </p>
 */
@Slf4j
@Component
public class Ocpp16IncomingHandlerRegistry {

    private final Map<OCPPAction, Ocpp16IncomingHandler> handlers = new ConcurrentHashMap<>();
    private final List<Ocpp16IncomingHandler> handlerList;

    public Ocpp16IncomingHandlerRegistry(List<Ocpp16IncomingHandler> handlerList) {
        this.handlerList = handlerList;
    }

    @PostConstruct
    public void init() {
        // Enregistrer tous les handlers
        for (Ocpp16IncomingHandler handler : handlerList) {
            registerHandler(handler);
        }

        log.info("OCPP 1.6 Incoming Handler Registry initialized with {} handlers: {}",
                handlers.size(), getSupportedActions());
    }

    /**
     * Enregistre un handler.
     *
     * @param handler Le handler à enregistrer
     */
    public void registerHandler(Ocpp16IncomingHandler handler) {
        OCPPAction action = handler.getAction();
        if (action != null) {
            Ocpp16IncomingHandler existing = handlers.put(action, handler);
            if (existing != null) {
                log.warn("Handler for action {} was replaced: {} -> {}",
                        action, existing.getClass().getSimpleName(),
                        handler.getClass().getSimpleName());
            } else {
                log.debug("Registered handler for action {}: {}",
                        action, handler.getClass().getSimpleName());
            }
        }
    }

    /**
     * Récupère un handler par action.
     *
     * @param action L'action OCPP
     * @return Optional contenant le handler ou vide si non trouvé
     */
    public Optional<Ocpp16IncomingHandler> getHandler(OCPPAction action) {
        return Optional.ofNullable(handlers.get(action));
    }

    /**
     * Récupère un handler par nom d'action.
     *
     * @param actionName Le nom de l'action (ex: "RemoteStartTransaction")
     * @return Optional contenant le handler ou vide si non trouvé
     */
    public Optional<Ocpp16IncomingHandler> getHandler(String actionName) {
        OCPPAction action = OCPPAction.fromValue(actionName);
        if (action == null) {
            return Optional.empty();
        }
        return getHandler(action);
    }

    /**
     * Route un message vers le handler approprié et retourne la réponse.
     *
     * @param session La session concernée
     * @param action  Le nom de l'action OCPP
     * @param payload Le payload du message
     * @return La réponse du handler
     * @throws Ocpp16Exception si l'action n'est pas supportée ou si une erreur survient
     */
    public Map<String, Object> handleMessage(Session session, String action,
                                              Map<String, Object> payload) {
        log.debug("[{}] Routing message: action={}", session.getId(), action);

        // Trouver le handler
        Optional<Ocpp16IncomingHandler> handlerOpt = getHandler(action);

        if (handlerOpt.isEmpty()) {
            log.warn("[{}] No handler found for action: {}", session.getId(), action);
            throw Ocpp16Exception.notImplemented(action);
        }

        Ocpp16IncomingHandler handler = handlerOpt.get();

        try {
            // Valider le payload
            handler.validate(payload);

            // Exécuter le handler
            Map<String, Object> response = handler.handle(session, payload);

            log.debug("[{}] Handler {} completed successfully", session.getId(), action);
            return response;

        } catch (Ocpp16Exception e) {
            log.error("[{}] Handler {} failed: {}", session.getId(), action, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[{}] Handler {} unexpected error: {}",
                    session.getId(), action, e.getMessage(), e);
            throw new Ocpp16Exception(
                    "Handler error: " + e.getMessage(),
                    Ocpp16Exception.INTERNAL_ERROR,
                    e
            );
        }
    }

    /**
     * Vérifie si une action est supportée.
     *
     * @param action L'action à vérifier
     * @return true si l'action est supportée
     */
    public boolean isSupported(String action) {
        return getHandler(action).isPresent();
    }

    /**
     * Vérifie si une action est supportée.
     *
     * @param action L'action à vérifier
     * @return true si l'action est supportée
     */
    public boolean isSupported(OCPPAction action) {
        return handlers.containsKey(action);
    }

    /**
     * Retourne la liste des actions supportées.
     *
     * @return Set des actions supportées
     */
    public Set<OCPPAction> getSupportedActions() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /**
     * Retourne le nombre de handlers enregistrés.
     *
     * @return Nombre de handlers
     */
    public int getHandlerCount() {
        return handlers.size();
    }
}
