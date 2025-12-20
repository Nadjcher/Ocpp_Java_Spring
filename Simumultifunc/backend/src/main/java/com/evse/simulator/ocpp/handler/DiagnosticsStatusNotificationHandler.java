package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.model.types.DiagnosticsStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message DiagnosticsStatusNotification (CP → CS).
 * <p>
 * Notifie le CSMS de l'état du téléchargement des diagnostics.
 * </p>
 *
 * <h3>Flux OCPP 1.6:</h3>
 * <pre>
 * 1. CS envoie GetDiagnostics.req
 * 2. CP répond avec GetDiagnostics.conf (filename)
 * 3. CP envoie DiagnosticsStatusNotification pour indiquer la progression
 * 4. CS répond avec DiagnosticsStatusNotification.conf (vide)
 * </pre>
 */
@Component
public class DiagnosticsStatusNotificationHandler extends AbstractOcppHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.DIAGNOSTICS_STATUS_NOTIFICATION;
    }

    @Override
    public Map<String, Object> buildPayload(OcppMessageContext context) {
        // Récupérer le statut depuis additionalData ou utiliser Idle par défaut
        String statusValue = DiagnosticsStatus.IDLE.getValue();

        if (context.getAdditionalData() != null) {
            Object statusObj = context.getAdditionalData().get("diagnosticsStatus");
            if (statusObj instanceof DiagnosticsStatus ds) {
                statusValue = ds.getValue();
            } else if (statusObj instanceof String s) {
                statusValue = s;
            }
        }

        return createPayload("status", statusValue);
    }

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        // DiagnosticsStatusNotification.conf est vide selon OCPP 1.6
        log.debug("[{}] DiagnosticsStatusNotification acknowledged", sessionId);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Construit un payload pour un statut spécifique.
     *
     * @param status le statut de diagnostic
     * @return le payload construit
     */
    public Map<String, Object> buildPayloadForStatus(DiagnosticsStatus status) {
        return createPayload("status", status.getValue());
    }

    /**
     * Construit un payload pour un statut sous forme de chaîne.
     *
     * @param status le statut sous forme de chaîne
     * @return le payload construit
     */
    public Map<String, Object> buildPayloadForStatus(String status) {
        return createPayload("status", status);
    }
}
