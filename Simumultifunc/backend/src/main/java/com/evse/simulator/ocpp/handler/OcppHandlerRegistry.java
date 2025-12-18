package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.enums.OCPPAction;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registre des handlers OCPP.
 * Permet de récupérer le handler approprié pour chaque action.
 */
@Component
public class OcppHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(OcppHandlerRegistry.class);

    private final List<OcppMessageHandler> handlers;
    private final Map<OCPPAction, OcppMessageHandler> handlerMap;

    public OcppHandlerRegistry(List<OcppMessageHandler> handlers) {
        this.handlers = handlers;
        this.handlerMap = new EnumMap<>(OCPPAction.class);
    }

    @PostConstruct
    public void init() {
        for (OcppMessageHandler handler : handlers) {
            OCPPAction action = handler.getAction();
            if (handlerMap.containsKey(action)) {
                log.warn("Handler dupliqué pour l'action {}: {} remplacé par {}",
                        action,
                        handlerMap.get(action).getClass().getSimpleName(),
                        handler.getClass().getSimpleName());
            }
            handlerMap.put(action, handler);
            log.debug("Handler enregistré: {} -> {}",
                    action, handler.getClass().getSimpleName());
        }

        log.info("OcppHandlerRegistry initialisé avec {} handlers", handlerMap.size());
    }

    /**
     * Récupère le handler pour une action donnée.
     *
     * @param action Action OCPP
     * @return Optional contenant le handler ou vide si non trouvé
     */
    public Optional<OcppMessageHandler> getHandler(OCPPAction action) {
        return Optional.ofNullable(handlerMap.get(action));
    }

    /**
     * Récupère le handler pour une action donnée ou lance une exception.
     *
     * @param action Action OCPP
     * @return Le handler
     * @throws IllegalArgumentException si aucun handler n'est trouvé
     */
    public OcppMessageHandler getHandlerOrThrow(OCPPAction action) {
        return getHandler(action)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun handler trouvé pour l'action: " + action));
    }

    /**
     * Vérifie si un handler existe pour une action.
     *
     * @param action Action OCPP
     * @return true si un handler existe
     */
    public boolean hasHandler(OCPPAction action) {
        return handlerMap.containsKey(action);
    }

    /**
     * Retourne le nombre de handlers enregistrés.
     */
    public int getHandlerCount() {
        return handlerMap.size();
    }

    /**
     * Retourne toutes les actions supportées.
     */
    public java.util.Set<OCPPAction> getSupportedActions() {
        return java.util.Collections.unmodifiableSet(handlerMap.keySet());
    }
}
