package com.evse.simulator.tnr.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validateur de cohérence énergétique pour les sessions de charge.
 * <p>
 * Vérifie que l'énergie mesurée est cohérente avec la puissance
 * et la durée de charge selon la formule: E = P × t
 * </p>
 *
 * @example
 * <pre>
 * EnergyValidator validator = new EnergyValidator();
 *
 * // Vérifie E = P × t avec tolérance de 10%
 * boolean isValid = validator.validateEnergyConsistency(
 *     11000,    // Puissance moyenne en W
 *     3600,     // Durée en secondes (1h)
 *     11000,    // Énergie mesurée en Wh
 *     10.0      // Tolérance en %
 * );
 * // Expected: 11000 * 3600 / 3600 = 11000 Wh ✓
 * </pre>
 */
@Slf4j
@Component
public class EnergyValidator {

    // Constantes physiques
    private static final double SECONDS_PER_HOUR = 3600.0;
    private static final double DEFAULT_TOLERANCE_PERCENT = 5.0;

    /**
     * Vérifie la cohérence entre puissance, durée et énergie.
     * Formule: expectedWh = powerW × durationSec / 3600
     *
     * @param powerW           Puissance moyenne en Watts
     * @param durationSec      Durée en secondes
     * @param actualEnergyWh   Énergie mesurée en Wh
     * @param tolerancePercent Tolérance acceptée en %
     * @return true si cohérent
     */
    public boolean validateEnergyConsistency(double powerW, long durationSec,
                                              double actualEnergyWh, double tolerancePercent) {
        double expectedWh = calculateExpectedEnergy(powerW, durationSec);
        double deviation = calculateDeviation(expectedWh, actualEnergyWh);

        boolean valid = deviation <= tolerancePercent;

        log.debug("[ENERGY] Validation: P={}W, t={}s, expected={:.2f}Wh, actual={:.2f}Wh, " +
                        "deviation={:.2f}%, tolerance={:.2f}% -> {}",
                powerW, durationSec, expectedWh, actualEnergyWh,
                deviation, tolerancePercent, valid ? "PASS" : "FAIL");

        return valid;
    }

    /**
     * Vérifie la cohérence avec tolérance par défaut (5%).
     */
    public boolean validateEnergyConsistency(double powerW, long durationSec, double actualEnergyWh) {
        return validateEnergyConsistency(powerW, durationSec, actualEnergyWh, DEFAULT_TOLERANCE_PERCENT);
    }

    /**
     * Calcule l'énergie attendue.
     * E = P × t (en Wh)
     *
     * @param powerW      Puissance en Watts
     * @param durationSec Durée en secondes
     * @return Énergie en Wh
     */
    public double calculateExpectedEnergy(double powerW, long durationSec) {
        return powerW * durationSec / SECONDS_PER_HOUR;
    }

    /**
     * Calcule l'écart en pourcentage.
     */
    public double calculateDeviation(double expected, double actual) {
        if (expected == 0) {
            return actual == 0 ? 0 : 100;
        }
        return Math.abs((actual - expected) / expected) * 100;
    }

    /**
     * Valide avec un résultat détaillé.
     */
    public EnergyValidationResult validateWithDetails(double powerW, long durationSec,
                                                       double actualEnergyWh, double tolerancePercent) {
        double expectedWh = calculateExpectedEnergy(powerW, durationSec);
        double deviation = calculateDeviation(expectedWh, actualEnergyWh);
        boolean valid = deviation <= tolerancePercent;

        return EnergyValidationResult.builder()
                .powerW(powerW)
                .durationSec(durationSec)
                .expectedEnergyWh(expectedWh)
                .actualEnergyWh(actualEnergyWh)
                .deviationPercent(deviation)
                .tolerancePercent(tolerancePercent)
                .valid(valid)
                .build();
    }

    // =========================================================================
    // Validations additionnelles
    // =========================================================================

