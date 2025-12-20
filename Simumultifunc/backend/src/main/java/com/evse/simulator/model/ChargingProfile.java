package com.evse.simulator.model;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Profil de charge intelligent (Smart Charging) OCPP 1.6.
 * <p>
 * Définit les limites de puissance et le planning de charge
 * selon la spécification SetChargingProfile.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(hidden = true)
public class ChargingProfile {

    /**
     * Identifiant unique du profil.
     */
    @Min(0)
    private int chargingProfileId;

    /**
     * Identifiant de la transaction (optionnel).
     */
    private Integer transactionId;

    /**
     * Niveau de priorité du profil (stack level).
     */
    @Builder.Default
    @Min(0)
    private int stackLevel = 0;

    /**
     * But du profil.
     */
    @Builder.Default
    private ChargingProfilePurpose chargingProfilePurpose = ChargingProfilePurpose.TX_DEFAULT_PROFILE;

    /**
     * Type de profil (absolu, relatif, récurrent).
     */
    @Builder.Default
    private ChargingProfileKind chargingProfileKind = ChargingProfileKind.ABSOLUTE;

    /**
     * Type de récurrence.
     */
    private RecurrencyKind recurrencyKind;

    /**
     * Date/heure de début de validité.
     */
    private LocalDateTime validFrom;

    /**
     * Date/heure de fin de validité.
     */
    private LocalDateTime validTo;

    /**
     * Planning de charge.
     */
    private ChargingSchedule chargingSchedule;

    // =========================================================================
    // Métadonnées (ajoutées lors de l'application du profil)
    // =========================================================================

    /**
     * ID de la session associée.
     */
    private String sessionId;

    /**
     * ID du connecteur.
     */
    private Integer connectorId;

    /**
     * Timestamp d'application du profil.
     */
    private LocalDateTime appliedAt;

    /**
     * Timestamp de début effectif (pour profils Relative).
     */
    private LocalDateTime effectiveStartTime;

    /**
     * But du profil de charge.
     */
    public enum ChargingProfilePurpose {
        /**
         * Limite de puissance maximale du Charge Point.
         */
        CHARGE_POINT_MAX_PROFILE("ChargePointMaxProfile"),

        /**
         * Profil par défaut pour les transactions.
         */
        TX_DEFAULT_PROFILE("TxDefaultProfile"),

        /**
         * Profil spécifique à une transaction.
         */
        TX_PROFILE("TxProfile");

        private final String value;

        ChargingProfilePurpose(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public static ChargingProfilePurpose fromValue(String value) {
            for (ChargingProfilePurpose p : values()) {
                if (p.value.equalsIgnoreCase(value)) {
                    return p;
                }
            }
            return TX_DEFAULT_PROFILE;
        }
    }

    /**
     * Type de profil de charge.
     */
    public enum ChargingProfileKind {
        /**
         * Planning basé sur des horaires absolus.
         */
        ABSOLUTE("Absolute"),

        /**
         * Planning relatif au début de la charge.
         */
        RELATIVE("Relative"),

        /**
         * Planning récurrent.
         */
        RECURRING("Recurring");

        private final String value;

        ChargingProfileKind(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public static ChargingProfileKind fromValue(String value) {
            for (ChargingProfileKind k : values()) {
                if (k.value.equalsIgnoreCase(value)) {
                    return k;
                }
            }
            return ABSOLUTE;
        }
    }

    /**
     * Type de récurrence.
     */
    public enum RecurrencyKind {
        DAILY("Daily"),
        WEEKLY("Weekly");

        private final String value;

        RecurrencyKind(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    /**
     * Planning de charge.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChargingSchedule {

        /**
         * Durée du planning en secondes.
         */
        private Integer duration;

        /**
         * Date/heure de début du planning.
         */
        private LocalDateTime startSchedule;

        /**
         * Unité de charge (A ou W).
         */
        @Builder.Default
        private ChargingRateUnit chargingRateUnit = ChargingRateUnit.A;

        /**
         * Limite minimale de charge.
         */
        private Double minChargingRate;

        /**
         * Périodes de charge.
         */
        private List<ChargingSchedulePeriod> chargingSchedulePeriod;
    }

    /**
     * Période dans le planning de charge.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChargingSchedulePeriod {

        /**
         * Décalage en secondes depuis le début du planning.
         */
        @Min(0)
        private int startPeriod;

        /**
         * Limite de charge (A ou W selon l'unité).
         */
        @Min(0)
        private double limit;

        /**
         * Nombre de phases (optionnel).
         */
        private Integer numberPhases;
    }

    /**
     * Unité de mesure pour les limites de charge.
     */
    public enum ChargingRateUnit {
        /**
         * Ampères.
         */
        A("A"),

        /**
         * Watts.
         */
        W("W");

        private final String value;

        ChargingRateUnit(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public static ChargingRateUnit fromValue(String value) {
            return "W".equalsIgnoreCase(value) ? W : A;
        }
    }

    /**
     * Calcule la limite de puissance actuelle en kW.
     *
     * @param voltage tension en Volts
     * @param phases nombre de phases
     * @return limite en kW
     */
    public double getCurrentLimitKw(double voltage, int phases) {
        if (chargingSchedule == null || chargingSchedule.getChargingSchedulePeriod() == null) {
            return Double.MAX_VALUE;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduleStart = chargingSchedule.getStartSchedule();
        if (scheduleStart == null) {
            scheduleStart = now;
        }

        long elapsedSeconds = java.time.Duration.between(scheduleStart, now).getSeconds();
        if (elapsedSeconds < 0) {
            return Double.MAX_VALUE;
        }

        // Trouver la période active
        ChargingSchedulePeriod activePeriod = null;
        for (ChargingSchedulePeriod period : chargingSchedule.getChargingSchedulePeriod()) {
            if (period.getStartPeriod() <= elapsedSeconds) {
                activePeriod = period;
            } else {
                break;
            }
        }

        if (activePeriod == null) {
            return Double.MAX_VALUE;
        }

        double limit = activePeriod.getLimit();
        int numPhases = activePeriod.getNumberPhases() != null ?
                activePeriod.getNumberPhases() : phases;

        // Conversion selon l'unité
        if (chargingSchedule.getChargingRateUnit() == ChargingRateUnit.A) {
            // P = U * I * sqrt(3) pour triphasé
            double factor = numPhases == 1 ? 1.0 : (numPhases == 2 ? 2.0 : Math.sqrt(3));
            return (voltage * limit * factor) / 1000.0;
        } else {
            // Déjà en Watts
            return limit / 1000.0;
        }
    }
}