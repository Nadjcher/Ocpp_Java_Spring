package com.evse.simulator.tnr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Comparaison entre un résultat et sa baseline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaselineComparison {

    /**
     * Résultat de la comparaison.
     */
    public enum ComparisonResult {
        MATCH,          // Identique à la baseline
        IMPROVED,       // Meilleur que la baseline
        DEGRADED,       // Moins bon que la baseline
        CHANGED,        // Différent mais ni meilleur ni pire
        NO_BASELINE     // Pas de baseline disponible
    }

    /** ID de la baseline */
    private String baselineId;

    /** Version de la baseline */
    private String baselineVersion;

    /** Date de la baseline */
    private Instant baselineDate;

    /** Résultat global */
    private ComparisonResult result;

    /** Score de similarité (0-100) */
    private double similarityScore;

    /** Différences détectées */
    @Builder.Default
    private List<Difference> differences = new ArrayList<>();

    /** Métriques baseline */
    private Map<String, Object> baselineMetrics;

    /** Métriques actuelles */
    private Map<String, Object> currentMetrics;

    /** Régressions détectées */
    @Builder.Default
    private List<String> regressions = new ArrayList<>();

    /** Améliorations détectées */
    @Builder.Default
    private List<String> improvements = new ArrayList<>();

    /**
     * Représente une différence entre baseline et résultat actuel.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Difference {

        /**
         * Type de différence.
         */
        public enum DiffType {
            VALUE_CHANGED,      // Valeur différente
            FIELD_ADDED,        // Champ ajouté
            FIELD_REMOVED,      // Champ supprimé
            TYPE_CHANGED,       // Type changé
            ORDER_CHANGED       // Ordre changé
        }

        /** Chemin du champ */
        private String path;

        /** Type de différence */
        private DiffType type;

        /** Valeur baseline */
        private Object baselineValue;

        /** Valeur actuelle */
        private Object currentValue;

        /** Impact (positif, négatif, neutre) */
        private String impact;

        /** Significatif */
        private boolean significant;
    }

    /**
     * Vérifie si la comparaison montre des régressions.
     */
    public boolean hasRegressions() {
        return regressions != null && !regressions.isEmpty();
    }

    /**
     * Vérifie si le résultat est acceptable (match ou improved).
     */
    public boolean isAcceptable() {
        return result == ComparisonResult.MATCH || result == ComparisonResult.IMPROVED;
    }

    /**
     * Ajoute une différence.
     */
    public void addDifference(Difference diff) {
        if (differences == null) differences = new ArrayList<>();
        differences.add(diff);
    }

    /**
     * Compte les différences significatives.
     */
    public long getSignificantDifferenceCount() {
        if (differences == null) return 0;
        return differences.stream().filter(Difference::isSignificant).count();
    }
}
