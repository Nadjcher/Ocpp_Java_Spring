package com.evse.simulator.ocpp.v201;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Handler pour le protocole OCPP 2.0.1.
 * Gère les messages selon la spécification OCPP 2.0.1.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OCPP201Handler {

    private final ObjectMapper objectMapper;

    // =========================================================================
    // Message Types OCPP 2.0.1
    // =========================================================================

    public static final int CALL = 2;
    public static final int CALL_RESULT = 3;
    public static final int CALL_ERROR = 4;

    // =========================================================================
    // Handle Incoming Messages
    // =========================================================================

    /**
     * Traite un message OCPP 2.0.1 entrant.
     *
     * @param sessionId ID de la session
     * @param rawMessage message JSON brut
     * @return réponse JSON ou null si pas de réponse nécessaire
     */
    public String handleMessage(String sessionId, String rawMessage) {
        try {
            JsonNode msg = objectMapper.readTree(rawMessage);
            if (!msg.isArray() || msg.size() < 3) {
                log.warn("[OCPP201] Invalid message format: {}", rawMessage);
                return null;
            }

            int messageType = msg.get(0).asInt();
            String messageId = msg.get(1).asText();

            if (messageType == CALL) {
                String action = msg.get(2).asText();
                JsonNode payload = msg.size() > 3 ? msg.get(3) : objectMapper.createObjectNode();

                log.debug("[OCPP201] Session {} received {}: {}", sessionId, action, payload);

                return switch (action) {
                    case "BootNotification" -> handleBootNotification(messageId, payload);
                    case "Heartbeat" -> handleHeartbeat(messageId);
                    case "StatusNotification" -> handleStatusNotification(messageId, payload);
                    case "TransactionEvent" -> handleTransactionEvent(messageId, payload);
                    case "MeterValues" -> handleMeterValues(messageId, payload);
                    case "Authorize" -> handleAuthorize(messageId, payload);
                    default -> {
                        log.warn("[OCPP201] Unknown action: {}", action);
                        yield buildCallError(messageId, "NotImplemented", "Action not supported: " + action);
                    }
                };

            } else if (messageType == CALL_RESULT) {
                // Réponse à un message envoyé
                log.debug("[OCPP201] Session {} received CALL_RESULT for {}", sessionId, messageId);
                return null;

            } else if (messageType == CALL_ERROR) {
                log.warn("[OCPP201] Session {} received CALL_ERROR: {}", sessionId, rawMessage);
                return null;
            }

            return null;

        } catch (Exception e) {
            log.error("[OCPP201] Error handling message: {}", e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Message Handlers
    // =========================================================================

    private String handleBootNotification(String messageId, JsonNode payload) {
        // OCPP 2.0.1 BootNotification: chargingStation {model, vendorName, serialNumber}, reason
        JsonNode chargingStation = payload.get("chargingStation");
        String vendorName = chargingStation != null ? chargingStation.path("vendorName").asText("Unknown") : "Unknown";
        String model = chargingStation != null ? chargingStation.path("model").asText("Unknown") : "Unknown";
        String reason = payload.path("reason").asText("PowerUp");

        log.info("[OCPP201] BootNotification from {} {} (reason: {})", vendorName, model, reason);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("currentTime", Instant.now().toString());
        response.put("interval", 300);
        response.put("status", "Accepted");

        return buildCallResult(messageId, response);
    }

    private String handleHeartbeat(String messageId) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("currentTime", Instant.now().toString());

        return buildCallResult(messageId, response);
    }

    private String handleStatusNotification(String messageId, JsonNode payload) {
        // OCPP 2.0.1: timestamp, connectorStatus, evseId, connectorId
        String status = payload.path("connectorStatus").asText("Unknown");
        int evseId = payload.path("evseId").asInt(1);
        int connectorId = payload.path("connectorId").asInt(1);

        log.info("[OCPP201] StatusNotification: EVSE {} Connector {} -> {}", evseId, connectorId, status);

        // Réponse vide pour StatusNotification
        return buildCallResult(messageId, objectMapper.createObjectNode());
    }

    private String handleTransactionEvent(String messageId, JsonNode payload) {
        // OCPP 2.0.1: eventType, triggerReason, seqNo, transactionInfo
        String eventType = payload.path("eventType").asText("Unknown");
        String triggerReason = payload.path("triggerReason").asText("Unknown");
        int seqNo = payload.path("seqNo").asInt(0);

        JsonNode txInfo = payload.get("transactionInfo");
        String transactionId = txInfo != null ? txInfo.path("transactionId").asText("unknown") : "unknown";

        log.info("[OCPP201] TransactionEvent: {} (trigger: {}, seqNo: {}, txId: {})",
                eventType, triggerReason, seqNo, transactionId);

        ObjectNode response = objectMapper.createObjectNode();

        // Réponse selon le type d'événement
        if ("Started".equals(eventType)) {
            response.put("totalCost", 0);
            response.put("chargingPriority", 0);
        } else if ("Ended".equals(eventType)) {
            response.put("totalCost", 0);
        }

        return buildCallResult(messageId, response);
    }

    private String handleMeterValues(String messageId, JsonNode payload) {
        // OCPP 2.0.1: evseId, meterValue[]
        int evseId = payload.path("evseId").asInt(1);
        JsonNode meterValues = payload.get("meterValue");

        if (meterValues != null && meterValues.isArray() && meterValues.size() > 0) {
            JsonNode firstValue = meterValues.get(0);
            JsonNode sampledValues = firstValue.get("sampledValue");
            if (sampledValues != null && sampledValues.isArray()) {
                log.debug("[OCPP201] MeterValues EVSE {}: {} values", evseId, sampledValues.size());
            }
        }

        // Réponse vide pour MeterValues
        return buildCallResult(messageId, objectMapper.createObjectNode());
    }

    private String handleAuthorize(String messageId, JsonNode payload) {
        // OCPP 2.0.1: idToken {idToken, type}
        JsonNode idToken = payload.get("idToken");
        String tokenId = idToken != null ? idToken.path("idToken").asText("unknown") : "unknown";
        String tokenType = idToken != null ? idToken.path("type").asText("ISO14443") : "ISO14443";

        log.info("[OCPP201] Authorize request: {} (type: {})", tokenId, tokenType);

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode idTokenInfo = objectMapper.createObjectNode();
        idTokenInfo.put("status", "Accepted");
        response.set("idTokenInfo", idTokenInfo);

        return buildCallResult(messageId, response);
    }

    // =========================================================================
    // Build Request Messages
    // =========================================================================

    public String buildBootNotificationRequest(String vendorName, String model, String serialNumber) {
        String messageId = UUID.randomUUID().toString();

        ObjectNode chargingStation = objectMapper.createObjectNode();
        chargingStation.put("vendorName", vendorName);
        chargingStation.put("model", model);
        if (serialNumber != null) {
            chargingStation.put("serialNumber", serialNumber);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("chargingStation", chargingStation);
        payload.put("reason", "PowerUp");

        return buildCall(messageId, "BootNotification", payload);
    }

    public String buildHeartbeatRequest() {
        String messageId = UUID.randomUUID().toString();
        return buildCall(messageId, "Heartbeat", objectMapper.createObjectNode());
    }

    public String buildStatusNotificationRequest(int evseId, int connectorId, String status) {
        String messageId = UUID.randomUUID().toString();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("timestamp", Instant.now().toString());
        payload.put("connectorStatus", status);
        payload.put("evseId", evseId);
        payload.put("connectorId", connectorId);

        return buildCall(messageId, "StatusNotification", payload);
    }

    public String buildTransactionEventRequest(String eventType, String triggerReason,
                                                int seqNo, String transactionId, int evseId) {
        String messageId = UUID.randomUUID().toString();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("eventType", eventType);
        payload.put("timestamp", Instant.now().toString());
        payload.put("triggerReason", triggerReason);
        payload.put("seqNo", seqNo);

        ObjectNode txInfo = objectMapper.createObjectNode();
        txInfo.put("transactionId", transactionId);
        payload.set("transactionInfo", txInfo);

        ObjectNode evse = objectMapper.createObjectNode();
        evse.put("id", evseId);
        payload.set("evse", evse);

        return buildCall(messageId, "TransactionEvent", payload);
    }

    public String buildMeterValuesRequest(int evseId, Map<String, Double> values) {
        String messageId = UUID.randomUUID().toString();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("evseId", evseId);

        var meterValues = objectMapper.createArrayNode();
        var meterValue = objectMapper.createObjectNode();
        meterValue.put("timestamp", Instant.now().toString());

        var sampledValues = objectMapper.createArrayNode();
        values.forEach((measurand, value) -> {
            var sv = objectMapper.createObjectNode();
            sv.put("value", value);
            sv.put("measurand", measurand);
            sampledValues.add(sv);
        });
        meterValue.set("sampledValue", sampledValues);
        meterValues.add(meterValue);
        payload.set("meterValue", meterValues);

        return buildCall(messageId, "MeterValues", payload);
    }

    public String buildAuthorizeRequest(String idToken, String type) {
        String messageId = UUID.randomUUID().toString();

        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode token = objectMapper.createObjectNode();
        token.put("idToken", idToken);
        token.put("type", type != null ? type : "ISO14443");
        payload.set("idToken", token);

        return buildCall(messageId, "Authorize", payload);
    }

    // =========================================================================
    // Message Builders
    // =========================================================================

    private String buildCall(String messageId, String action, JsonNode payload) {
        try {
            var arr = objectMapper.createArrayNode();
            arr.add(CALL);
            arr.add(messageId);
            arr.add(action);
            arr.add(payload);
            return objectMapper.writeValueAsString(arr);
        } catch (Exception e) {
            log.error("[OCPP201] Error building CALL: {}", e.getMessage());
            return null;
        }
    }

    private String buildCallResult(String messageId, JsonNode payload) {
        try {
            var arr = objectMapper.createArrayNode();
            arr.add(CALL_RESULT);
            arr.add(messageId);
            arr.add(payload);
            return objectMapper.writeValueAsString(arr);
        } catch (Exception e) {
            log.error("[OCPP201] Error building CALL_RESULT: {}", e.getMessage());
            return null;
        }
    }

    private String buildCallError(String messageId, String errorCode, String errorDescription) {
        try {
            var arr = objectMapper.createArrayNode();
            arr.add(CALL_ERROR);
            arr.add(messageId);
            arr.add(errorCode);
            arr.add(errorDescription);
            arr.add(objectMapper.createObjectNode());
            return objectMapper.writeValueAsString(arr);
        } catch (Exception e) {
            log.error("[OCPP201] Error building CALL_ERROR: {}", e.getMessage());
            return null;
        }
    }
}
