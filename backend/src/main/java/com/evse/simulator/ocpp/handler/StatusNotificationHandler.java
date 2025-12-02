package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.enums.OCPPAction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message StatusNotification.
 * Notifie le CSMS du statut d'un connecteur.
 */
@Component
public class StatusNotificationHandler extends AbstractOcppHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.STATUS_NOTIFICATION;
    }

    @Override
    public Map<String, Object> buildPayload(OcppMessageContext context) {
        int connectorId = context.getConnectorId();
        String status = context.getStatus() != null ? context.getStatus() : "Available";
        String errorCode = context.getErrorCode() != null ? context.getErrorCode() : "NoError";

        Map<String, Object> payload = createPayload(
            "connectorId", connectorId,
            "status", status,
            "errorCode", errorCode,
            "timestamp", formatTimestamp()
        );

        // Ajouter info vendeur si présente
        if (context.getVendorInfo() != null) {
            payload.put("vendorErrorCode", context.getVendorInfo());
        }

        return payload;
    }

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        // StatusNotification.conf est vide selon OCPP 1.6
        log.debug("[{}] StatusNotification acknowledged", sessionId);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Construit un payload pour un statut spécifique.
     */
    public Map<String, Object> buildPayloadForStatus(int connectorId, String status) {
        return createPayload(
            "connectorId", connectorId,
            "status", status,
            "errorCode", "NoError",
            "timestamp", formatTimestamp()
        );
    }

    /**
     * Construit un payload pour une erreur.
     */
    public Map<String, Object> buildPayloadForError(int connectorId, String errorCode, String vendorErrorCode) {
        Map<String, Object> payload = createPayload(
            "connectorId", connectorId,
            "status", "Faulted",
            "errorCode", errorCode,
            "timestamp", formatTimestamp()
        );

        if (vendorErrorCode != null) {
            payload.put("vendorErrorCode", vendorErrorCode);
        }

        return payload;
    }
}
