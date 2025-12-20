package com.evse.simulator.tnr.engine;

import com.evse.simulator.tnr.model.*;
import com.evse.simulator.tnr.steps.StepRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Exécuteur de scénarios TNR.
 * <p>
 * Responsable de l'exécution d'un scénario individuel avec gestion des:
 * - Steps et Background
 * - Timeout et Retry
 * - Contexte partagé
 * - Scenario Outline avec Examples
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioExecutor {

    private final StepRegistry stepRegistry;

    // Executor pour les timeouts
    private final ExecutorService timeoutExecutor = Executors.newCachedThreadPool();

    /**
     * Exécute un scénario avec sa configuration.
     *
     * @param scenario Le scénario à exécuter
     * @param config   Configuration d'exécution (optionnel)
     * @return Résultat de l'exécution
     */
    public TnrResult execute(TnrScenario scenario, TnrSuiteConfig config) {
        String executionId = UUID.randomUUID().toString();
        log.info("[TNR] Executing scenario '{}' (id={}, executionId={})",
                scenario.getName(), scenario.getId(), executionId);

        TnrResult result = TnrResult.builder()
                .executionId(executionId)
                .scenario(scenario)
                .status(TnrResult.Status.RUNNING)
                .startTime(Instant.now())
                .build();

        // Créer le contexte d'exécution
        TnrContext context = createContext(scenario, config);

        try {
            // Vérifier le timeout global
            long timeoutMs = config != null ?
                    config.getScenarioTimeoutSeconds() * 1000L :
                    scenario.getTimeoutSeconds() * 1000L;

            Future<TnrResult> future = timeoutExecutor.submit(() ->
                    executeWithContext(scenario, result, context, config));

            TnrResult executedResult = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return executedResult;

        } catch (TimeoutException e) {
            log.error("[TNR] Scenario '{}' timed out", scenario.getName());
            result.setStatus(TnrResult.Status.ERROR);
            result.setErrorMessage("Scenario execution timed out");
            return finalizeResult(result, context);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[TNR] Scenario '{}' interrupted", scenario.getName());
            result.setStatus(TnrResult.Status.ERROR);
            result.setErrorMessage("Scenario execution interrupted");
            return finalizeResult(result, context);

        } catch (ExecutionException e) {
            log.error("[TNR] Scenario '{}' failed with exception", scenario.getName(), e.getCause());
            result.setStatus(TnrResult.Status.ERROR);
            result.setErrorMessage(e.getCause().getMessage());
            result.setStackTrace(getStackTrace(e.getCause()));
            return finalizeResult(result, context);
        }
    }

    /**
     * Exécute un scénario avec retry.
     *
     * @param scenario   Le scénario à exécuter
     * @param config     Configuration d'exécution
     * @param maxRetries Nombre maximum de tentatives
     * @return Résultat de l'exécution
     */
    public TnrResult executeWithRetry(TnrScenario scenario, TnrSuiteConfig config, int maxRetries) {
        TnrResult result = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            result = execute(scenario, config);

            if (result.isSuccess()) {
                log.info("[TNR] Scenario '{}' passed on attempt {}", scenario.getName(), attempt + 1);
                return result;
            }

            if (attempt < maxRetries) {
                log.warn("[TNR] Scenario '{}' failed, retrying ({}/{})",
                        scenario.getName(), attempt + 1, maxRetries);
                result.setRetryCount(attempt + 1);

                // Attendre avant le retry avec backoff exponentiel
                try {
                    long delay = config != null ? config.getRetryDelayMs() : 1000L;
                    Thread.sleep(delay * (attempt + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Exécute le scénario avec le contexte.
     */
    private TnrResult executeWithContext(TnrScenario scenario, TnrResult result,
                                          TnrContext context, TnrSuiteConfig config) {
        try {
            // Scenario Outline avec Examples
            if (scenario.isOutline()) {
                return executeOutline(scenario, result, context, config);
            }

            // Exécuter le Background
            if (!executeBackground(scenario, result, context, config)) {
                result.setStatus(TnrResult.Status.FAILED);
                return finalizeResult(result, context);
            }

            // Exécuter les Steps
            if (!executeSteps(scenario.getSteps(), result, context, config)) {
                result.setStatus(TnrResult.Status.FAILED);
                return finalizeResult(result, context);
            }

            // Succès
            result.setStatus(TnrResult.Status.PASSED);
            log.info("[TNR] Scenario '{}' PASSED in {}ms",
                    scenario.getName(), result.getDurationMs());

        } catch (Exception e) {
            log.error("[TNR] Scenario '{}' ERROR: {}", scenario.getName(), e.getMessage(), e);
            result.setStatus(TnrResult.Status.ERROR);
            result.setErrorMessage(e.getMessage());
            result.setStackTrace(getStackTrace(e));
        }

        return finalizeResult(result, context);
    }

    /**
     * Exécute un Scenario Outline avec Examples.
     */
    private TnrResult executeOutline(TnrScenario scenario, TnrResult result,
                                      TnrContext context, TnrSuiteConfig config) {
        List<TnrStepResult> allStepResults = new ArrayList<>();
        boolean allPassed = true;
        int exampleIndex = 0;

        for (Map<String, String> example : scenario.getExamples()) {
            exampleIndex++;
            log.debug("[TNR] Executing outline example {}/{}: {}",
                    exampleIndex, scenario.getExamples().size(), example);

            // Injecter les variables de l'example
            for (Map.Entry<String, String> entry : example.entrySet()) {
                context.set(entry.getKey(), entry.getValue());
            }

            // Background pour chaque example
            for (TnrStep step : scenario.getBackgroundSteps()) {
                TnrStep resolved = resolveVariables(step, context);
                TnrStepResult stepResult = executeStep(resolved, context, config);
                allStepResults.add(stepResult);
                if (!stepResult.isSuccess()) {
                    allPassed = false;
                    if (shouldStopOnFailure(config)) break;
                }
            }

            // Steps pour chaque example
            for (TnrStep step : scenario.getSteps()) {
                TnrStep resolved = resolveVariables(step, context);
                TnrStepResult stepResult = executeStep(resolved, context, config);
                allStepResults.add(stepResult);
                if (!stepResult.isSuccess() && !step.isOptional()) {
                    allPassed = false;
                    if (shouldStopOnFailure(config)) break;
                }
            }
        }

        result.setStepResults(allStepResults);
        result.setStatus(allPassed ? TnrResult.Status.PASSED : TnrResult.Status.FAILED);

        return finalizeResult(result, context);
    }

    /**
     * Exécute les steps de Background.
     */
    private boolean executeBackground(TnrScenario scenario, TnrResult result,
                                       TnrContext context, TnrSuiteConfig config) {
        List<TnrStep> backgroundSteps = scenario.getBackgroundSteps();
        if (backgroundSteps == null || backgroundSteps.isEmpty()) {
            return true;
        }

        log.debug("[TNR] Executing {} background steps", backgroundSteps.size());

        for (TnrStep step : backgroundSteps) {
            TnrStepResult stepResult = executeStep(step, context, config);
            result.getStepResults().add(stepResult);

            if (!stepResult.isSuccess()) {
                log.error("[TNR] Background step failed: {}", step.getFullText());
                result.setErrorMessage("Background failed: " + step.getFullText());
                return false;
            }
        }

        return true;
    }

    /**
     * Exécute une liste de steps.
     */
    private boolean executeSteps(List<TnrStep> steps, TnrResult result,
                                  TnrContext context, TnrSuiteConfig config) {
        if (steps == null || steps.isEmpty()) {
            return true;
        }

        for (int i = 0; i < steps.size(); i++) {
            TnrStep step = steps.get(i);
            log.debug("[TNR] Step {}/{}: {} {}", i + 1, steps.size(), step.getType(), step.getText());

            TnrStepResult stepResult = executeStep(step, context, config);
            result.getStepResults().add(stepResult);

            if (!stepResult.isSuccess()) {
                if (step.isOptional()) {
                    log.warn("[TNR] Optional step failed (continuing): {}", step.getFullText());
                } else {
                    log.error("[TNR] Step failed: {}", step.getFullText());
                    result.setErrorMessage("Step failed: " + step.getFullText());
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Exécute un step individuel avec timeout.
     */
    public TnrStepResult executeStep(TnrStep step, TnrContext context, TnrSuiteConfig config) {
        TnrStepResult result = TnrStepResult.builder()
                .step(step)
                .status(TnrStepResult.Status.PENDING)
                .startTime(Instant.now())
                .build();

        long timeoutMs = step.getTimeoutMs();
        if (config != null && config.getStepTimeoutMs() > 0) {
            timeoutMs = config.getStepTimeoutMs();
        }

        try {
            Future<Boolean> future = timeoutExecutor.submit(() ->
                    stepRegistry.executeStep(step, context, result));

            boolean executed = future.get(timeoutMs, TimeUnit.MILLISECONDS);

            if (!executed) {
                result.setStatus(TnrStepResult.Status.UNDEFINED);
                result.setErrorMessage("No handler found for step: " + step.getText());
                log.warn("[TNR] Undefined step: {}", step.getText());
            } else if (result.getStatus() == TnrStepResult.Status.PENDING) {
                result.setStatus(TnrStepResult.Status.PASSED);
            }

        } catch (TimeoutException e) {
            result.setStatus(TnrStepResult.Status.ERROR);
            result.setErrorMessage("Step timed out after " + timeoutMs + "ms");
            log.error("[TNR] Step timed out: {}", step.getText());

        } catch (AssertionError e) {
            result.setStatus(TnrStepResult.Status.FAILED);
            result.setErrorMessage(e.getMessage());
            log.debug("[TNR] Step assertion failed: {}", e.getMessage());

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            result.setStatus(TnrStepResult.Status.ERROR);
            result.setErrorMessage(cause.getMessage());
            result.setStackTrace(getStackTrace(cause));
            log.error("[TNR] Step error: {}", cause.getMessage());

        } finally {
            result.setEndTime(Instant.now());
            result.setDurationMs(
                    result.getEndTime().toEpochMilli() - result.getStartTime().toEpochMilli());
        }

        return result;
    }

    /**
     * Crée le contexte d'exécution.
     */
    private TnrContext createContext(TnrScenario scenario, TnrSuiteConfig config) {
        TnrContext context = new TnrContext();

        // Injecter les paramètres du scénario
        if (scenario.getParameters() != null) {
            for (Map.Entry<String, Object> entry : scenario.getParameters().entrySet()) {
                context.set(entry.getKey(), entry.getValue());
            }
        }

        // Injecter les variables de la config
        if (config != null && config.getVariables() != null) {
            for (Map.Entry<String, Object> entry : config.getVariables().entrySet()) {
                context.set(entry.getKey(), entry.getValue());
            }

            // Variables de configuration
            if (config.getCsmsUrl() != null) {
                context.set("csmsUrl", config.getCsmsUrl());
            }
            if (config.getDefaultIdTag() != null) {
                context.set("idTag", config.getDefaultIdTag());
            }
            context.set("cpIdPrefix", config.getCpIdPrefix());
            context.set("environment", config.getEnvironment());
        }

        return context;
    }

    /**
     * Résout les variables dans un step.
     */
    private TnrStep resolveVariables(TnrStep step, TnrContext context) {
        String resolvedText = context.resolveVariables(step.getText());

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
                .docString(step.getDocString() != null ?
                        context.resolveVariables(step.getDocString()) : null)
                .sourceLine(step.getSourceLine())
                .optional(step.isOptional())
                .timeoutMs(step.getTimeoutMs())
                .build();
    }

    /**
     * Finalise le résultat avec les métriques.
     */
    private TnrResult finalizeResult(TnrResult result, TnrContext context) {
        result.setEndTime(Instant.now());
        result.setDurationMs(
                result.getEndTime().toEpochMilli() - result.getStartTime().toEpochMilli());
        result.setContext(context.snapshot());
        result.getMetrics().put("messageCount", context.getMessageHistory().size());
        result.getMetrics().put("activeSessionCount", context.getActiveSessions().size());
        return result;
    }

    /**
     * Vérifie si on doit s'arrêter sur échec.
     */
    private boolean shouldStopOnFailure(TnrSuiteConfig config) {
        return config != null && config.isStopOnFailure();
    }

    /**
     * Convertit une exception en stack trace.
     */
    private String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append("\n");
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append("\tat ").append(el).append("\n");
            if (sb.length() > 2000) {
                sb.append("\t... truncated");
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Arrête l'exécuteur proprement.
     */
    public void shutdown() {
        timeoutExecutor.shutdown();
        try {
            if (!timeoutExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                timeoutExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
