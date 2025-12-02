package com.evse.simulator.model.enums;

/**
 * Types d'optimisation de charge disponibles.
 */
public enum OptimizationType {
    /**
     * Charge standard sans optimisation.
     */
    STANDARD,

    /**
     * Optimisation par coût (heures creuses).
     */
    COST,

    /**
     * Optimisation énergie verte.
     */
    GREEN_ENERGY,

    /**
     * Charge rapide.
     */
    FAST
}