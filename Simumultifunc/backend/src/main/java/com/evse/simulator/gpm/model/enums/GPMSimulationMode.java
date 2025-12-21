package com.evse.simulator.gpm.model.enums;

/**
 * Mode de simulation GPM.
 */
public enum GPMSimulationMode {
    /**
     * Mode local: simulation sans appels API TTE.
     * Utile pour les tests.
     */
    LOCAL,

    /**
     * Mode Dry-Run: utilise l'API TTE /qa/dry-run/*.
     * Simulation avec feedback loop GPM réel.
     */
    DRY_RUN
}
