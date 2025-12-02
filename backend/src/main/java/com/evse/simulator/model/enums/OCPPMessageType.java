package com.evse.simulator.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Types de messages OCPP 1.6.
 * <p>
 * Définit les types de trames WebSocket selon la spécification OCPP-J.
 * </p>
 */
public enum OCPPMessageType {

    /**
     * Message de requête (CALL).
     * Format: [2, "messageId", "action", {payload}]
     */
    CALL(2, "CALL"),

    /**
     * Message de réponse (CALLRESULT).
     * Format: [3, "messageId", {payload}]
     */
    CALL_RESULT(3, "CALLRESULT"),

    /**
     * Message d'erreur (CALLERROR).
     * Format: [4, "messageId", "errorCode", "errorDescription", {errorDetails}]
     */
    CALL_ERROR(4, "CALLERROR");

    private final int typeId;
    private final String name;

    OCPPMessageType(int typeId, String name) {
        this.typeId = typeId;
        this.name = name;
    }

    /**
     * Identifiant numérique du type.
     */
    @JsonValue
    public int getTypeId() {
        return typeId;
    }

    /**
     * Nom du type de message.
     */
    public String getName() {
        return name;
    }

    /**
     * Convertit un identifiant en OCPPMessageType.
     */
    public static OCPPMessageType fromTypeId(int typeId) {
        for (OCPPMessageType type : values()) {
            if (type.typeId == typeId) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OCPP message type: " + typeId);
    }

    @Override
    public String toString() {
        return name + " (" + typeId + ")";
    }
}