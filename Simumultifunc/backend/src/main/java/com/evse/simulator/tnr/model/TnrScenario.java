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

/**
 * Représente un scénario de test TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrScenario {

    private String id;
    private String name;
    private String description;
    private String category;
    private List<String> tags;

    /** Configuration du scénario */
    @Builder.Default
    private TnrConfig config = TnrConfig.defaults();

    /** Résultats attendus pour la validation */
    private TnrExpectedResults expectedResults;

    /** Métadonnées du scénario */
    @Builder.Default
    private TnrMetadata metadata = new TnrMetadata();

    /** Contenu Gherkin brut */
    private String gherkinContent;

    /** Steps parsés */
    @Builder.Default
    private List<TnrStep> steps = new ArrayList<>();

    /** Steps de Background (exécutés avant chaque scénario) */
    @Builder.Default
    private List<TnrStep> backgroundSteps = new ArrayList<>();

    /** Paramètres du scénario (pour Scenario Outline) */
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    /** Examples pour Scenario Outline */
    @Builder.Default
    private List<Map<String, String>> examples = new ArrayList<>();

    /** Dépendances (autres scénarios à exécuter avant) */
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();

    /** Priorité d'exécution (plus petit = plus prioritaire) */
    @Builder.Default
    private int priority = 100;

    /** Timeout max en secondes */
    @Builder.Default
    private int timeoutSeconds = 300;

    /** Nombre de retry en cas d'échec */
    @Builder.Default
    private int maxRetries = 0;

    /** Scénario critique (bloque la suite si échec) */
    @Builder.Default
    private boolean critical = false;

    /** Scénario activé */
    @Builder.Default
    private boolean enabled = true;

    /** Fichier source */
    private String sourceFile;

    /** Ligne dans le fichier source */
    private int sourceLine;

    /** Date de création */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Date de dernière modification */
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /** Indique si c'est un template */
    @Builder.Default
    private boolean template = false;

    /** ID du template source (si créé à partir d'un template) */
    private String templateId;

    /**
     * Vérifie si le scénario a un tag spécifique.
     */
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }

    /**
     * Vérifie si le scénario est un Scenario Outline (avec Examples).
     */
    public boolean isOutline() {
        return examples != null && !examples.isEmpty();
    }

    /**
     * Retourne le nombre total de steps (background + steps).
     */
    public int getTotalStepCount() {
        int count = steps != null ? steps.size() : 0;
        count += backgroundSteps != null ? backgroundSteps.size() : 0;
        return count;
    }
}
