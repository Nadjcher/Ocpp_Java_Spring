package com.evse.simulator.model;

import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.model.enums.OCPPMessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Représente un message OCPP 1.6.
 * <p>
 * Encapsule les informations d'un message OCPP-J (JSON over WebSocket).
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OCPPMessage {

    /**
     * Identifiant unique du message.
     */
    @Builder.Default
    private String messageId = UUID.randomUUID().toString();

    /**
     * Type de message (CALL, CALLRESULT, CALLERROR).
     */
    private OCPPMessageType messageType;

    /**
     * Action OCPP (pour les CALL uniquement).
     */
    private OCPPAction action;

    /**
     * Nom de l'action (chaîne brute).
     */
    private String actionName;

    /**
     * Payload du message (JSON).
     */
    private Map<String, Object> payload;

    /**
     * Message brut WebSocket.
     */
    private String rawMessage;

    /**
     * Direction du message.
     */
    @Builder.Default
    private Direction direction = Direction.OUTGOING;

    /**
     * Horodatage du message.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * ID de session associée.
     */
    private String sessionId;

    /**
     * Code d'erreur (pour CALLERROR).
     */
    private String errorCode;

    /**
     * Description d'erreur (pour CALLERROR).
     */
    private String errorDescription;

    /**
     * Détails d'erreur (pour CALLERROR).
     */
    private Map<String, Object> errorDetails;

    /**
     * Durée de traitement en ms.
     */
    private Long processingTimeMs;

    /**
     * Statut de la réponse.
     */
    private ResponseStatus responseStatus;

    /**
     * Direction du message.
     */
    public enum Direction {
        /**
         * Message sortant (vers le CSMS).
         */
        OUTGOING,

        /**
         * Message entrant (du CSMS).
         */
        INCOMING
    }

    /**
     * Statut de la réponse.
     */
    public enum ResponseStatus {
        /**
         * En attente de réponse.
         */
        PENDING,

        /**
         * Réponse reçue avec succès.
         */
        ACCEPTED,

        /**
         * Réponse reçue avec rejet.
         */
        REJECTED,

        /**
         * Timeout.
         */
        TIMEOUT,

        /**
         * Erreur.
         */
        ERROR
    }

    /**
     * Crée un message CALL.
     */
    public static OCPPMessage createCall(OCPPAction action, Map<String, Object> payload) {
        return OCPPMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .messageType(OCPPMessageType.CALL)
                .action(action)
                .actionName(action.getValue())
                .payload(payload)
                .direction(Direction.OUTGOING)
                .responseStatus(ResponseStatus.PENDING)
                .build();
    }

    /**
     * Crée un message CALLRESULT.
     */
    public static OCPPMessage createCallResult(String messageId, Map<String, Object> payload) {
        return OCPPMessage.builder()
                .messageId(messageId)
                .messageType(OCPPMessageType.CALL_RESULT)
                .payload(payload)
                .direction(Direction.OUTGOING)
                .build();
    }

    /**
     * Crée un message CALLERROR.
     */
    public static OCPPMessage createCallError(String messageId, String errorCode,
                                               String errorDescription, Map<String, Object> errorDetails) {
        return OCPPMessage.builder()
                .messageId(messageId)
                .messageType(OCPPMessageType.CALL_ERROR)
                .errorCode(errorCode)
                .errorDescription(errorDescription)
                .errorDetails(errorDetails)
                .direction(Direction.OUTGOING)
                .build();
    }

    /**
     * Formate le message en JSON OCPP-J.
     */
    public String toOcppJson() {
        StringBuilder sb = new StringBuilder("[");

        switch (messageType) {
            case CALL:
                sb.append(messageType.getTypeId()).append(",\"")
                        .append(messageId).append("\",\"")
                        .append(actionName).append("\",")
                        .append(payloadToJson());
                break;

            case CALL_RESULT:
                sb.append(messageType.getTypeId()).append(",\"")
                        .append(messageId).append("\",")
                        .append(payloadToJson());
                break;

            case CALL_ERROR:
                sb.append(messageType.getTypeId()).append(",\"")
                        .append(messageId).append("\",\"")
                        .append(errorCode != null ? errorCode : "GenericError").append("\",\"")
                        .append(errorDescription != null ? errorDescription : "").append("\",")
                        .append(errorDetails != null ? payloadToJson(errorDetails) : "{}");
                break;
        }

        sb.append("]");
        return sb.toString();
    }

    private String payloadToJson() {
        return payloadToJson(payload);
    }

    private String payloadToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Crée un résumé du message.
     */
    public String toSummary() {
        String dir = direction == Direction.INCOMING ? "←" : "→";
        String type = messageType.getName();
        String act = actionName != null ? actionName : (action != null ? action.getValue() : "");

        return String.format("%s %s %s [%s]", dir, type, act, messageId.substring(0, 8));
    }
}