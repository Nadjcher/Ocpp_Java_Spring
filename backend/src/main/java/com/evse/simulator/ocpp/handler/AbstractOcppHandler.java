package com.evse.simulator.ocpp.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Classe abstraite fournissant les fonctionnalités communes aux handlers OCPP.
 */
public abstract class AbstractOcppHandler implements OcppMessageHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        log.debug("[{}] Réponse {} reçue: {}", sessionId, getAction(), response);
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public void handleError(String sessionId, String errorCode, String errorDescription) {
        log.error("[{}] Erreur {} pour {}: {}", sessionId, errorCode, getAction(), errorDescription);
    }

    /**
     * Formate un timestamp ISO 8601.
     */
    protected String formatTimestamp() {
        return java.time.Instant.now().toString();
    }

    /**
     * Crée une map modifiable à partir des paramètres.
     */
    protected Map<String, Object> createPayload(Object... keyValues) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            if (keyValues[i + 1] != null) {
                payload.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return payload;
    }
}
