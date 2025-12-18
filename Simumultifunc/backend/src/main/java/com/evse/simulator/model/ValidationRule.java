package com.evse.simulator.model;

import lombok.Data;

/**
 * Règle de validation pour les tests TNR.
 */
@Data
public class ValidationRule {
    /**
     * Type de validation : response, latency, value, scp.
     */
    private String type;

    /**
     * Cible de la validation (chemin ou champ).
     */
    private String target;

    /**
     * Valeur attendue.
     */
    private Object expected;

    /**
     * Tolérance pour les comparaisons numériques.
     */
    private Double tolerance;
}