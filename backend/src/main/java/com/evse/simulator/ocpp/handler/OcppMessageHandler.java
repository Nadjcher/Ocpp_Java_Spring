package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.enums.OCPPAction;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface de base pour les handlers de messages OCPP.
 * Chaque handler gère un type spécifique d'action OCPP.
 */
public interface OcppMessageHandler {

    /**
     * Retourne l'action OCPP gérée par ce handler.
     */
    OCPPAction getAction();

    /**
     * Construit le payload pour l'envoi du message.
     *
     * @param context Contexte contenant les données nécessaires
     * @return Map du payload à envoyer
     */
    Map<String, Object> buildPayload(OcppMessageContext context);

    /**
     * Traite la réponse reçue du CSMS.
     *
     * @param sessionId ID de la session
     * @param response Réponse reçue
     * @return Future avec le résultat du traitement
     */
    CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response);

    /**
     * Traite une erreur reçue.
     *
     * @param sessionId ID de la session
     * @param errorCode Code d'erreur
     * @param errorDescription Description de l'erreur
     */
    void handleError(String sessionId, String errorCode, String errorDescription);

    /**
     * Valide le contexte avant l'envoi.
     *
     * @param context Contexte à valider
     * @return true si le contexte est valide
     */
    default boolean validateContext(OcppMessageContext context) {
        return context != null && context.getSessionId() != null;
    }
}
