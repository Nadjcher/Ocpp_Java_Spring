package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;

import java.util.Map;

/**
 * Interface pour les handlers de messages OCPP 1.6 entrants (CS → CP).
 * <p>
 * Ces handlers traitent les commandes envoyées par le Central System
 * vers le Charge Point (simulateur).
 * </p>
 */
public interface Ocpp16IncomingHandler {

    /**
     * Retourne l'action OCPP gérée par ce handler.
     *
     * @return L'action OCPP
     */
    OCPPAction getAction();

    /**
     * Traite le message entrant et retourne la réponse.
     *
     * @param session La session concernée
     * @param payload Le payload du message reçu
     * @return La réponse à envoyer au CSMS
     */
    Map<String, Object> handle(Session session, Map<String, Object> payload);

    /**
     * Valide le payload avant traitement.
     *
     * @param payload Le payload à valider
     * @throws Ocpp16Exception si le payload est invalide
     */
    default void validate(Map<String, Object> payload) throws Ocpp16Exception {
        if (payload == null) {
            throw new Ocpp16Exception("Payload cannot be null", Ocpp16Exception.FORMATION_VIOLATION);
        }
    }

    /**
     * Retourne le nom de l'action sous forme de chaîne.
     *
     * @return Le nom de l'action
     */
    default String getActionName() {
        return getAction().getValue();
    }

    /**
     * Vérifie si un champ requis est présent.
     *
     * @param payload   Le payload
     * @param fieldName Le nom du champ
     * @throws Ocpp16Exception si le champ est absent
     */
    default void requireField(Map<String, Object> payload, String fieldName) throws Ocpp16Exception {
        if (!payload.containsKey(fieldName) || payload.get(fieldName) == null) {
            throw Ocpp16Exception.missingField(fieldName);
        }
    }

    /**
     * Récupère un champ String avec validation.
     *
     * @param payload   Le payload
     * @param fieldName Le nom du champ
     * @param required  Si le champ est requis
     * @return La valeur ou null si optionnel et absent
     */
    default String getString(Map<String, Object> payload, String fieldName, boolean required) {
        Object value = payload.get(fieldName);
        if (value == null) {
            if (required) {
                throw Ocpp16Exception.missingField(fieldName);
            }
            return null;
        }
        return value.toString();
    }

    /**
     * Récupère un champ Integer avec validation.
     *
     * @param payload   Le payload
     * @param fieldName Le nom du champ
     * @param required  Si le champ est requis
     * @return La valeur ou null si optionnel et absent
     */
    default Integer getInteger(Map<String, Object> payload, String fieldName, boolean required) {
        Object value = payload.get(fieldName);
        if (value == null) {
            if (required) {
                throw Ocpp16Exception.missingField(fieldName);
            }
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw Ocpp16Exception.invalidType(fieldName, "integer");
        }
    }

    /**
     * Récupère un champ Double avec validation.
     *
     * @param payload   Le payload
     * @param fieldName Le nom du champ
     * @param required  Si le champ est requis
     * @return La valeur ou null si optionnel et absent
     */
    default Double getDouble(Map<String, Object> payload, String fieldName, boolean required) {
        Object value = payload.get(fieldName);
        if (value == null) {
            if (required) {
                throw Ocpp16Exception.missingField(fieldName);
            }
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw Ocpp16Exception.invalidType(fieldName, "number");
        }
    }

    /**
     * Récupère un sous-objet Map avec validation.
     *
     * @param payload   Le payload
     * @param fieldName Le nom du champ
     * @param required  Si le champ est requis
     * @return La valeur ou null si optionnel et absent
     */
    @SuppressWarnings("unchecked")
    default Map<String, Object> getObject(Map<String, Object> payload, String fieldName, boolean required) {
        Object value = payload.get(fieldName);
        if (value == null) {
            if (required) {
                throw Ocpp16Exception.missingField(fieldName);
            }
            return null;
        }
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        throw Ocpp16Exception.invalidType(fieldName, "object");
    }
}
