package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Classe abstraite fournissant les fonctionnalités communes aux handlers OCPP 1.6 entrants.
 */
public abstract class AbstractOcpp16IncomingHandler implements Ocpp16IncomingHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Crée une réponse avec un status.
     *
     * @param status Le status de la réponse
     * @return La map de réponse
     */
    protected Map<String, Object> createResponse(String status) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        return response;
    }

    /**
     * Crée une réponse avec un status enum.
     * Utilise getValue() si disponible pour obtenir la valeur OCPP correcte.
     *
     * @param status Le status enum
     * @return La map de réponse
     */
    protected Map<String, Object> createResponse(Enum<?> status) {
        String statusValue;
        try {
            // Essayer d'appeler getValue() pour obtenir la valeur OCPP conforme
            var method = status.getClass().getMethod("getValue");
            statusValue = (String) method.invoke(status);
        } catch (Exception e) {
            // Fallback: utiliser le nom de l'enum
            statusValue = status.name();
        }
        return createResponse(statusValue);
    }

    /**
     * Crée une réponse vide.
     *
     * @return La map de réponse vide
     */
    protected Map<String, Object> createEmptyResponse() {
        return new HashMap<>();
    }

    /**
     * Formate un timestamp ISO 8601.
     *
     * @return Le timestamp formaté
     */
    protected String formatTimestamp() {
        return Instant.now().toString();
    }

    /**
     * Ajoute un log à la session.
     *
     * @param session La session
     * @param message Le message
     */
    protected void logToSession(Session session, String message) {
        session.addLog(LogEntry.info("OCPP", message));
    }

    /**
     * Ajoute un log d'erreur à la session.
     *
     * @param session La session
     * @param message Le message d'erreur
     */
    protected void logErrorToSession(Session session, String message) {
        session.addLog(LogEntry.error("OCPP", message));
    }

    /**
     * Valide qu'un entier est dans une plage.
     *
     * @param value     La valeur
     * @param fieldName Le nom du champ
     * @param min       La valeur minimum
     * @param max       La valeur maximum
     */
    protected void validateRange(Integer value, String fieldName, int min, int max) {
        if (value != null && (value < min || value > max)) {
            throw Ocpp16Exception.outOfRange(fieldName, value, min, max);
        }
    }

    /**
     * Valide la longueur d'une chaîne.
     *
     * @param value     La valeur
     * @param fieldName Le nom du champ
     * @param maxLength La longueur maximum
     */
    protected void validateStringLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new Ocpp16Exception(
                    String.format("Field %s exceeds maximum length of %d", fieldName, maxLength),
                    Ocpp16Exception.PROPERTY_CONSTRAINT_VIOLATION
            );
        }
    }

    /**
     * Log l'entrée du handler.
     *
     * @param session La session
     * @param payload Le payload reçu
     */
    protected void logEntry(Session session, Map<String, Object> payload) {
        log.info("[{}] {} received: {}", session.getId(), getActionName(), payload);
    }

    /**
     * Log la sortie du handler.
     *
     * @param session  La session
     * @param response La réponse
     */
    protected void logExit(Session session, Map<String, Object> response) {
        log.debug("[{}] {} response: {}", session.getId(), getActionName(), response);
    }
}
