package com.evse.simulator.tnr.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Capture d'un message OCPP échangé pendant un test TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcppMessageCapture {

    /**
     * Direction du message.
     */
    public enum Direction {
        OUTBOUND,   // CP → CSMS
        INBOUND     // CSMS → CP
    }

    /**
     * Type de message OCPP.
     */
    public enum MessageType {
        CALL,       // [2, messageId, action, payload]
        CALLRESULT, // [3, messageId, payload]
        CALLERROR   // [4, messageId, errorCode, errorDescription, errorDetails]
    }

    /** Direction */
    private Direction direction;

    /** Type de message */
    private MessageType messageType;

    /** ID du message */
    private String messageId;

    /** Action OCPP (pour CALL) */
    private String action;

    /** Payload JSON */
    private JsonNode payload;

    /** Message brut JSON */
    private String rawMessage;

    /** Timestamp d'envoi/réception */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** Latence en ms (pour les réponses) */
    private Long latencyMs;

    /** Code d'erreur (pour CALLERROR) */
    private String errorCode;

    /** Description d'erreur (pour CALLERROR) */
    private String errorDescription;

    /** Session ID */
    private String sessionId;

    /** Connector ID */
    private Integer connectorId;

    /** Transaction ID (si applicable) */
    private Integer transactionId;

    /**
     * Vérifie si c'est un message sortant.
     */
    public boolean isOutbound() {
        return direction == Direction.OUTBOUND;
    }

    /**
     * Vérifie si c'est un message entrant.
     */
    public boolean isInbound() {
        return direction == Direction.INBOUND;
    }

    /**
     * Vérifie si c'est un appel.
     */
    public boolean isCall() {
        return messageType == MessageType.CALL;
    }

    /**
     * Vérifie si c'est une réponse.
     */
    public boolean isResult() {
        return messageType == MessageType.CALLRESULT;
    }

    /**
     * Vérifie si c'est une erreur.
     */
    public boolean isError() {
        return messageType == MessageType.CALLERROR;
    }

    /**
     * Crée une capture pour un CALL sortant.
     */
    public static OcppMessageCapture outboundCall(String messageId, String action, JsonNode payload, String raw) {
        return OcppMessageCapture.builder()
            .direction(Direction.OUTBOUND)
            .messageType(MessageType.CALL)
            .messageId(messageId)
            .action(action)
            .payload(payload)
            .rawMessage(raw)
            .build();
    }

    /**
     * Crée une capture pour un CALLRESULT entrant.
     */
    public static OcppMessageCapture inboundResult(String messageId, JsonNode payload, String raw, long latencyMs) {
        return OcppMessageCapture.builder()
            .direction(Direction.INBOUND)
            .messageType(MessageType.CALLRESULT)
            .messageId(messageId)
            .payload(payload)
            .rawMessage(raw)
            .latencyMs(latencyMs)
            .build();
    }

    /**
     * Crée une capture pour un CALL entrant.
     */
    public static OcppMessageCapture inboundCall(String messageId, String action, JsonNode payload, String raw) {
        return OcppMessageCapture.builder()
            .direction(Direction.INBOUND)
            .messageType(MessageType.CALL)
            .messageId(messageId)
            .action(action)
            .payload(payload)
            .rawMessage(raw)
            .build();
    }
}
