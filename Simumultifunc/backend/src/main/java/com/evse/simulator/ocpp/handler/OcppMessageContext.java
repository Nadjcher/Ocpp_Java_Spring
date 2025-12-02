package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.Session;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Contexte pour la construction et le traitement des messages OCPP.
 */
@Data
@Builder
public class OcppMessageContext {

    /**
     * ID de la session.
     */
    private String sessionId;

    /**
     * Session du simulateur.
     */
    private Session session;

    /**
     * ID du connecteur.
     */
    @Builder.Default
    private int connectorId = 1;

    /**
     * ID de la transaction.
     */
    private Integer transactionId;

    /**
     * Tag RFID.
     */
    private String idTag;

    /**
     * Valeur du compteur (Wh).
     */
    private Long meterValue;

    /**
     * Raison d'arrêt de transaction.
     */
    private String stopReason;

    /**
     * Statut du connecteur.
     */
    private String status;

    /**
     * Code d'erreur.
     */
    private String errorCode;

    /**
     * Informations vendeur.
     */
    private String vendorInfo;

    /**
     * Données supplémentaires.
     */
    private Map<String, Object> additionalData;
}
