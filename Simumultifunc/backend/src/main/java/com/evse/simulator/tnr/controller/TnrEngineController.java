package com.evse.simulator.tnr.controller;

import com.evse.simulator.tnr.engine.ScenarioExecutor;
import com.evse.simulator.tnr.engine.TnrEngine;
import com.evse.simulator.tnr.model.*;
import com.evse.simulator.tnr.model.TnrComparisonResult.ComparisonOptions;
import com.evse.simulator.tnr.service.TnrComparisonService;
import com.evse.simulator.tnr.service.TnrRecordingService;
import com.evse.simulator.tnr.service.TnrSignatureService;
import com.evse.simulator.tnr.service.TnrTemplateService;
import com.evse.simulator.tnr.service.TnrTemplateService.TemplateParams;
import com.evse.simulator.tnr.service.TnrValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contrôleur REST pour le moteur TNR Gherkin.
 * <p>
 * Expose les endpoints pour:
 * - Lister les scénarios disponibles
 * - Exécuter un scénario individuel
 * - Exécuter une suite de scénarios
 * - Récupérer les résultats d'exécution
 * </p>
 *
 * @example
 * <pre>
 * // Exécuter une suite
 * POST /api/tnr/suite
 * {
 *   "scenarios": ["SC001", "SC002", "SC003"],
 *   "parallel": false,
 *   "stopOnFailure": false
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/tnr")
@Tag(name = "TNR Engine", description = "Moteur d'exécution des tests non-régressifs Gherkin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class TnrEngineController {

    private final TnrEngine tnrEngine;
    private final ScenarioExecutor scenarioExecutor;
    private final TnrRecordingService recordingService;
    private final TnrComparisonService comparisonService;
    private final TnrSignatureService signatureService;
    private final TnrTemplateService templateService;
    private final TnrValidationService validationService;

    // Cache des scénarios chargés
    private final Map<String, TnrScenario> scenarioCache = new ConcurrentHashMap<>();

    // Résultats d'exécution
    private final Map<String, TnrResult> executionResults = new ConcurrentHashMap<>();
    private final Map<String, TnrSuiteResult> suiteResults = new ConcurrentHashMap<>();

    // Exécutions en cours
    private final Set<String> runningScenarios = ConcurrentHashMap.newKeySet();
    private final Set<String> runningSuites = ConcurrentHashMap.newKeySet();

    // Répertoire par défaut des features
    private static final String DEFAULT_FEATURE_DIR = "src/test/resources/features";

    // =========================================================================
    // Scénarios
    // =========================================================================

    @GetMapping("/engine/scenarios")
    @Operation(summary = "Liste tous les scénarios Gherkin disponibles")
    public ResponseEntity<List<TnrScenario>> getAllScenarios(
            @RequestParam(required = false) String directory,
            @RequestParam(required = false) List<String> tags) {

        try {
            Path featureDir = directory != null ?
                    Paths.get(directory) : Paths.get(DEFAULT_FEATURE_DIR);

            List<TnrScenario> scenarios;
            if (tags != null && !tags.isEmpty()) {
                scenarios = tnrEngine.loadScenariosByTags(featureDir, tags, null);
            } else {
                scenarios = tnrEngine.loadScenarios(featureDir);
            }

            // Mettre en cache
            for (TnrScenario scenario : scenarios) {
                scenarioCache.put(scenario.getId(), scenario);
            }

            return ResponseEntity.ok(scenarios);

        } catch (IOException e) {
            log.error("Failed to load scenarios", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/engine/scenarios/{scenarioId}")
    @Operation(summary = "Récupère un scénario Gherkin par ID")
    public ResponseEntity<TnrScenario> getScenario(
            @PathVariable @Parameter(description = "ID du scénario") String scenarioId) {

        TnrScenario scenario = scenarioCache.get(scenarioId);
        if (scenario == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(scenario);
    }

    // =========================================================================
    // Exécution d'un scénario
    // =========================================================================

    @PostMapping("/engine/run/{scenarioId}")
    @Operation(summary = "Exécute un scénario Gherkin individuel")
    public ResponseEntity<TnrResult> runScenario(
            @PathVariable @Parameter(description = "ID du scénario") String scenarioId,
            @RequestBody(required = false) TnrSuiteConfig config) {

        TnrScenario scenario = scenarioCache.get(scenarioId);
        if (scenario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(TnrResult.builder()
                            .executionId(UUID.randomUUID().toString())
                            .status(TnrResult.Status.ERROR)
                            .errorMessage("Scenario not found: " + scenarioId)
                            .build());
        }

        if (runningScenarios.contains(scenarioId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(TnrResult.builder()
                            .executionId(UUID.randomUUID().toString())
                            .status(TnrResult.Status.ERROR)
                            .errorMessage("Scenario already running: " + scenarioId)
                            .build());
        }

        try {
            runningScenarios.add(scenarioId);
            log.info("Starting execution of scenario: {}", scenarioId);

            TnrResult result;
            int retryCount = config != null ? config.getRetryCount() : 0;

            if (retryCount > 0) {
                result = scenarioExecutor.executeWithRetry(scenario, config, retryCount);
            } else {
                result = scenarioExecutor.execute(scenario, config);
            }

            executionResults.put(result.getExecutionId(), result);

            HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.EXPECTATION_FAILED;
            return ResponseEntity.status(status).body(result);

        } finally {
            runningScenarios.remove(scenarioId);
        }
    }

    @PostMapping("/engine/run/{scenarioId}/async")
    @Operation(summary = "Exécute un scénario Gherkin de manière asynchrone")
    public ResponseEntity<Map<String, String>> runScenarioAsync(
            @PathVariable String scenarioId,
            @RequestBody(required = false) TnrSuiteConfig config) {

        TnrScenario scenario = scenarioCache.get(scenarioId);
        if (scenario == null) {
            return ResponseEntity.notFound().build();
        }

        String executionId = UUID.randomUUID().toString();

        CompletableFuture.runAsync(() -> {
            runningScenarios.add(scenarioId);
            try {
                TnrResult result = scenarioExecutor.execute(scenario, config);
                executionResults.put(executionId, result);
            } finally {
                runningScenarios.remove(scenarioId);
            }
        });

        return ResponseEntity.accepted().body(Map.of(
                "executionId", executionId,
                "scenarioId", scenarioId,
                "status", "RUNNING"
        ));
    }

    // =========================================================================
    // Exécution d'une suite
    // =========================================================================

    @PostMapping("/engine/suite")
    @Operation(summary = "Exécute une suite de scénarios Gherkin")
    public ResponseEntity<TnrSuiteResult> runSuite(
            @RequestBody TnrSuiteConfig config) {

        String suiteId = UUID.randomUUID().toString();
        log.info("Starting suite execution: {} with config: {}", suiteId, config);

        if (runningSuites.contains(config.getSuiteName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(TnrSuiteResult.builder()
                            .executionId(suiteId)
                            .suiteName(config.getSuiteName())
                            .build());
        }

        try {
            runningSuites.add(config.getSuiteName());

            // Récupérer les scénarios à exécuter
            List<TnrScenario> scenarios = getScenarioss(config);

            if (scenarios.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(TnrSuiteResult.builder()
                                .executionId(suiteId)
                                .suiteName(config.getSuiteName())
                                .build());
            }

            TnrSuiteResult result;

            if (config.isParallel()) {
                result = tnrEngine.executeSuiteParallel(scenarios,
                        config.getSuiteName(), config.getParallelism());
            } else {
                result = tnrEngine.executeSuite(scenarios, config.getSuiteName());
            }

            result.setConfiguration(Map.of(
                    "parallel", config.isParallel(),
                    "parallelism", config.getParallelism(),
                    "stopOnFailure", config.isStopOnFailure()
            ));

            suiteResults.put(result.getExecutionId(), result);

            HttpStatus status = result.isAllPassed() ? HttpStatus.OK : HttpStatus.EXPECTATION_FAILED;
            return ResponseEntity.status(status).body(result);

        } finally {
            runningSuites.remove(config.getSuiteName());
        }
    }

    @PostMapping("/engine/suite/async")
    @Operation(summary = "Exécute une suite Gherkin de manière asynchrone")
    public ResponseEntity<Map<String, String>> runSuiteAsync(
            @RequestBody TnrSuiteConfig config) {

        String executionId = UUID.randomUUID().toString();

        CompletableFuture.runAsync(() -> {
            runningSuites.add(config.getSuiteName());
            try {
                List<TnrScenario> scenarios = getScenarioss(config);
                TnrSuiteResult result;

                if (config.isParallel()) {
                    result = tnrEngine.executeSuiteParallel(scenarios,
                            config.getSuiteName(), config.getParallelism());
                } else {
                    result = tnrEngine.executeSuite(scenarios, config.getSuiteName());
                }

                suiteResults.put(executionId, result);
            } finally {
                runningSuites.remove(config.getSuiteName());
            }
        });

        return ResponseEntity.accepted().body(Map.of(
                "executionId", executionId,
                "suiteName", config.getSuiteName(),
                "status", "RUNNING"
        ));
    }

    // =========================================================================
    // Résultats
    // =========================================================================

    @GetMapping("/engine/results/{executionId}")
    @Operation(summary = "Récupère le résultat d'une exécution Gherkin")
    public ResponseEntity<?> getResult(
            @PathVariable @Parameter(description = "ID de l'exécution") String executionId) {

        // Chercher dans les résultats de scénarios
        TnrResult scenarioResult = executionResults.get(executionId);
        if (scenarioResult != null) {
            return ResponseEntity.ok(scenarioResult);
        }

        // Chercher dans les résultats de suites
        TnrSuiteResult suiteResult = suiteResults.get(executionId);
        if (suiteResult != null) {
            return ResponseEntity.ok(suiteResult);
        }

        // Vérifier si en cours
        if (runningScenarios.stream().anyMatch(s -> s.contains(executionId)) ||
            runningSuites.stream().anyMatch(s -> s.contains(executionId))) {
            return ResponseEntity.ok(Map.of(
                    "executionId", executionId,
                    "status", "RUNNING"
            ));
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/engine/results")
    @Operation(summary = "Liste tous les résultats d'exécution Gherkin")
    public ResponseEntity<Map<String, Object>> getAllResults(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String status) {

        List<TnrResult> scenarioResults = new ArrayList<>(executionResults.values());
        List<TnrSuiteResult> suiteResultsList = new ArrayList<>(suiteResults.values());

        // Filtrer par status si spécifié
        if (status != null) {
            TnrResult.Status filterStatus = TnrResult.Status.valueOf(status.toUpperCase());
            scenarioResults = scenarioResults.stream()
                    .filter(r -> r.getStatus() == filterStatus)
                    .toList();
        }

        // Limiter
        scenarioResults = scenarioResults.stream()
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .limit(limit)
                .toList();

        suiteResultsList = suiteResultsList.stream()
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .limit(limit)
                .toList();

        return ResponseEntity.ok(Map.of(
                "scenarios", scenarioResults,
                "suites", suiteResultsList,
                "totalScenarios", executionResults.size(),
                "totalSuites", suiteResults.size()
        ));
    }

    // =========================================================================
    // Status
    // =========================================================================

    @GetMapping("/engine/status")
    @Operation(summary = "Statut du moteur TNR Gherkin")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "running", !runningScenarios.isEmpty() || !runningSuites.isEmpty(),
                "runningScenarios", runningScenarios.size(),
                "runningSuites", runningSuites.size(),
                "cachedScenarios", scenarioCache.size(),
                "storedResults", executionResults.size(),
                "storedSuiteResults", suiteResults.size()
        ));
    }

    @GetMapping("/engine/running")
    @Operation(summary = "Liste des exécutions Gherkin en cours")
    public ResponseEntity<Map<String, Object>> getRunning() {
        return ResponseEntity.ok(Map.of(
                "scenarios", new ArrayList<>(runningScenarios),
                "suites", new ArrayList<>(runningSuites)
        ));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<TnrScenario> getScenarioss(TnrSuiteConfig config) {
        List<TnrScenario> scenarios = new ArrayList<>();

        // Par IDs spécifiques
        if (config.hasSpecificScenarios()) {
            for (String id : config.getScenarios()) {
                TnrScenario scenario = scenarioCache.get(id);
                if (scenario != null) {
                    scenarios.add(scenario);
                } else {
                    log.warn("Scenario not found in cache: {}", id);
                }
            }
            return scenarios;
        }

        // Par fichiers feature
        if (config.getFeatureFiles() != null && !config.getFeatureFiles().isEmpty()) {
            for (String file : config.getFeatureFiles()) {
                try {
                    scenarios.addAll(tnrEngine.loadScenarios(Paths.get(file)));
                } catch (IOException e) {
                    log.error("Failed to load feature file: {}", file, e);
                }
            }
            return scenarios;
        }

        // Par répertoire
        String dir = config.getFeatureDirectory() != null ?
                config.getFeatureDirectory() : DEFAULT_FEATURE_DIR;
        try {
            if (config.hasTagFilters()) {
                scenarios = tnrEngine.loadScenariosByTags(
                        Paths.get(dir),
                        config.getIncludeTags(),
                        config.getExcludeTags()
                );
            } else {
                scenarios = tnrEngine.loadScenarios(Paths.get(dir));
            }

            // Mettre en cache
            for (TnrScenario scenario : scenarios) {
                scenarioCache.put(scenario.getId(), scenario);
            }

        } catch (IOException e) {
            log.error("Failed to load scenarios from directory: {}", dir, e);
        }

        return scenarios;
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    @DeleteMapping("/engine/results")
    @Operation(summary = "Efface tous les résultats Gherkin")
    public ResponseEntity<Map<String, Integer>> clearResults() {
        int scenarioCount = executionResults.size();
        int suiteCount = suiteResults.size();

        executionResults.clear();
        suiteResults.clear();

        return ResponseEntity.ok(Map.of(
                "clearedScenarios", scenarioCount,
                "clearedSuites", suiteCount
        ));
    }

    @DeleteMapping("/engine/cache")
    @Operation(summary = "Vide le cache des scénarios Gherkin")
    public ResponseEntity<Map<String, Integer>> clearCache() {
        int count = scenarioCache.size();
        scenarioCache.clear();
        return ResponseEntity.ok(Map.of("cleared", count));
    }

    // =========================================================================
    // Recording (Enregistrement TNR amélioré)
    // =========================================================================

    @PostMapping("/recording/start")
    @Operation(summary = "Démarre l'enregistrement des événements TNR")
    public ResponseEntity<Map<String, Object>> startRecording(
            @RequestParam(required = false) String scenarioName) {

        String executionId = UUID.randomUUID().toString();
        boolean started = recordingService.startRecording(executionId, scenarioName);

        if (started) {
            return ResponseEntity.ok(Map.of(
                    "executionId", executionId,
                    "scenarioName", scenarioName != null ? scenarioName : "",
                    "status", "RECORDING"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "Recording already in progress",
                    "currentRecordingId", recordingService.getCurrentRecordingId()
            ));
        }
    }

    @PostMapping("/recording/stop")
    @Operation(summary = "Arrête l'enregistrement et sauvegarde l'exécution")
    public ResponseEntity<TnrExecution> stopRecording() {
        TnrExecution execution = recordingService.stopRecording();

        if (execution != null) {
            return ResponseEntity.ok(execution);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/recording/cancel")
    @Operation(summary = "Annule l'enregistrement en cours")
    public ResponseEntity<Map<String, String>> cancelRecording() {
        recordingService.cancelRecording();
        return ResponseEntity.ok(Map.of("status", "CANCELLED"));
    }

    @GetMapping("/recording/status")
    @Operation(summary = "Statut de l'enregistrement TNR")
    public ResponseEntity<Map<String, Object>> getRecordingStatus() {
        return ResponseEntity.ok(recordingService.getRecordingStats());
    }

    @GetMapping("/recording/events")
    @Operation(summary = "Événements en cours d'enregistrement")
    public ResponseEntity<List<TnrEvent>> getRecordingEvents(
            @RequestParam(defaultValue = "100") int limit) {

        List<TnrEvent> events = recordingService.getCurrentEvents();
        if (events.size() > limit) {
            events = events.subList(events.size() - limit, events.size());
        }
        return ResponseEntity.ok(events);
    }

    // =========================================================================
    // Executions (Exécutions enregistrées) - sous /recording/executions pour éviter conflit avec TNRController
    // =========================================================================

    @GetMapping("/recording/executions")
    @Operation(summary = "Liste toutes les exécutions TNR enregistrées (nouveau système)")
    public ResponseEntity<List<TnrExecution>> getAllRecordedExecutions() {
        return ResponseEntity.ok(recordingService.getAllExecutions());
    }

    @GetMapping("/recording/executions/{executionId}")
    @Operation(summary = "Récupère une exécution TNR par ID (nouveau système)")
    public ResponseEntity<TnrExecution> getRecordedExecution(
            @PathVariable @Parameter(description = "ID de l'exécution") String executionId) {

        return recordingService.getExecution(executionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/recording/executions/{executionId}")
    @Operation(summary = "Supprime une exécution TNR (nouveau système)")
    public ResponseEntity<Map<String, String>> deleteRecordedExecution(
            @PathVariable String executionId) {

        boolean deleted = recordingService.deleteExecution(executionId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("deleted", executionId));
        }
        return ResponseEntity.notFound().build();
    }

    // =========================================================================
    // Comparison (Comparaison TNR)
    // =========================================================================

    @PostMapping("/compare")
    @Operation(summary = "Compare deux exécutions TNR")
    public ResponseEntity<TnrComparisonResult> compare(
            @RequestBody CompareRequest request) {

        try {
            ComparisonOptions options = request.getOptions() != null ?
                    request.getOptions() : ComparisonOptions.defaults();

            TnrComparisonResult result = comparisonService.compare(
                    request.getBaselineId(),
                    request.getComparedId(),
                    options
            );

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/compare/{baselineId}/{comparedId}")
    @Operation(summary = "Compare deux exécutions TNR (simple)")
    public ResponseEntity<TnrComparisonResult> compareSimple(
            @PathVariable String baselineId,
            @PathVariable String comparedId,
            @RequestParam(defaultValue = "false") boolean criticalOnly,
            @RequestParam(defaultValue = "true") boolean ignoreHeartbeats) {

        try {
            ComparisonOptions options = ComparisonOptions.builder()
                    .criticalOnly(criticalOnly)
                    .ignoreHeartbeats(ignoreHeartbeats)
                    .build();

            TnrComparisonResult result = comparisonService.compare(
                    baselineId, comparedId, options);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/compare/baseline/{scenarioName}")
    @Operation(summary = "Compare avec la dernière baseline d'un scénario")
    public ResponseEntity<TnrComparisonResult> compareWithBaseline(
            @PathVariable String scenarioName,
            @RequestParam String comparedId) {

        try {
            TnrComparisonResult result = comparisonService.compareWithBaseline(
                    comparedId, scenarioName);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(TnrComparisonResult.builder()
                            .summary("Baseline not found for scenario: " + scenarioName)
                            .build());
        }
    }

    // =========================================================================
    // Signature
    // =========================================================================

    @GetMapping("/signature/{executionId}")
    @Operation(summary = "Récupère les signatures d'une exécution")
    public ResponseEntity<Map<String, Object>> getSignatures(
            @PathVariable String executionId) {

        return recordingService.getExecution(executionId)
                .map(exec -> {
                    List<TnrEvent> events = exec.getEvents();
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "executionId", executionId,
                            "signature", signatureService.computeSignature(events),
                            "criticalSignature", signatureService.computeCriticalSignature(events),
                            "lightSignature", signatureService.computeLightSignature(events),
                            "ocppSignature", signatureService.computeOcppSequenceSignature(events),
                            "eventCount", events.size()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================================
    // Templates (Scénarios prédéfinis)
    // =========================================================================

    @GetMapping("/templates")
    @Operation(summary = "Liste tous les templates TNR disponibles")
    public ResponseEntity<List<TnrScenario>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    @GetMapping("/templates/{templateId}")
    @Operation(summary = "Récupère un template TNR par ID")
    public ResponseEntity<TnrScenario> getTemplate(
            @PathVariable @Parameter(description = "ID du template") String templateId) {

        return templateService.getTemplate(templateId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/templates/{templateId}/instantiate")
    @Operation(summary = "Crée un scénario à partir d'un template")
    public ResponseEntity<TnrScenario> instantiateTemplate(
            @PathVariable @Parameter(description = "ID du template") String templateId,
            @RequestBody TemplateParams params) {

        try {
            TnrScenario scenario = templateService.instantiate(templateId, params);

            // Mettre en cache le nouveau scénario
            scenarioCache.put(scenario.getId(), scenario);

            return ResponseEntity.status(HttpStatus.CREATED).body(scenario);

        } catch (IllegalArgumentException e) {
            log.warn("Failed to instantiate template {}: {}", templateId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/templates/{templateId}/run")
    @Operation(summary = "Instantie et exécute un template directement")
    public ResponseEntity<TnrResult> runTemplate(
            @PathVariable String templateId,
            @RequestBody TemplateParams params) {

        try {
            // Instancier le template
            TnrScenario scenario = templateService.instantiate(templateId, params);
            scenarioCache.put(scenario.getId(), scenario);

            // Exécuter
            TnrResult result = scenarioExecutor.execute(scenario, null);
            executionResults.put(result.getExecutionId(), result);

            // Mettre à jour les métadonnées
            if (scenario.getMetadata() != null) {
                scenario.getMetadata().recordExecution(
                        result.isSuccess(),
                        result.getDurationMs(),
                        result.getExecutionId()
                );
            }

            HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.EXPECTATION_FAILED;
            return ResponseEntity.status(status).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(TnrResult.builder()
                            .executionId(UUID.randomUUID().toString())
                            .status(TnrResult.Status.ERROR)
                            .errorMessage("Template not found: " + templateId)
                            .build());
        }
    }

    // =========================================================================
    // Validation (Validation avec tolérances)
    // =========================================================================

    @PostMapping("/validate/{executionId}")
    @Operation(summary = "Valide une exécution contre les résultats attendus d'un scénario")
    public ResponseEntity<TnrValidationService.TnrValidationSummary> validateExecution(
            @PathVariable String executionId,
            @RequestParam(required = false) String scenarioId) {

        // Récupérer l'exécution
        TnrExecution execution = recordingService.getExecution(executionId).orElse(null);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        // Récupérer le scénario (par ID ou depuis l'exécution)
        String resolvedScenarioId = scenarioId != null ? scenarioId : execution.getScenarioId();
        TnrScenario scenario = scenarioCache.get(resolvedScenarioId);
        if (scenario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Valider
        TnrValidationService.TnrValidationSummary summary =
                validationService.validateExecution(execution, scenario);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/tolerances/presets")
    @Operation(summary = "Liste les presets de tolérances disponibles")
    public ResponseEntity<Map<String, TnrTolerances>> getTolerancePresets() {
        return ResponseEntity.ok(Map.of(
                "default", TnrTolerances.defaults(),
                "strict", TnrTolerances.strict(),
                "relaxed", TnrTolerances.relaxed()
        ));
    }

    // =========================================================================
    // DTOs pour les requêtes
    // =========================================================================

    /**
     * Requête de comparaison.
     */
    @lombok.Data
    public static class CompareRequest {
        private String baselineId;
        private String comparedId;
        private ComparisonOptions options;
    }
}
