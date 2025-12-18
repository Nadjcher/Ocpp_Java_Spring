package com.evse.simulator.model;

import lombok.Data;

import java.util.Map;

/**
 * Enregistrement TNR brut.
 */
@Data
public class TNRRecord {
    /**
     * Timestamp de l'enregistrement.
     */
    private long timestamp;

    /**
     * ID de la session.
     */
    private String sessionId;

    /**
     * Type d'événement.
     */
    private String eventType;

    /**
     * Données associées.
     */
    private Map<String, Object> data;
}