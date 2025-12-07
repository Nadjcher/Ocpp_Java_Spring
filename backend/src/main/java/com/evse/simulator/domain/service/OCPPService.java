package com.evse.simulator.domain.service;

import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.OCPPAction;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface de communication OCPP 1.6 avec le CSMS.
 */
public interface OCPPService {

    // Connection Management
    CompletableFuture<Boolean> connect(String sessionId);
    void disconnect(String sessionId);
    boolean isConnected(String sessionId);
    int getActiveConnectionsCount();
    void disconnectAll();

    // OCPP Messages - Charge Point to CSMS
    CompletableFuture<Map<String, Object>> sendBootNotification(String sessionId);
    CompletableFuture<Map<String, Object>> sendAuthorize(String sessionId);
    CompletableFuture<Map<String, Object>> sendStartTransaction(String sessionId);
    CompletableFuture<Map<String, Object>> sendStopTransaction(String sessionId);
    CompletableFuture<Map<String, Object>> sendStatusNotification(String sessionId, ConnectorStatus status);
    CompletableFuture<Map<String, Object>> sendMeterValues(String sessionId);
    CompletableFuture<Map<String, Object>> sendHeartbeat(String sessionId);

    // Generic Call
    CompletableFuture<Map<String, Object>> sendCall(String sessionId, OCPPAction action, Map<String, Object> payload);

    /**
     * Envoie un message OCPP avec action en String (compatibilité frontend).
     */
    default CompletableFuture<Map<String, Object>> sendMessage(String sessionId, String action, Map<String, Object> payload) {
        OCPPAction ocppAction = OCPPAction.fromValue(action);
        if (ocppAction == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown OCPP action: " + action)
            );
        }
        return sendCall(sessionId, ocppAction, payload);
    }

    // Message Handling - CSMS to Charge Point
    void handleCallResult(String sessionId, String messageId, Map<String, Object> payload);
    void handleCallError(String sessionId, String messageId, String errorCode, String errorDescription);

    // Meter Values Scheduling
    void startMeterValuesWithInterval(String sessionId, int intervalSec);
    void stopMeterValuesPublic(String sessionId);
}