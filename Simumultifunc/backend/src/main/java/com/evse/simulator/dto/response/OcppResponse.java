package com.evse.simulator.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Réponse à une requête OCPP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcppResponse {

    /**
     * Action OCPP exécutée.
     */
    private String action;

    /**
     * ID du message OCPP.
     */
    private String messageId;

    /**
     * Succès de l'opération.
     */
    @Builder.Default
    private boolean success = true;

    /**
     * Statut de la réponse (Accepted, Rejected, etc.).
     */
    private String status;

    /**
     * Message d'erreur (si échec).
     */
    private String error;

    /**
     * Payload de la réponse du CSMS.
     */
    private Map<String, Object> payload;

    /**
     * ID de la transaction (pour StartTransaction).
     */
    private Integer transactionId;

    /**
     * Heure du CSMS (pour BootNotification, Heartbeat).
     */
    private LocalDateTime currentTime;

    /**
     * Intervalle heartbeat (pour BootNotification).
     */
    private Integer heartbeatInterval;

    /**
     * Horodatage de la réponse.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Crée une réponse de succès.
     */
    public static OcppResponse success(String action, Map<String, Object> payload) {
        return OcppResponse.builder()
                .action(action)
                .success(true)
                .status("Accepted")
                .payload(payload)
                .build();
    }

    /**
     * Crée une réponse d'erreur.
     */
    public static OcppResponse error(String action, String error) {
        return OcppResponse.builder()
                .action(action)
                .success(false)
                .status("Rejected")
                .error(error)
                .build();
    }
}