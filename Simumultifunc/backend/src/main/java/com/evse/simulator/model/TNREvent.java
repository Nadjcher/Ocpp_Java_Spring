package com.evse.simulator.model;

import lombok.Data;

/**
 * Représente un événement enregistré pendant une exécution TNR.
 */
@Data
public class TNREvent {
    /**
     * Timestamp de l'événement.
     */
    private Long timestamp;

    /**
     * ID de la session associée.
     */
    private String sessionId;

    /**
     * Type d'événement : connect, disconnect, authorize, startTransaction, etc.
     */
    private String type;

    /**
     * Action OCPP ou autre.
     */
    private String action;

    /**
     * Payload de l'événement.
     */
    private Object payload;

    /**
     * Latence observée en ms.
     */
    private Long latency;
}