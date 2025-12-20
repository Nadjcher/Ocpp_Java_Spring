package com.evse.simulator.tnr.engine;

import com.evse.simulator.tnr.model.*;
import com.evse.simulator.tnr.steps.StepRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Moteur d'exécution TNR principal.
 * Orchestre le chargement, l'exécution et le reporting des scénarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TnrEngine {

    private final GherkinParser gherkinParser;
    private final StepRegistry stepRegistry;

    private final ExecutorService parallelExecutor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
    );

    // =========================================================================
    // Chargement des scénarios
    // =========================================================================

    /**
     * Charge tous les scénarios d'un répertoire.
     */
    public List<TnrScenario> loadScenarios(Path directory) throws IOException {
        List<TnrScenario> scenarios = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> featureFiles = paths
                .filter(p -> p.toString().endsWith(".feature"))
                .toList();

            for (Path file : featureFiles) {
                try {
                    scenarios.addAll(gherkinParser.parseFile(file));
                } catch (Exception e) {
                    log.error("Error loading feature file: {}", file, e);
                }
            }
        }

        log.info("Loaded {} scenarios from {}", scenarios.size(), directory);
        return scenarios;
    }

    /**
     * Charge les scénarios filtrés par tags.
     */
    public List<TnrScenario> loadScenariosByTags(Path directory, List<String> includeTags, List<String> excludeTags) throws IOException {
        return loadScenarios(directory).stream()
            .filter(s -> matchesTags(s, includeTags, excludeTags))
            .toList();
    }

    /**
     * Vérifie si un scénario correspond aux filtres de tags.
     */
    private boolean matchesTags(TnrScenario scenario, List<String> includeTags, List<String> excludeTags) {
        if (includeTags != null && !includeTags.isEmpty()) {
            boolean hasIncludeTag = scenario.getTags().stream().anyMatch(includeTags::contains);
            if (!hasIncludeTag) return false;
        }
        if (excludeTags != null && !excludeTags.isEmpty()) {
            boolean hasExcludeTag = scenario.getTags().stream().anyMatch(excludeTags::contains);
            if (hasExcludeTag) return false;
        }
        return true;
    }

    // =========================================================================
    // Exécution des scénarios
    // =========================================================================

    /**
     * Exécute une suite de scénarios.
     */
    public TnrSuiteResult executeSuite(List<TnrScenario> scenarios, String suiteName) {
        String executionId = UUID.randomUUID().toString();
        log.info("Starting TNR suite '{}' with {} scenarios (executionId={})",
            suiteName, scenarios.size(), executionId);

        TnrSuiteResult suiteResult = TnrSuiteResult.builder()
            .executionId(executionId)
            .suiteName(suiteName)
            .startTime(Instant.now())
            .build();

        // Trier par priorité
        List<TnrScenario> sortedScenarios = scenarios.stream()
            .filter(TnrScenario::isEnabled)
            .sorted(Comparator.comparingInt(TnrScenario::getPriority))
            .toList();

        // Exécuter séquentiellement
        for (TnrScenario scenario : sortedScenarios) {
            TnrResult result = executeScenario(scenario);
            suiteResult.addScenarioResult(result);

            // Arrêter si scénario critique échoue
            if (scenario.isCritical() && !result.isSuccess()) {
                log.error("Critical scenario '{}' failed, stopping suite", scenario.getName());
                break;
            }
        }

        suiteResult.setEndTime(Instant.now());
        suiteResult.setDurationMs(
            suiteResult.getEndTime().toEpochMilli() - suiteResult.getStartTime().toEpochMilli()
        );

        log.info("TNR suite '{}' completed: {}/{} passed ({}%)",
            suiteName,
            suiteResult.getPassedCount(),
            suiteResult.getTotalScenarios(),
            String.format("%.1f", suiteResult.getSuccessRate()));

        return suiteResult;
    }

    /**
     * Exécute des scénarios en parallèle.
     */
    public TnrSuiteResult executeSuiteParallel(List<TnrScenario> scenarios, String suiteName, int parallelism) {
        String executionId = UUID.randomUUID().toString();
        log.info("Starting parallel TNR suite '{}' with {} scenarios, parallelism={}",
            suiteName, scenarios.size(), parallelism);

        TnrSuiteResult suiteResult = TnrSuiteResult.builder()
            .executionId(executionId)
            .suiteName(suiteName)
            .startTime(Instant.now())
            .build();

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);

        List<TnrScenario> enabledScenarios = scenarios.stream()
            .filter(TnrScenario::isEnabled)
            .filter(s -> !s.isCritical()) // Scénarios critiques en séquentiel
            .toList();

        List<TnrScenario> criticalScenarios = scenarios.stream()
            .filter(TnrScenario::isEnabled)
            .filter(TnrScenario::isCritical)
            .sorted(Comparator.comparingInt(TnrScenario::getPriority))
            .toList();

        // D'abord les critiques en séquentiel
        for (TnrScenario scenario : criticalScenarios) {
            TnrResult result = executeScenario(scenario);
            suiteResult.addScenarioResult(result);
            if (!result.isSuccess()) {
                log.error("Critical scenario '{}' failed, stopping suite", scenario.getName());
                executor.shutdownNow();
                suiteResult.setEndTime(Instant.now());
                return suiteResult;
            }
        }

        // Puis les non-critiques en parallèle
        List<Future<TnrResult>> futures = enabledScenarios.stream()
            .map(scenario -> executor.submit(() -> executeScenario(scenario)))
            .toList();

        for (Future<TnrResult> future : futures) {
            try {
                TnrResult result = future.get(5, TimeUnit.MINUTES);
                suiteResult.addScenarioResult(result);
            } catch (Exception e) {
                log.error("Error executing scenario in parallel", e);
            }
        }

        executor.shutdown();
        suiteResult.setEndTime(Instant.now());
        suiteResult.setDurationMs(
            suiteResult.getEndTime().toEpochMilli() - suiteResult.getStartTime().toEpochMilli()
        );

        return suiteResult;
    }

    /**
     * Exécute un seul scénario.
     */
    public TnrResult executeScenario(TnrScenario scenario) {
        String executionId = UUID.randomUUID().toString();
        log.info("Executing scenario '{}' (id={})", scenario.getName(), scenario.getId());

        TnrResult result = TnrResult.builder()
            .executionId(executionId)
            .scenario(scenario)
            .status(TnrResult.Status.RUNNING)
            .startTime(Instant.now())
            .build();

        TnrContext context = new TnrContext();

        try {
            // Expand Scenario Outline si nécessaire
            if (scenario.isOutline()) {
                return executeScenarioOutline(scenario, result, context);
            }

            // Exécuter Background
            for (TnrStep step : scenario.getBackgroundSteps()) {
                TnrStepResult stepResult = executeStep(step, context);
                result.getStepResults().add(stepResult);
                if (!stepResult.isSuccess()) {
                    result.setStatus(TnrResult.Status.FAILED);
                    result.setErrorMessage("Background step failed: " + step.getText());
                    break;
                }
            }

            // Exécuter Steps si background OK
            if (result.getStatus() != TnrResult.Status.FAILED) {
                for (TnrStep step : scenario.getSteps()) {
                    TnrStepResult stepResult = executeStep(step, context);
                    result.getStepResults().add(stepResult);

                    if (!stepResult.isSuccess() && !step.isOptional()) {
                        result.setStatus(TnrResult.Status.FAILED);
                        result.setErrorMessage("Step failed: " + step.getText());
                        result.setStackTrace(stepResult.getStackTrace());
                        break;
                    }
                }
            }

            // Si tous les steps ont réussi
            if (result.getStatus() == TnrResult.Status.RUNNING) {
                result.setStatus(TnrResult.Status.PASSED);
            }

        } catch (Exception e) {
            log.error("Error executing scenario '{}'", scenario.getName(), e);
            result.setStatus(TnrResult.Status.ERROR);
            result.setErrorMessage(e.getMessage());
            result.setStackTrace(getStackTraceString(e));
        } finally {
            result.setEndTime(Instant.now());
            result.setDurationMs(
                result.getEndTime().toEpochMilli() - result.getStartTime().toEpochMilli()
            );
            result.setContext(context.snapshot());
        }

        log.info("Scenario '{}' completed: {} in {}ms",
            scenario.getName(), result.getStatus(), result.getDurationMs());

        return result;
    }

    /**
     * Exécute un Scenario Outline avec tous ses Examples.
     */
    private TnrResult executeScenarioOutline(TnrScenario scenario, TnrResult result, TnrContext context) {
        List<TnrStepResult> allStepResults = new ArrayList<>();
        boolean allPassed = true;

        for (Map<String, String> example : scenario.getExamples()) {
            log.debug("Executing outline with example: {}", example);

            // Injecter les variables de l'example
            for (Map.Entry<String, String> entry : example.entrySet()) {
                context.set(entry.getKey(), entry.getValue());
            }

            // Exécuter Background
            for (TnrStep step : scenario.getBackgroundSteps()) {
                TnrStep resolvedStep = resolveStepVariables(step, context);
                TnrStepResult stepResult = executeStep(resolvedStep, context);
                allStepResults.add(stepResult);
                if (!stepResult.isSuccess()) {
                    allPassed = false;
                }
            }

            // Exécuter Steps
            for (TnrStep step : scenario.getSteps()) {
                TnrStep resolvedStep = resolveStepVariables(step, context);
                TnrStepResult stepResult = executeStep(resolvedStep, context);
                allStepResults.add(stepResult);
                if (!stepResult.isSuccess() && !step.isOptional()) {
                    allPassed = false;
                }
            }
        }

        result.setStepResults(allStepResults);
        result.setStatus(allPassed ? TnrResult.Status.PASSED : TnrResult.Status.FAILED);

        return result;
    }

    /**
     * Résout les variables dans un step.
     */
    private TnrStep resolveStepVariables(TnrStep step, TnrContext context) {
        String resolvedText = context.resolveVariables(step.getText());

        // Résoudre aussi les DataTable
        List<Map<String, String>> resolvedTable = new ArrayList<>();
        if (step.hasDataTable()) {
            for (Map<String, String> row : step.getDataTable()) {
                Map<String, String> resolvedRow = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : row.entrySet()) {
                    resolvedRow.put(entry.getKey(), context.resolveVariables(entry.getValue()));
                }
                resolvedTable.add(resolvedRow);
            }
        }

        return TnrStep.builder()
            .type(step.getType())
            .text(resolvedText)
            .pattern(step.getPattern())
            .parameters(step.getParameters())
            .dataTable(resolvedTable)
            .docString(step.getDocString() != null ? context.resolveVariables(step.getDocString()) : null)
            .sourceLine(step.getSourceLine())
            .optional(step.isOptional())
            .timeoutMs(step.getTimeoutMs())
            .build();
    }

    /**
     * Exécute un step individuel.
     */
    private TnrStepResult executeStep(TnrStep step, TnrContext context) {
        log.debug("Executing step: {} {}", step.getType(), step.getText());

        TnrStepResult result = TnrStepResult.builder()
            .step(step)
            .status(TnrStepResult.Status.PENDING)
            .startTime(Instant.now())
            .build();

        try {
            // Trouver et exécuter le handler
            boolean executed = stepRegistry.executeStep(step, context, result);

            if (!executed) {
                result.setStatus(TnrStepResult.Status.UNDEFINED);
                result.setErrorMessage("No handler found for step: " + step.getText());
                log.warn("Undefined step: {}", step.getText());
            } else if (result.getStatus() == TnrStepResult.Status.PENDING) {
                // Si le handler n'a pas explicitement défini le statut
                result.setStatus(TnrStepResult.Status.PASSED);
            }

        } catch (AssertionError e) {
            result.setStatus(TnrStepResult.Status.FAILED);
            result.setErrorMessage(e.getMessage());
            result.setStackTrace(getStackTraceString(e));
            log.debug("Step assertion failed: {}", e.getMessage());
        } catch (Exception e) {
            result.setStatus(TnrStepResult.Status.ERROR);
            result.setErrorMessage(e.getMessage());
            result.setStackTrace(getStackTraceString(e));
            log.error("Step execution error: {}", e.getMessage(), e);
        } finally {
            result.setEndTime(Instant.now());
            result.setDurationMs(
                result.getEndTime().toEpochMilli() - result.getStartTime().toEpochMilli()
            );
        }

        return result;
    }

    /**
     * Convertit une exception en stack trace string.
     */
    private String getStackTraceString(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append("\n");
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append("\tat ").append(element).append("\n");
            if (sb.length() > 2000) {
                sb.append("\t... truncated");
                break;
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // Retry logic
    // =========================================================================

    /**
     * Exécute un scénario avec retry.
     */
    public TnrResult executeScenarioWithRetry(TnrScenario scenario) {
        int maxRetries = scenario.getMaxRetries();
        TnrResult result = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            result = executeScenario(scenario);

            if (result.isSuccess()) {
                break;
            }

            if (attempt < maxRetries) {
                log.info("Scenario '{}' failed, retrying ({}/{})",
                    scenario.getName(), attempt + 1, maxRetries);
                result.setRetryCount(attempt + 1);

                // Pause avant retry
                try {
                    Thread.sleep(1000 * (attempt + 1)); // Backoff exponentiel
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return result;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Arrête le moteur proprement.
     */
    public void shutdown() {
        parallelExecutor.shutdown();
        try {
            if (!parallelExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                parallelExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            parallelExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
