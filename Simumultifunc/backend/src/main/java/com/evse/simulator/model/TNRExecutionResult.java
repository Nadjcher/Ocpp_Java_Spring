package com.evse.simulator.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Résultat d'une exécution de scénario TNR.
 */
@Data
public class TNRExecutionResult {
    /**
     * ID du scénario exécuté.
     */
    private String scenarioId;

    /**
     * ID unique de l'exécution.
     */
    private String executionId;

    /**
     * Date/heure de l'exécution.
     */
    private Date timestamp;

    /**
     * Indique si le test a réussi.
     */
    private boolean passed;

    /**
     * Liste des différences trouvées.
     */
    private List<TNRDifference> differences;

    /**
     * Liste des événements enregistrés.
     */
    private List<TNREvent> events;

    /**
     * Métriques de l'exécution.
     */
    private Metrics metrics;

    /**
     * Métriques d'exécution TNR.
     */
    @Data
    public static class Metrics {
        private Integer totalEvents;
        private Double avgLatency;
        private Long maxLatency;
        private Integer errorCount;
    }
}