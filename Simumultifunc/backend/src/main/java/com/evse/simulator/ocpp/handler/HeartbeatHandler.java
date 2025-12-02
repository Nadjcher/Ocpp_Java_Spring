package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.enums.OCPPAction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message Heartbeat.
 * Maintient la connexion active et synchronise l'horloge.
 */
@Component
public class HeartbeatHandler extends AbstractOcppHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.HEARTBEAT;
    }

    @Override
    public Map<String, Object> buildPayload(OcppMessageContext context) {
        // Heartbeat.req est vide selon OCPP 1.6
        return Collections.emptyMap();
    }

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        String currentTime = (String) response.get("currentTime");

        if (currentTime != null) {
            try {
                Instant serverTime = Instant.parse(currentTime);
                Instant localTime = Instant.now();
                long drift = Math.abs(serverTime.toEpochMilli() - localTime.toEpochMilli());

                if (drift > 5000) {
                    log.warn("[{}] Dérive d'horloge détectée: {}ms", sessionId, drift);
                } else {
                    log.trace("[{}] Heartbeat OK, currentTime={}", sessionId, currentTime);
                }
            } catch (Exception e) {
                log.debug("[{}] Format currentTime invalide: {}", sessionId, currentTime);
            }
        }

        return CompletableFuture.completedFuture(response);
    }
}
