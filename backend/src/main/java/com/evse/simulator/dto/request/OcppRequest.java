package com.evse.simulator.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Requête OCPP générique.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcppRequest {

    /**
     * Action OCPP à exécuter.
     */
    private String action;

    /**
     * Tag RFID (pour Authorize, StartTransaction).
     */
    private String idTag;

    /**
     * Statut du connecteur (pour StatusNotification).
     */
    private String status;

    /**
     * Payload personnalisé.
     */
    private Map<String, Object> payload;
}