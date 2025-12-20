package com.evse.simulator.tnr.model;

import lombok.*;

import java.time.Instant;

/**
 * Métadonnées d'un scénario TNR.
 * <p>
 * Stocke les informations de suivi comme les dates, l'auteur,
 * et les statistiques d'exécution.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrMetadata {

    /** Version du scénario */
    @Builder.Default
    private String version = "1.0";

    /** Date de création */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Auteur/créateur */
    @Builder.Default
    private String createdBy = "system";

    /** Date de dernière exécution */
    private Instant lastRunAt;

    /** Statut de la dernière exécution */
    private String lastRunStatus;

    /** ID de la dernière exécution */
    private String lastExecutionId;

    /** Nombre total d'exécutions */
    @Builder.Default
    private int runCount = 0;

    /** Nombre d'exécutions réussies */
    @Builder.Default
    private int passCount = 0;

    /** Nombre d'exécutions échouées */
    @Builder.Default
    private int failCount = 0;

    /** Durée moyenne d'exécution en millisecondes */
    private Long avgDurationMs;

    /** Durée minimale d'exécution en millisecondes */
    private Long minDurationMs;

    /** Durée maximale d'exécution en millisecondes */
    private Long maxDurationMs;

    /** Taux de réussite (0-100) */
    public double getSuccessRate() {
        if (runCount == 0) {
            return 0.0;
        }
        return (double) passCount / runCount * 100.0;
    }

    /**
     * Met à jour les statistiques après une exécution.
     */
    public void recordExecution(boolean passed, long durationMs, String executionId) {
        runCount++;
        if (passed) {
            passCount++;
            lastRunStatus = "PASSED";
        } else {
            failCount++;
            lastRunStatus = "FAILED";
        }
        lastRunAt = Instant.now();
        lastExecutionId = executionId;

        // Mettre à jour les durées
        if (minDurationMs == null || durationMs < minDurationMs) {
            minDurationMs = durationMs;
        }
        if (maxDurationMs == null || durationMs > maxDurationMs) {
            maxDurationMs = durationMs;
        }

        // Calculer la moyenne
        if (avgDurationMs == null) {
            avgDurationMs = durationMs;
        } else {
            avgDurationMs = (avgDurationMs * (runCount - 1) + durationMs) / runCount;
        }
    }

    /**
     * Clone les métadonnées pour un nouveau scénario.
     */
    public TnrMetadata cloneForNew(String author) {
        return TnrMetadata.builder()
                .version("1.0")
                .createdAt(Instant.now())
                .createdBy(author)
                .runCount(0)
                .passCount(0)
                .failCount(0)
                .build();
    }
}
