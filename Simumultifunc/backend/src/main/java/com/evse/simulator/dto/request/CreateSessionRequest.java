package com.evse.simulator.dto.request;

import com.evse.simulator.model.enums.ChargerType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Requête de création de session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {

    /**
     * Titre de la session (optionnel).
     */
    private String title;

    /**
     * URL du CSMS.
     */
    @NotBlank(message = "L'URL du CSMS est obligatoire")
    private String url;

    /**
     * Identifiant du Charge Point.
     */
    @NotBlank(message = "L'ID du Charge Point est obligatoire")
    private String cpId;

    /**
     * Token d'authentification Bearer (optionnel).
     */
    private String bearerToken;

    /**
     * ID du profil véhicule à utiliser (optionnel).
     */
    private String vehicleProfileId;

    /**
     * Type de chargeur.
     */
    @Builder.Default
    private ChargerType chargerType = ChargerType.AC_TRI;

    /**
     * Tag RFID pour l'autorisation.
     */
    @Builder.Default
    private String idTag = "EVSE001";

    /**
     * État de charge initial (%).
     */
    @Min(0)
    @Max(100)
    @Builder.Default
    private double soc = 20.0;

    /**
     * État de charge cible (%).
     */
    @Min(0)
    @Max(100)
    @Builder.Default
    private double targetSoc = 80.0;

    /**
     * Intervalle heartbeat en secondes.
     */
    @Min(1)
    @Builder.Default
    private int heartbeatInterval = 30;

    /**
     * Intervalle MeterValues en secondes.
     */
    @Min(1)
    @Builder.Default
    private int meterValuesInterval = 10;
}