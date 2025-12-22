package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.Session;

import java.util.Map;

/**
 * Interface pour les handlers de messages OCPP 1.6 entrants (CSMS → CP).
 */
public interface Ocpp16IncomingHandler {

    /**
     * Retourne le nom de l'action OCPP gérée.
     */
    String getAction();

    /**
     * Traite le message entrant et retourne la réponse.
     *
     * @param session Session concernée
     * @param payload Payload du message
     * @return Réponse à envoyer au CSMS
     */
    Map<String, Object> handle(Session session, Map<String, Object> payload);
}
