package com.evse.simulator.tte.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Requête pour créer/modifier un profil de charge.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChargingProfileRequest {

    /**
     * ID du Charge Point
     */
    private String chargePointId;

    /**
     * ID du connecteur (0 = tous)
     */
    @Builder.Default
    private int connectorId = 1;

    /**
     * ID du profil (optionnel, généré si absent)
     */
    private Integer chargingProfileId;

    /**
     * ID de la transaction (pour TxProfile)
     */
    private Integer transactionId;

    /**
     * Niveau de stack (priorité)
     */
    @Builder.Default
    private int stackLevel = 0;

    /**
     * But du profil: ChargePointMaxProfile, TxDefaultProfile, TxProfile
     */
    @Builder.Default
    private String chargingProfilePurpose = "TxDefaultProfile";

    /**
     * Type: Absolute, Recurring, Relative
     */
    @Builder.Default
    private String chargingProfileKind = "Absolute";

    /**
     * Type de récurrence (si Recurring): Daily, Weekly
     */
    private String recurrencyKind;

    /**
     * Date de validité début
     */
    private Instant validFrom;

    /**
     * Date de validité fin
     */
    private Instant validTo;

    /**
     * Schedule de charge
     */
    private ChargingSchedule chargingSchedule;

    /**
     * Schedule de charge OCPP 1.6
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChargingSchedule {
        /**
         * Durée en secondes
         */
        private Integer duration;

        /**
         * Date de début (optionnel)
         */
        private Instant startSchedule;

        /**
         * Unité: W (Watts) ou A (Ampères)
         */
        @Builder.Default
        private String chargingRateUnit = "W";

        /**
         * Limite minimale (optionnel)
         */
        private Double minChargingRate;

        /**
         * Périodes de charge
         */
        private List<ChargingSchedulePeriod> chargingSchedulePeriod;
    }

    /**
     * Période de charge
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChargingSchedulePeriod {
        /**
         * Offset en secondes depuis le début
         */
        private int startPeriod;

        /**
         * Limite de puissance/courant
         */
        private double limit;

        /**
         * Nombre de phases (optionnel)
         */
        private Integer numberPhases;
    }
}