    /**
     * Vérifie la cohérence du SoC.
     * Formule: deltaSOC = energyWh / batteryCapacityWh × 100
     *
     * @param startSoc         SoC initial en %
     * @param endSoc           SoC final en %
     * @param energyWh         Énergie délivrée en Wh
     * @param batteryCapacityWh Capacité batterie en Wh
     * @param tolerancePercent Tolérance en points de %
     * @return true si cohérent
     */
    public boolean validateSocConsistency(double startSoc, double endSoc,
                                           double energyWh, double batteryCapacityWh,
                                           double tolerancePercent) {
        double expectedDeltaSoc = (energyWh / batteryCapacityWh) * 100;
        double actualDeltaSoc = endSoc - startSoc;
        double deviation = Math.abs(actualDeltaSoc - expectedDeltaSoc);

        boolean valid = deviation <= tolerancePercent;

        log.debug("[ENERGY] SoC Validation: start={:.1f}%, end={:.1f}%, energy={:.2f}Wh, " +
                        "capacity={:.2f}Wh, expectedDelta={:.2f}%, actualDelta={:.2f}%, " +
                        "deviation={:.2f}% -> {}",
                startSoc, endSoc, energyWh, batteryCapacityWh,
                expectedDeltaSoc, actualDeltaSoc, deviation, valid ? "PASS" : "FAIL");

        return valid;
    }

    /**
     * Vérifie que la puissance ne dépasse pas la limite.
     */
    public boolean validatePowerLimit(double actualPowerW, double maxPowerW) {
        return actualPowerW <= maxPowerW;
    }

    /**
     * Vérifie que le courant ne dépasse pas la limite.
     */
    public boolean validateCurrentLimit(double actualCurrentA, double maxCurrentA) {
        return actualCurrentA <= maxCurrentA;
    }

    /**
     * Calcule la puissance à partir de la tension et du courant.
     * Triphasé avec tension phase-neutre (230V): P = phases × V × I
     * Triphasé avec tension ligne-ligne (400V): P = √3 × V × I
     * Monophasé: P = V × I
     *
     * @param voltageV Tension en Volts
     * @param currentA Courant en Ampères
     * @param phases   Nombre de phases (1, 2 ou 3)
     * @return Puissance en Watts
     */
    public double calculatePower(double voltageV, double currentA, int phases) {
        if (phases == 1) {
            return voltageV * currentA;
        } else if (phases >= 3) {
            // Triphasé: vérifier si tension phase-neutre ou ligne-ligne
            if (voltageV < 300) {
                return phases * voltageV * currentA;
            } else {
                return Math.sqrt(3) * voltageV * currentA;
            }
        } else {
            // Biphasé
            return voltageV * currentA * phases;
        }
    }

    /**
     * Calcule le courant à partir de la puissance.
     * Triphasé avec tension phase-neutre (230V): I = P / (phases × V)
     * Triphasé avec tension ligne-ligne (400V): I = P / (√3 × V)
     * Monophasé: I = P / V
     */
    public double calculateCurrent(double powerW, double voltageV, int phases) {
        if (phases == 1) {
            return powerW / voltageV;
        } else if (phases >= 3) {
            // Triphasé: vérifier si tension phase-neutre ou ligne-ligne
            if (voltageV < 300) {
                return powerW / (phases * voltageV);
            } else {
                return powerW / (Math.sqrt(3) * voltageV);
            }
        } else {
            // Biphasé
            return powerW / (voltageV * phases);
        }
    }

