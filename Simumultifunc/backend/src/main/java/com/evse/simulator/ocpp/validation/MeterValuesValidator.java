package com.evse.simulator.ocpp.validation;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validateur pour les MeterValues OCPP 1.6.
 * Vérifie la cohérence des valeurs électriques (P, V, I) et détecte les anomalies.
 */
@Component
@Slf4j
public class MeterValuesValidator {

    // Tolérances pour les validations
    private static final double POWER_TOLERANCE_PERCENT = 10.0;  // 10% de tolérance
    private static final double MIN_CHARGING_CURRENT_A = 0.5;    // Courant minimum pour charge active
    private static final double MAX_PHASE_IMBALANCE_PERCENT = 15.0; // 15% de déséquilibre max

    /**
     * Résultat de validation avec détails.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> warnings;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> warnings, List<String> errors) {
            this.valid = valid;
            this.warnings = warnings;
            this.errors = errors;
        }

        public boolean isValid() { return valid; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getErrors() { return errors; }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public boolean hasErrors() { return !errors.isEmpty(); }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult{valid=").append(valid);
            if (!warnings.isEmpty()) {
                sb.append(", warnings=").append(warnings);
            }
            if (!errors.isEmpty()) {
                sb.append(", errors=").append(errors);
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Valide les valeurs d'une session avant l'envoi des MeterValues.
     * @param session la session à valider
     * @return résultat de validation avec warnings/erreurs
     */
    public ValidationResult validateSession(Session session) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (session == null) {
            errors.add("Session is null");
            return new ValidationResult(false, warnings, errors);
        }

        double powerKw = session.getCurrentPowerKw();
        double currentA = session.getCurrentA();
        double voltage = session.getVoltage();
        int phases = session.getEffectivePhases();
        ChargerType chargerType = session.getChargerType();

        // 1. Vérifier la cohérence P = V × I (ou P = √3 × V × I pour triphasé)
        validatePowerCurrentConsistency(powerKw, currentA, voltage, phases, chargerType, warnings, errors);

        // 2. Vérifier les plages de valeurs réalistes
        validateRealisticRanges(powerKw, currentA, voltage, warnings, errors);

        // 3. Vérifier la cohérence entre état de charge et courant
        validateChargingState(session, warnings, errors);

        boolean isValid = errors.isEmpty();

        if (!warnings.isEmpty() || !errors.isEmpty()) {
            log.warn("[{}] MeterValues validation: valid={}, warnings={}, errors={}",
                    session.getSessionId(), isValid, warnings, errors);
        }

