package com.evse.simulator.tnr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration pour l'exécution d'une suite de scénarios TNR.
 * <p>
 * Permet de définir les paramètres d'exécution, le filtrage par tags,
 * et les options de parallélisme.
 * </p>
 *
 * @example
 * <pre>
 * TnrSuiteConfig config = TnrSuiteConfig.builder()
 *     .scenarios(List.of("SC001", "SC002", "SC003"))
 *     .parallel(false)
 *     .stopOnFailure(false)
 *     .build();
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrSuiteConfig {

    /**
     * Nom de la suite.
     */
    private String suiteName;

    /**
     * Description de la suite.
     */
    private String description;

    /**
     * Liste des IDs de scénarios à exécuter.
     * Si vide, tous les scénarios disponibles seront exécutés.
     */
    @Builder.Default
    private List<String> scenarios = new ArrayList<>();

    /**
     * Tags à inclure (OR logic).
     * Seuls les scénarios avec au moins un de ces tags seront exécutés.
     */
    @Builder.Default
    private List<String> includeTags = new ArrayList<>();

    /**
     * Tags à exclure.
     * Les scénarios avec ces tags seront exclus.
     */
    @Builder.Default
    private List<String> excludeTags = new ArrayList<>();

    /**
     * Exécution parallèle des scénarios.
     */
    @Builder.Default
    private boolean parallel = false;

    /**
     * Nombre de threads pour l'exécution parallèle.
     */
    @Builder.Default
    private int parallelism = 4;

    /**
     * Arrêter l'exécution au premier échec.
     */
    @Builder.Default
    private boolean stopOnFailure = false;

    /**
     * Arrêter si un scénario critique échoue.
     */
    @Builder.Default
    private boolean stopOnCriticalFailure = true;

    /**
     * Timeout global de la suite en secondes.
     */
    @Builder.Default
    private int globalTimeoutSeconds = 1800; // 30 minutes

    /**
     * Timeout par scénario en secondes.
     */
    @Builder.Default
    private int scenarioTimeoutSeconds = 300; // 5 minutes

    /**
     * Timeout par step en millisecondes.
     */
    @Builder.Default
    private long stepTimeoutMs = 30000; // 30 secondes

    /**
     * Nombre de tentatives par scénario en cas d'échec.
     */
    @Builder.Default
    private int retryCount = 2;

    /**
     * Délai entre les tentatives en millisecondes.
     */
    @Builder.Default
    private long retryDelayMs = 1000;

    /**
     * URL du CSMS pour les tests.
     */
    private String csmsUrl;

    /**
     * Préfixe des Charge Point IDs générés.
     */
    @Builder.Default
    private String cpIdPrefix = "TNR_CP_";

    /**
     * IdTag par défaut.
     */
    @Builder.Default
    private String defaultIdTag = "TNR_TAG_001";

    /**
     * Environnement d'exécution (dev, staging, prod).
     */
    @Builder.Default
    private String environment = "dev";

    /**
     * Variables globales injectées dans tous les scénarios.
     */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    /**
     * Headers HTTP additionnels.
     */
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /**
     * Activer le mode dry-run (validation sans exécution).
     */
    @Builder.Default
    private boolean dryRun = false;

    /**
     * Activer le mode verbose (logs détaillés).
     */
    @Builder.Default
    private boolean verbose = false;

    /**
     * Générer un rapport de couverture.
     */
    @Builder.Default
    private boolean generateCoverageReport = true;

    /**
     * Comparer avec une baseline.
     */
    private String baselineExecutionId;

    /**
     * Répertoire des fichiers .feature.
     */
    private String featureDirectory;

    /**
     * Fichiers .feature spécifiques à exécuter.
     */
    @Builder.Default
    private List<String> featureFiles = new ArrayList<>();

    // =========================================================================
    // Builder helpers
    // =========================================================================

    /**
     * Ajoute un scénario.
     */
    public TnrSuiteConfig withScenario(String scenarioId) {
        this.scenarios.add(scenarioId);
        return this;
    }

    /**
     * Ajoute plusieurs scénarios.
     */
    public TnrSuiteConfig withScenarios(String... scenarioIds) {
        for (String id : scenarioIds) {
            this.scenarios.add(id);
        }
        return this;
    }

    /**
     * Ajoute un tag à inclure.
     */
    public TnrSuiteConfig withIncludeTag(String tag) {
        this.includeTags.add(tag);
        return this;
    }

    /**
     * Ajoute un tag à exclure.
     */
    public TnrSuiteConfig withExcludeTag(String tag) {
        this.excludeTags.add(tag);
        return this;
    }

    /**
     * Ajoute une variable globale.
     */
    public TnrSuiteConfig withVariable(String key, Object value) {
        this.variables.put(key, value);
        return this;
    }

    /**
     * Active l'exécution parallèle.
     */
    public TnrSuiteConfig parallel(int threads) {
        this.parallel = true;
        this.parallelism = threads;
        return this;
    }

    /**
     * Configure le retry.
     */
    public TnrSuiteConfig withRetry(int count, long delayMs) {
        this.retryCount = count;
        this.retryDelayMs = delayMs;
        return this;
    }

    /**
     * Vérifie si la configuration utilise des scénarios spécifiques.
     */
    public boolean hasSpecificScenarios() {
        return scenarios != null && !scenarios.isEmpty();
    }

    /**
     * Vérifie si des tags de filtrage sont définis.
     */
    public boolean hasTagFilters() {
        return (includeTags != null && !includeTags.isEmpty()) ||
               (excludeTags != null && !excludeTags.isEmpty());
    }

    /**
     * Crée une configuration par défaut.
     */
    public static TnrSuiteConfig defaultConfig() {
        return TnrSuiteConfig.builder()
                .suiteName("Default Suite")
                .build();
    }

    /**
     * Crée une configuration pour un test rapide (smoke test).
     */
    public static TnrSuiteConfig smokeTest() {
        return TnrSuiteConfig.builder()
                .suiteName("Smoke Test")
                .includeTags(List.of("smoke", "critical"))
                .stopOnFailure(true)
                .scenarioTimeoutSeconds(60)
                .retryCount(0)
                .build();
    }

    /**
     * Crée une configuration pour un test de régression complet.
     */
    public static TnrSuiteConfig fullRegression() {
        return TnrSuiteConfig.builder()
                .suiteName("Full Regression")
                .excludeTags(List.of("wip", "skip"))
                .parallel(true)
                .parallelism(Runtime.getRuntime().availableProcessors())
                .stopOnFailure(false)
                .generateCoverageReport(true)
                .retryCount(2)
                .build();
    }
}
