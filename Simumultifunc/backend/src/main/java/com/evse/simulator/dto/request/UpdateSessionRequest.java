package com.evse.simulator.dto.request;

import com.evse.simulator.model.enums.ChargerType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Requête de mise à jour de session.
 * Tous les champs sont optionnels - seuls les champs fournis sont mis à jour.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSessionRequest {

    /**
     * Titre de la session.
     */
    private String title;

    /**
     * URL du CSMS.
     */
    private String url;

    /**
     * Identifiant du Charge Point.
     */
    private String cpId;

    /**
     * Token d'authentification Bearer.
     */
    private String bearerToken;

    /**
     * ID du profil véhicule.
     */
    private String vehicleProfileId;

    /**
     * Type de chargeur.
     */
    private ChargerType chargerType;

    /**
     * Tag RFID.
     */
    private String idTag;

    /**
     * État de charge actuel (%).
     */
    @Min(0)
    @Max(100)
    private Double soc;

    /**
     * État de charge cible (%).
     */
    @Min(0)
    @Max(100)
    private Double targetSoc;

    /**
     * Intervalle heartbeat en secondes.
     */
    @Min(1)
    private Integer heartbeatInterval;

    /**
     * Intervalle MeterValues en secondes.
     */
    @Min(1)
    private Integer meterValuesInterval;
}
