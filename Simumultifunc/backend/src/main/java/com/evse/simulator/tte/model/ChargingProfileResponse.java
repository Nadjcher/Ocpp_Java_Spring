package com.evse.simulator.tte.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Réponse après envoi d'un profil de charge.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChargingProfileResponse {

    /**
     * ID du profil créé/modifié
     */
    private Integer chargingProfileId;

    /**
     * ID du Charge Point
     */
    private String chargePointId;

    /**
     * ID du connecteur
     */
    private int connectorId;

    /**
     * Statut: Accepted, Rejected, NotSupported
     */
    private String status;

    /**
     * Message de statut
     */
    private String statusMessage;

    /**
     * But du profil
     */
    private String chargingProfilePurpose;

    /**
     * Niveau de stack
     */
    private int stackLevel;

    /**
     * Date de création
     */
    private Instant createdAt;

    /**
     * Date de mise à jour
     */
    private Instant updatedAt;

    /**
     * Limite actuelle (W ou A)
     */
    private Double currentLimit;

    /**
     * Unité de la limite
     */
    private String limitUnit;

    /**
     * Vérifie si le profil a été accepté
     */
    public boolean isAccepted() {
        return "Accepted".equalsIgnoreCase(status);
    }
}
