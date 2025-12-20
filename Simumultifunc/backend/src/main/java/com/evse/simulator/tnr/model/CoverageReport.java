package com.evse.simulator.tnr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rapport de couverture des tests TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageReport {

    /** Timestamp du rapport */
    @Builder.Default
    private Instant generatedAt = Instant.now();

    // =========================================================================
    // Couverture des messages OCPP
    // =========================================================================

    /** Messages OCPP testés */
    @Builder.Default
    private Set<String> testedMessages = Set.of();

    /** Messages OCPP non testés */
    @Builder.Default
    private Set<String> untestedMessages = Set.of();

    /** Taux de couverture messages (0-100) */
    private double messageCoveragePercent;

    // =========================================================================
    // Couverture des transitions d'état
    // =========================================================================

    /** Transitions testées */
    @Builder.Default
    private List<StateTransition> testedTransitions = new ArrayList<>();

    /** Transitions non testées */
    @Builder.Default
    private List<StateTransition> untestedTransitions = new ArrayList<>();

    /** Taux de couverture transitions (0-100) */
    private double transitionCoveragePercent;

    // =========================================================================
    // Couverture des paramètres
    // =========================================================================

    /** Combinaisons de paramètres testées */
    @Builder.Default
    private Set<String> testedCombinations = Set.of();

    /** Combinaisons non testées */
    @Builder.Default
    private Set<String> untestedCombinations = Set.of();

    /** Taux de couverture combinaisons (0-100) */
    private double combinationCoveragePercent;

    // =========================================================================
    // Couverture des scénarios d'erreur
    // =========================================================================

    /** Scénarios d'erreur testés */
    @Builder.Default
    private Set<String> testedErrorScenarios = Set.of();

    /** Scénarios d'erreur non testés */
    @Builder.Default
    private Set<String> untestedErrorScenarios = Set.of();

    /** Taux de couverture erreurs (0-100) */
    private double errorCoveragePercent;

    // =========================================================================
    // Statistiques globales
    // =========================================================================

    /** Score de couverture global (0-100) */
    private double overallCoverageScore;

    /** Zones non couvertes critiques */
    @Builder.Default
    private List<String> criticalGaps = new ArrayList<>();

    /** Suggestions d'amélioration */
    @Builder.Default
    private List<String> suggestions = new ArrayList<>();

    /** Couverture par catégorie */
    @Builder.Default
    private Map<String, Double> coverageByCategory = new HashMap<>();

    /**
     * Transition d'état OCPP.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateTransition {
        private String fromState;
        private String toState;
        private String trigger;
        private boolean critical;
    }

    /**
     * Calcule le score global.
     */
    public void calculateOverallScore() {
        overallCoverageScore = (
            messageCoveragePercent * 0.35 +
            transitionCoveragePercent * 0.30 +
            combinationCoveragePercent * 0.20 +
            errorCoveragePercent * 0.15
        );
    }

    /**
     * Identifie les gaps critiques.
     */
    public void identifyCriticalGaps() {
        criticalGaps = new ArrayList<>();

        // Messages critiques non testés
        if (untestedMessages.contains("StartTransaction")) {
            criticalGaps.add("StartTransaction non testé");
        }
        if (untestedMessages.contains("StopTransaction")) {
            criticalGaps.add("StopTransaction non testé");
        }
        if (untestedMessages.contains("SetChargingProfile")) {
            criticalGaps.add("Smart Charging non testé");
        }

        // Transitions critiques non testées
        for (StateTransition t : untestedTransitions) {
            if (t.isCritical()) {
                criticalGaps.add(String.format("Transition critique non testée: %s -> %s",
                    t.getFromState(), t.getToState()));
            }
        }

        // Si couverture globale trop basse
        if (overallCoverageScore < 50) {
            criticalGaps.add("Couverture globale insuffisante (< 50%)");
        }
    }

    /**
     * Génère des suggestions.
     */
    public void generateSuggestions() {
        suggestions = new ArrayList<>();

        if (messageCoveragePercent < 80) {
            suggestions.add("Ajouter des tests pour les messages OCPP non couverts: " +
                String.join(", ", untestedMessages.stream().limit(5).toList()));
        }

        if (transitionCoveragePercent < 70) {
            suggestions.add("Ajouter des tests de transition d'état, notamment pour les cas d'erreur");
        }

        if (combinationCoveragePercent < 60) {
            suggestions.add("Utiliser la génération automatique pour couvrir plus de combinaisons de paramètres");
        }

        if (errorCoveragePercent < 50) {
            suggestions.add("Renforcer les tests de gestion d'erreur et cas limites");
        }
    }
}
