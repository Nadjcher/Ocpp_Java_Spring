package com.evse.simulator.tnr.model;

import lombok.*;

/**
 * Tolérances configurables pour la validation TNR.
 * <p>
 * Permet de définir des marges d'erreur acceptables pour différents types de mesures.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrTolerances {

    /** Tolérance pour le SOC en pourcentage (ex: 2 = ±2%) */
    @Builder.Default
    private double socPercent = 2.0;

    /** Tolérance pour l'énergie en kWh (ex: 0.5 = ±0.5 kWh) */
    @Builder.Default
    private double energyKwh = 0.5;

    /** Tolérance pour la puissance en kW (ex: 0.5 = ±0.5 kW) */
    @Builder.Default
    private double powerKw = 0.5;

    /** Tolérance pour la durée en secondes (ex: 60 = ±60 secondes) */
    @Builder.Default
    private int durationSec = 60;

    /** Tolérance pour les timestamps en millisecondes (ex: 2000 = ±2 secondes) */
    @Builder.Default
    private int timestampMs = 2000;

    /** Tolérance pour la tension en volts (ex: 5 = ±5V) */
    @Builder.Default
    private double voltageV = 5.0;

    /** Tolérance pour le courant en ampères (ex: 0.5 = ±0.5A) */
    @Builder.Default
    private double currentA = 0.5;

    /** Tolérance pour la latence OCPP en millisecondes */
    @Builder.Default
    private int latencyMs = 500;

    /**
     * Retourne les tolérances par défaut.
     */
    public static TnrTolerances defaults() {
        return TnrTolerances.builder().build();
    }

    /**
     * Retourne des tolérances strictes (faibles marges).
     */
    public static TnrTolerances strict() {
        return TnrTolerances.builder()
                .socPercent(1.0)
                .energyKwh(0.1)
                .powerKw(0.1)
                .durationSec(10)
                .timestampMs(500)
                .voltageV(2.0)
                .currentA(0.2)
                .latencyMs(200)
                .build();
    }

    /**
     * Retourne des tolérances souples (grandes marges).
     */
    public static TnrTolerances relaxed() {
        return TnrTolerances.builder()
                .socPercent(5.0)
                .energyKwh(1.0)
                .powerKw(1.0)
                .durationSec(120)
                .timestampMs(5000)
                .voltageV(10.0)
                .currentA(1.0)
                .latencyMs(1000)
                .build();
    }

    /**
     * Vérifie si une valeur SOC est dans la tolérance.
     */
    public boolean isWithinSocTolerance(double expected, double actual) {
        return Math.abs(expected - actual) <= socPercent;
    }

    /**
     * Vérifie si une valeur d'énergie est dans la tolérance.
     */
    public boolean isWithinEnergyTolerance(double expected, double actual) {
        return Math.abs(expected - actual) <= energyKwh;
    }

    /**
     * Vérifie si une valeur de puissance est dans la tolérance.
     */
    public boolean isWithinPowerTolerance(double expected, double actual) {
        return Math.abs(expected - actual) <= powerKw;
    }

    /**
     * Vérifie si une durée est dans la tolérance.
     */
    public boolean isWithinDurationTolerance(long expectedSec, long actualSec) {
        return Math.abs(expectedSec - actualSec) <= durationSec;
    }

    /**
     * Vérifie si un timestamp est dans la tolérance.
     */
    public boolean isWithinTimestampTolerance(long expectedMs, long actualMs) {
        return Math.abs(expectedMs - actualMs) <= timestampMs;
    }

    /**
     * Vérifie si une valeur numérique est dans une tolérance donnée.
     */
    public static boolean isWithinTolerance(double expected, double actual, double tolerance) {
        return Math.abs(expected - actual) <= tolerance;
    }
}