        return new ValidationResult(isValid, warnings, errors);
    }

    /**
     * Valide la cohérence entre puissance, courant et tension.
     * P = V × I (monophasé) ou P = √3 × V × I (triphasé équilibré)
     */
    private void validatePowerCurrentConsistency(double powerKw, double currentA, double voltage,
                                                  int phases, ChargerType chargerType,
                                                  List<String> warnings, List<String> errors) {
        if (powerKw <= 0 || currentA <= 0 || voltage <= 0) {
            // Pas de charge active, pas de validation nécessaire
            return;
        }

        double powerW = powerKw * 1000;
        double expectedPowerW;

        if (chargerType != null && chargerType.isAC() && phases >= 3) {
            // Triphasé: P = √3 × V × I (V = tension phase-neutre)
            expectedPowerW = Math.sqrt(3) * voltage * currentA;
        } else if (chargerType != null && chargerType.isAC() && phases == 2) {
            // Biphasé: P = 2 × V × I
            expectedPowerW = 2 * voltage * currentA;
        } else {
            // Monophasé ou DC: P = V × I
            expectedPowerW = voltage * currentA;
        }

        double deviation = Math.abs(powerW - expectedPowerW) / expectedPowerW * 100;

        if (deviation > POWER_TOLERANCE_PERCENT * 2) {
            errors.add(String.format(
                "Incohérence majeure P/V/I: P=%.0fW, attendu=%.0fW (V=%.1fV, I=%.2fA, phases=%d), écart=%.1f%%",
                powerW, expectedPowerW, voltage, currentA, phases, deviation));
        } else if (deviation > POWER_TOLERANCE_PERCENT) {
            warnings.add(String.format(
                "Écart P/V/I: P=%.0fW vs attendu=%.0fW (écart=%.1f%%)",
                powerW, expectedPowerW, deviation));
        }
    }

    /**
     * Valide que les valeurs sont dans des plages réalistes.
     */
    private void validateRealisticRanges(double powerKw, double currentA, double voltage,
                                         List<String> warnings, List<String> errors) {
        // Tension
        if (voltage > 0 && (voltage < 100 || voltage > 1000)) {
            errors.add(String.format("Tension hors plage réaliste: %.1fV (attendu: 100-1000V)", voltage));
        }

        // Courant
        if (currentA > 500) {
            errors.add(String.format("Courant irréaliste: %.1fA (max attendu: 500A)", currentA));
        }

        // Puissance
        if (powerKw > 350) {
            warnings.add(String.format("Puissance très élevée: %.1fkW (typique max: 350kW)", powerKw));
        }

        // Courant très faible pendant charge supposée active
        if (powerKw > 1 && currentA < MIN_CHARGING_CURRENT_A) {
            warnings.add(String.format(
                "Courant très faible (%.3fA) pour puissance %.1fkW - vérifier la source de données",
                currentA, powerKw));
        }
    }

    /**
     * Valide la cohérence de l'état de charge.
     */
    private void validateChargingState(Session session, List<String> warnings, List<String> errors) {
        double soc = session.getSoc();
        double targetSoc = session.getTargetSoc();
        double powerKw = session.getCurrentPowerKw();
        double currentA = session.getCurrentA();

        // Si SoC >= target, la charge devrait être arrêtée ou très faible
        if (soc >= targetSoc && powerKw > 1) {
            warnings.add(String.format(
                "Charge active (%.1fkW) alors que SoC (%.0f%%) >= target (%.0f%%)",
                powerKw, soc, targetSoc));
        }

        // Si puissance > 0 mais courant ≈ 0, incohérence
        if (powerKw > 0.5 && currentA < 0.1) {
            errors.add(String.format(
                "Incohérence: puissance=%.2fkW mais courant=%.3fA (quasi nul)",
                powerKw, currentA));
        }
    }

    /**
     * Valide un payload MeterValues OCPP déjà construit.
     * Utile pour valider les messages avant envoi.
     */
    public ValidationResult validatePayload(Map<String, Object> payload) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (payload == null) {
            errors.add("Payload is null");
            return new ValidationResult(false, warnings, errors);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> meterValues = (List<Map<String, Object>>) payload.get("meterValue");

        if (meterValues == null || meterValues.isEmpty()) {
            errors.add("No meterValue in payload");
            return new ValidationResult(false, warnings, errors);
        }

        for (Map<String, Object> mv : meterValues) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sampledValues = (List<Map<String, Object>>) mv.get("sampledValue");

            if (sampledValues != null) {
                validateSampledValues(sampledValues, warnings, errors);
            }
        }

        return new ValidationResult(errors.isEmpty(), warnings, errors);
    }

    /**
     * Valide les sampledValues d'un MeterValue.
     */
    private void validateSampledValues(List<Map<String, Object>> sampledValues,
                                       List<String> warnings, List<String> errors) {
        Double totalPowerW = null;
        Double totalCurrentA = null;
        Double voltage = null;
        List<Double> phaseCurrents = new ArrayList<>();

        for (Map<String, Object> sv : sampledValues) {
            String measurand = (String) sv.get("measurand");
            String valueStr = (String) sv.get("value");
            String phase = (String) sv.get("phase");

            if (valueStr == null || measurand == null) continue;

            try {
                double value = Double.parseDouble(valueStr);

                switch (measurand) {
                    case "Power.Active.Import":
                        if (phase == null) {
                            totalPowerW = value;
                        }
                        break;
                    case "Current.Import":
                        if (phase != null && !phase.equals("N")) {
                            phaseCurrents.add(value);
                        } else if (phase == null) {
                            totalCurrentA = value;
                        }
                        break;
                    case "Voltage":
                        if (phase != null && !phase.contains("-")) {
                            voltage = value; // Tension phase-neutre
                        }
                        break;
                }
            } catch (NumberFormatException e) {
                errors.add(String.format("Invalid value format for %s: %s", measurand, valueStr));
            }
        }

        // Vérifier le déséquilibre de phase si triphasé
        if (phaseCurrents.size() >= 2) {
            double maxCurrent = phaseCurrents.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double minCurrent = phaseCurrents.stream().mapToDouble(Double::doubleValue).min().orElse(0);

            if (maxCurrent > 0) {
                double imbalance = (maxCurrent - minCurrent) / maxCurrent * 100;
                if (imbalance > MAX_PHASE_IMBALANCE_PERCENT) {
                    warnings.add(String.format(
                        "Déséquilibre de phase important: %.1f%% (max recommandé: %.1f%%)",
                        imbalance, MAX_PHASE_IMBALANCE_PERCENT));
                }
            }

            // Calculer le courant total pour validation P/I si pas de courant total
            if (totalCurrentA == null && !phaseCurrents.isEmpty()) {
                totalCurrentA = phaseCurrents.get(0); // Courant par phase en triphasé
            }
        }

        // Validation croisée P/V/I depuis le payload
        if (totalPowerW != null && totalCurrentA != null && voltage != null) {
            int phases = phaseCurrents.size() > 0 ? phaseCurrents.size() : 1;
            double expectedPower;

            if (phases >= 3) {
                expectedPower = Math.sqrt(3) * voltage * totalCurrentA;
            } else {
                expectedPower = voltage * totalCurrentA * phases;
            }

            double deviation = Math.abs(totalPowerW - expectedPower) / Math.max(expectedPower, 1) * 100;

            if (deviation > POWER_TOLERANCE_PERCENT * 2 && totalPowerW > 100) {
                warnings.add(String.format(
                    "Payload: P=%.0fW vs calculé=%.0fW (V=%.1fV, I=%.2fA), écart=%.1f%%",
                    totalPowerW, expectedPower, voltage, totalCurrentA, deviation));
            }
        }
    }
}