    /**
     * Valide une série de points de mesure (MeterValues).
     */
    public MeterValuesValidationResult validateMeterValuesSeries(List<MeterValuePoint> points,
                                                                   double tolerancePercent) {
        MeterValuesValidationResult result = new MeterValuesValidationResult();
        result.setTotalPoints(points.size());

        if (points.size() < 2) {
            result.setValid(true);
            result.setMessage("Not enough points to validate");
            return result;
        }

        List<String> errors = new ArrayList<>();

        for (int i = 1; i < points.size(); i++) {
            MeterValuePoint prev = points.get(i - 1);
            MeterValuePoint curr = points.get(i);

            // Vérifier que l'énergie augmente
            if (curr.getEnergyWh() < prev.getEnergyWh()) {
                errors.add(String.format("Energy decreased at point %d: %.2f -> %.2f",
                        i, prev.getEnergyWh(), curr.getEnergyWh()));
            }

            // Vérifier la cohérence P × t = E
            long durationSec = curr.getTimestampSec() - prev.getTimestampSec();
            double avgPower = (prev.getPowerW() + curr.getPowerW()) / 2;
            double expectedEnergyDelta = calculateExpectedEnergy(avgPower, durationSec);
            double actualEnergyDelta = curr.getEnergyWh() - prev.getEnergyWh();
            double deviation = calculateDeviation(expectedEnergyDelta, actualEnergyDelta);

            if (deviation > tolerancePercent) {
                errors.add(String.format("Energy deviation at point %d: expected %.2fWh delta, " +
                                "got %.2fWh (%.2f%% deviation)",
                        i, expectedEnergyDelta, actualEnergyDelta, deviation));
            }

            // Vérifier que le SoC augmente (si charge)
            if (avgPower > 0 && curr.getSocPercent() < prev.getSocPercent()) {
                errors.add(String.format("SoC decreased while charging at point %d: %.1f%% -> %.1f%%",
                        i, prev.getSocPercent(), curr.getSocPercent()));
            }
        }

        result.setValid(errors.isEmpty());
        result.setErrors(errors);
        result.setMessage(errors.isEmpty() ? "All meter values consistent" :
                errors.size() + " inconsistencies found");

        return result;
    }

    // =========================================================================
    // Modèles
    // =========================================================================

    /**
     * Résultat de validation énergétique.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnergyValidationResult {
        private double powerW;
        private long durationSec;
        private double expectedEnergyWh;
        private double actualEnergyWh;
        private double deviationPercent;
        private double tolerancePercent;
        private boolean valid;

        public String getSummary() {
            return String.format("P=%.1fW × t=%ds = %.2fWh (expected) vs %.2fWh (actual), " +
                            "deviation=%.2f%%, tolerance=%.2f%% -> %s",
                    powerW, durationSec, expectedEnergyWh, actualEnergyWh,
                    deviationPercent, tolerancePercent, valid ? "PASS" : "FAIL");
        }
    }

    /**
     * Point de mesure (MeterValue).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MeterValuePoint {
        private long timestampSec;
        private double powerW;
        private double energyWh;
        private double socPercent;
        private double currentA;
        private double voltageV;
    }

    /**
     * Résultat de validation d'une série de MeterValues.
     */
    @Data
    public static class MeterValuesValidationResult {
        private int totalPoints;
        private boolean valid;
        private String message;
        private List<String> errors = new ArrayList<>();
    }

    // =========================================================================
    // Conversion helpers
    // =========================================================================

    /**
     * Convertit Wh en kWh.
     */
    public double whToKwh(double wh) {
        return wh / 1000.0;
    }

    /**
     * Convertit kWh en Wh.
     */
    public double kwhToWh(double kwh) {
        return kwh * 1000.0;
    }

    /**
     * Convertit W en kW.
     */
    public double wToKw(double w) {
        return w / 1000.0;
    }

    /**
     * Convertit kW en W.
     */
    public double kwToW(double kw) {
        return kw * 1000.0;
    }

    /**
     * Calcule le temps de charge estimé.
     *
     * @param energyNeededWh Énergie nécessaire en Wh
     * @param powerW         Puissance de charge en W
     * @return Durée en secondes
     */
    public long estimateChargingTime(double energyNeededWh, double powerW) {
        if (powerW <= 0) return Long.MAX_VALUE;
        return Math.round(energyNeededWh / powerW * SECONDS_PER_HOUR);
    }

    /**
     * Calcule l'énergie nécessaire pour atteindre un SoC cible.
     *
     * @param currentSoc      SoC actuel en %
     * @param targetSoc       SoC cible en %
     * @param batteryCapacityWh Capacité batterie en Wh
     * @return Énergie nécessaire en Wh
     */
    public double calculateEnergyNeeded(double currentSoc, double targetSoc, double batteryCapacityWh) {
        double deltaSoc = targetSoc - currentSoc;
        if (deltaSoc <= 0) return 0;
        return batteryCapacityWh * deltaSoc / 100.0;
    }
}
