package com.evse.simulator.tnr.model;

import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Résultats attendus pour un scénario TNR.
 * <p>
 * Définit les valeurs de référence pour la validation des exécutions.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrExpectedResults {

    /** Toutes les sessions doivent être terminées */
    @Builder.Default
    private boolean allSessionsCompleted = true;

    /** Énergie totale attendue en kWh */
    private Double totalEnergyKwh;

    /** Durée totale attendue en secondes */
    private Integer totalDurationSec;

    /** Nombre d'erreurs OCPP attendues (généralement 0) */
    @Builder.Default
    private int ocppErrors = 0;

    /** Nombre de profils SCP appliqués */
    private Integer scpProfilesApplied;

    /** Les limites SCP doivent être respectées */
    private Boolean scpLimitRespected;

    /** Assertions personnalisées */
    @Builder.Default
    private List<TnrAssertion> customAssertions = new ArrayList<>();

    /**
     * Résultats attendus pour une session spécifique.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionExpectedResults {
        private String sessionId;

        /** SOC final exact attendu */
        private Integer finalSoc;

        /** SOC final minimum */
        private Integer finalSocMin;

        /** SOC final maximum */
        private Integer finalSocMax;

        /** Énergie délivrée attendue en kWh */
        private Double energyDeliveredKwh;

        /** Énergie minimum attendue en kWh */
        private Double energyDeliveredKwhMin;

        /** Puissance maximale atteinte en kW */
        private Double maxPowerReachedKw;

        /** Durée de charge en secondes */
        private Integer chargingDurationSec;

        /** Nombre de messages OCPP */
        private Integer ocppMessagesCount;

        /** Nombre de profils SCP appliqués */
        private Integer scpAppliedCount;

        /**
         * Valide le SOC final contre les attentes.
         */
        public boolean validateFinalSoc(int actualSoc, double tolerance) {
            if (finalSoc != null) {
                return Math.abs(actualSoc - finalSoc) <= tolerance;
            }
            if (finalSocMin != null && actualSoc < finalSocMin) {
                return false;
            }
            if (finalSocMax != null && actualSoc > finalSocMax) {
                return false;
            }
            return true;
        }

        /**
         * Valide l'énergie délivrée contre les attentes.
         */
        public boolean validateEnergy(double actualKwh, double tolerance) {
            if (energyDeliveredKwh != null) {
                return Math.abs(actualKwh - energyDeliveredKwh) <= tolerance;
            }
            if (energyDeliveredKwhMin != null) {
                return actualKwh >= energyDeliveredKwhMin - tolerance;
            }
            return true;
        }
    }

    /**
     * Assertion personnalisée pour la validation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TnrAssertion {
        /** Champ à valider (ex: "session.soc", "ocpp.lastMessage.action") */
        private String field;

        /** Opérateur de comparaison */
        private AssertionOperator operator;

        /** Valeur attendue */
        private Object expected;

        /** Tolérance pour les comparaisons numériques */
        private Double tolerance;

        /** Message d'erreur personnalisé */
        private String errorMessage;

        public enum AssertionOperator {
            EQUALS,
            NOT_EQUALS,
            GREATER_THAN,
            LESS_THAN,
            GREATER_OR_EQUAL,
            LESS_OR_EQUAL,
            CONTAINS,
            NOT_CONTAINS,
            MATCHES_REGEX,
            IS_NULL,
            IS_NOT_NULL,
            IN_RANGE
        }

        /**
         * Évalue l'assertion contre une valeur réelle.
         */
        public boolean evaluate(Object actual) {
            if (operator == AssertionOperator.IS_NULL) {
                return actual == null;
            }
            if (operator == AssertionOperator.IS_NOT_NULL) {
                return actual != null;
            }
            if (actual == null) {
                return false;
            }

            switch (operator) {
                case EQUALS:
                    if (actual instanceof Number && expected instanceof Number) {
                        double tol = tolerance != null ? tolerance : 0.0;
                        return Math.abs(((Number) actual).doubleValue() - ((Number) expected).doubleValue()) <= tol;
                    }
                    return actual.equals(expected);

                case NOT_EQUALS:
                    return !actual.equals(expected);

                case GREATER_THAN:
                    if (actual instanceof Number && expected instanceof Number) {
                        return ((Number) actual).doubleValue() > ((Number) expected).doubleValue();
                    }
                    return false;

                case LESS_THAN:
                    if (actual instanceof Number && expected instanceof Number) {
                        return ((Number) actual).doubleValue() < ((Number) expected).doubleValue();
                    }
                    return false;

                case GREATER_OR_EQUAL:
                    if (actual instanceof Number && expected instanceof Number) {
                        return ((Number) actual).doubleValue() >= ((Number) expected).doubleValue();
                    }
                    return false;

                case LESS_OR_EQUAL:
                    if (actual instanceof Number && expected instanceof Number) {
                        return ((Number) actual).doubleValue() <= ((Number) expected).doubleValue();
                    }
                    return false;

                case CONTAINS:
                    return actual.toString().contains(expected.toString());

                case NOT_CONTAINS:
                    return !actual.toString().contains(expected.toString());

                case MATCHES_REGEX:
                    return actual.toString().matches(expected.toString());

                case IN_RANGE:
                    if (actual instanceof Number && expected instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Number> range = (Map<String, Number>) expected;
                        double val = ((Number) actual).doubleValue();
                        double min = range.get("min").doubleValue();
                        double max = range.get("max").doubleValue();
                        return val >= min && val <= max;
                    }
                    return false;

                default:
                    return false;
            }
        }
    }
}
