package com.evse.simulator.tte.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Données de tarification d'une session de charge.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PricingData {

    /**
     * ID de la session (interne simulateur)
     */
    private String sessionId;

    /**
     * ID du Charge Point (OCPP ID)
     */
    private String chargePointId;

    /**
     * ID de la transaction OCPP
     */
    private Integer transactionId;

    /**
     * Prix total de la session
     */
    private BigDecimal totalPrice;

    /**
     * Devise (EUR, USD, etc.)
     */
    private String currency;

    /**
     * Prix par kWh
     */
    private BigDecimal pricePerKwh;

    /**
     * Énergie totale délivrée (kWh)
     */
    private BigDecimal energyDelivered;

    /**
     * Durée de la session en secondes
     */
    private Long durationSeconds;

    /**
     * Prix par minute (si applicable)
     */
    private BigDecimal pricePerMinute;

    /**
     * Frais fixes (si applicable)
     */
    private BigDecimal fixedFee;

    /**
     * Détails par période (si tarification dynamique)
     */
    private List<PricingPeriod> periods;

    /**
     * Horodatage du calcul
     */
    private Instant calculatedAt;

    /**
     * Période de tarification
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PricingPeriod {
        private Instant startTime;
        private Instant endTime;
        private BigDecimal pricePerKwh;
        private BigDecimal energyKwh;
        private BigDecimal subtotal;
    }
}
