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

/**
 * Résultat d'exécution d'un scénario TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrResult {

    /**
     * Statut d'exécution.
     */
    public enum Status {
        PASSED,      // Tous les steps ont réussi
        FAILED,      // Au moins un step a échoué
        SKIPPED,     // Scénario non exécuté (dépendance échouée)
        ERROR,       // Erreur technique (exception)
        PENDING,     // En attente d'exécution
        RUNNING      // En cours d'exécution
    }

    /** ID unique de l'exécution */
    private String executionId;

    /** Scénario exécuté */
    private TnrScenario scenario;

    /** Statut final */
    @Builder.Default
    private Status status = Status.PENDING;

    /** Résultats des steps */
    @Builder.Default
    private List<TnrStepResult> stepResults = new ArrayList<>();

    /** Heure de début */
    private Instant startTime;

    /** Heure de fin */
    private Instant endTime;

    /** Durée totale en ms */
    private long durationMs;

    /** Nombre de retries effectués */
    @Builder.Default
    private int retryCount = 0;

    /** Message d'erreur (si échec) */
    private String errorMessage;

    /** Stack trace (si erreur) */
    private String stackTrace;

    /** Captures d'écran ou pièces jointes */
    @Builder.Default
    private List<TnrAttachment> attachments = new ArrayList<>();

    /** Métriques collectées */
    @Builder.Default
    private Map<String, Object> metrics = new HashMap<>();

    /** Contexte d'exécution (variables, etc.) */
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    /** Anomalies détectées */
    @Builder.Default
    private List<Anomaly> anomalies = new ArrayList<>();

    /** Comparaison avec baseline */
    private BaselineComparison baselineComparison;

    /**
     * Calcule la durée.
     */
    public Duration getDuration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }

    /**
     * Compte les steps par statut.
     */
    public Map<TnrStepResult.Status, Long> getStepCountByStatus() {
        Map<TnrStepResult.Status, Long> counts = new HashMap<>();
        for (TnrStepResult.Status s : TnrStepResult.Status.values()) {
            counts.put(s, 0L);
        }
        if (stepResults != null) {
            for (TnrStepResult sr : stepResults) {
                counts.merge(sr.getStatus(), 1L, Long::sum);
            }
        }
        return counts;
    }

    /**
     * Vérifie si le résultat est un succès.
     */
    public boolean isSuccess() {
        return status == Status.PASSED;
    }

    /**
     * Retourne le premier step en échec.
     */
    public TnrStepResult getFirstFailedStep() {
        if (stepResults == null) return null;
        return stepResults.stream()
            .filter(sr -> sr.getStatus() == TnrStepResult.Status.FAILED
                       || sr.getStatus() == TnrStepResult.Status.ERROR)
            .findFirst()
            .orElse(null);
    }

    /**
     * Ajoute une métrique.
     */
    public void addMetric(String key, Object value) {
        if (metrics == null) metrics = new HashMap<>();
        metrics.put(key, value);
    }

    /**
     * Ajoute une anomalie.
     */
    public void addAnomaly(Anomaly anomaly) {
        if (anomalies == null) anomalies = new ArrayList<>();
        anomalies.add(anomaly);
    }
}
