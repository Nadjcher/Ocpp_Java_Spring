package com.evse.simulator.gpm.model.enums;

/**
 * États possibles d'une simulation GPM.
 */
public enum GPMSimulationStatus {
    CREATED,    // Simulation créée, en attente de démarrage
    RUNNING,    // Simulation en cours d'exécution
    PAUSED,     // Simulation en pause
    COMPLETED,  // Simulation terminée avec succès
    FAILED,     // Simulation échouée
    CANCELLED   // Simulation annulée par l'utilisateur
}
