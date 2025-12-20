package com.evse.simulator.tnr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Résultat d'exécution d'une suite de scénarios TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrSuiteResult {

    /** ID unique de l'exécution */
    private String executionId;

    /** Nom de la suite */
    private String suiteName;

    /** Description */
    private String description;

    /** Tags de la suite */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /** Environnement d'exécution */
    private String environment;

    /** Version du code testé */
    private String version;

    /** Résultats des scénarios */
    @Builder.Default
    private List<TnrResult> scenarioResults = new ArrayList<>();

    /** Heure de début */
    private Instant startTime;

    /** Heure de fin */
    private Instant endTime;

    /** Durée totale en ms */
    private long durationMs;

    /** Configuration utilisée */
    @Builder.Default
    private Map<String, Object> configuration = new HashMap<>();

    /** Métriques globales */
    @Builder.Default
    private Map<String, Object> metrics = new HashMap<>();

    /** Rapport de couverture */
    private CoverageReport coverageReport;

    /** Anomalies détectées globalement */
    @Builder.Default
    private List<Anomaly> anomalies = new ArrayList<>();

    // =========================================================================
    // Statistiques calculées
    // =========================================================================

    /**
     * Nombre total de scénarios.
     */
    public int getTotalScenarios() {
        return scenarioResults != null ? scenarioResults.size() : 0;
    }

    /**
     * Nombre de scénarios passés.
     */
    public long getPassedCount() {
        return countByStatus(TnrResult.Status.PASSED);
    }

    /**
     * Nombre de scénarios échoués.
     */
    public long getFailedCount() {
        return countByStatus(TnrResult.Status.FAILED);
    }

    /**
     * Nombre de scénarios en erreur.
     */
    public long getErrorCount() {
        return countByStatus(TnrResult.Status.ERROR);
    }

    /**
     * Nombre de scénarios skippés.
     */
    public long getSkippedCount() {
        return countByStatus(TnrResult.Status.SKIPPED);
    }

    /**
     * Taux de succès en pourcentage.
     */
    public double getSuccessRate() {
        int total = getTotalScenarios();
        if (total == 0) return 0.0;
        return (double) getPassedCount() / total * 100.0;
    }

    /**
     * Durée moyenne par scénario.
     */
    public double getAverageDurationMs() {
        if (scenarioResults == null || scenarioResults.isEmpty()) return 0.0;
        return scenarioResults.stream()
            .mapToLong(TnrResult::getDurationMs)
            .average()
            .orElse(0.0);
    }

    /**
     * Durée totale.
     */
    public Duration getDuration() {
        if (startTime == null || endTime == null) return Duration.ZERO;
        return Duration.between(startTime, endTime);
    }

    /**
     * Vérifie si tous les scénarios ont réussi.
     */
    public boolean isAllPassed() {
        return getFailedCount() == 0 && getErrorCount() == 0;
    }

    /**
     * Retourne les scénarios par statut.
     */
    public Map<TnrResult.Status, List<TnrResult>> getResultsByStatus() {
        if (scenarioResults == null) return new HashMap<>();
        return scenarioResults.stream()
            .collect(Collectors.groupingBy(TnrResult::getStatus));
    }

    /**
     * Retourne les scénarios échoués.
     */
    public List<TnrResult> getFailedScenarios() {
        if (scenarioResults == null) return new ArrayList<>();
        return scenarioResults.stream()
            .filter(r -> r.getStatus() == TnrResult.Status.FAILED
                      || r.getStatus() == TnrResult.Status.ERROR)
            .collect(Collectors.toList());
    }

    /**
     * Compte les steps totaux.
     */
    public long getTotalSteps() {
        if (scenarioResults == null) return 0;
        return scenarioResults.stream()
            .mapToLong(r -> r.getStepResults() != null ? r.getStepResults().size() : 0)
            .sum();
    }

    /**
     * Compte les steps passés.
     */
    public long getPassedSteps() {
        if (scenarioResults == null) return 0;
        return scenarioResults.stream()
            .flatMap(r -> r.getStepResults() != null ? r.getStepResults().stream() : null)
            .filter(sr -> sr != null && sr.getStatus() == TnrStepResult.Status.PASSED)
            .count();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private long countByStatus(TnrResult.Status status) {
        if (scenarioResults == null) return 0;
        return scenarioResults.stream()
            .filter(r -> r.getStatus() == status)
            .count();
    }

    /**
     * Ajoute un résultat de scénario.
     */
    public void addScenarioResult(TnrResult result) {
        if (scenarioResults == null) scenarioResults = new ArrayList<>();
        scenarioResults.add(result);
    }
}
