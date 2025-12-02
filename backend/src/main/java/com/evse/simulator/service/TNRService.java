package com.evse.simulator.service;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.ExecutionDetail;
import com.evse.simulator.model.ExecutionMeta;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.TNRScenario;
import com.evse.simulator.model.TNRScenario.*;
import com.evse.simulator.repository.JsonFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.evse.simulator.model.TNREvent;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Service de gestion des Tests Non-Régressifs (TNR).
 * <p>
 * Permet de définir, exécuter et valider des scénarios de test.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TNRService implements com.evse.simulator.domain.service.TNRService {

    private final JsonFileRepository repository;
    private final SessionService sessionService;
    private final OCPPService ocppService;

    // Résultats en cours
    private final Map<String, TNRResult> runningTests = new ConcurrentHashMap<>();

    // Stockage des exécutions et événements pour TNR+
    private final Map<String, ExecutionDetail> executions = new ConcurrentHashMap<>();
    private final List<TNREvent> currentRecordingEvents = new CopyOnWriteArrayList<>();
    private volatile String currentRecordingId = null;

    // =========================================================================
    // TNR+ Methods
    // =========================================================================

    /**
     * Liste toutes les exécutions TNR disponibles.
     */
    public List<ExecutionMeta> listExecutions() {
        List<ExecutionMeta> metas = new ArrayList<>();
        for (ExecutionDetail detail : executions.values()) {
            ExecutionMeta meta = new ExecutionMeta();
            meta.setId(detail.id);
            meta.setScenarioName(detail.scenarioName);
            meta.setExecutedAt(detail.executedAt);
            meta.setEventCount(detail.events != null ? detail.events.size() : 0);
            meta.setSignature(detail.signature);
            metas.add(meta);
        }
        return metas;
    }

    /**
     * Récupère le détail d'une exécution.
     */
    public ExecutionDetail getExecution(String executionId) throws Exception {
        ExecutionDetail detail = executions.get(executionId);
        if (detail == null) {
            throw new IllegalArgumentException("Execution not found: " + executionId);
        }
        return detail;
    }

    /**
     * Enregistre un événement TNR.
     */
    public void recordEvent(TNREvent event) {
        if (currentRecordingId != null) {
            currentRecordingEvents.add(event);
            log.debug("TNR event recorded: {} - {}", event.getType(), event.getAction());
        }
    }

    /**
     * Démarre l'enregistrement d'une exécution.
     */
    public void startRecording(String executionId, String scenarioName) {
        currentRecordingId = executionId;
        currentRecordingEvents.clear();
        log.info("TNR recording started: {}", executionId);
    }

    /**
     * Arrête l'enregistrement et sauvegarde l'exécution.
     */
    public void stopRecording() {
        if (currentRecordingId != null) {
            ExecutionDetail detail = new ExecutionDetail();
            detail.id = currentRecordingId;
            detail.executedAt = LocalDateTime.now();
            detail.events = new ArrayList<>(currentRecordingEvents);
            detail.signature = computeSignature(detail.events);
            executions.put(currentRecordingId, detail);
            log.info("TNR recording stopped: {} with {} events", currentRecordingId, detail.events.size());
            currentRecordingId = null;
            currentRecordingEvents.clear();
        }
    }

    private String computeSignature(List<TNREvent> events) {
        StringBuilder sb = new StringBuilder();
        for (TNREvent ev : events) {
            sb.append(ev.getType()).append(":").append(ev.getAction()).append(";");
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    // =========================================================================
    // CRUD Operations
    // =========================================================================

    /**
     * Récupère tous les scénarios.
     *
     * @return liste des scénarios
     */
    public List<TNRScenario> getAllScenarios() {
        return repository.findAllTNRScenarios();
    }

    /**
     * Récupère un scénario par ID.
     *
     * @param id identifiant du scénario
     * @return le scénario trouvé
     */
    public TNRScenario getScenario(String id) {
        return repository.findTNRScenarioById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));
    }

    /**
     * Crée un nouveau scénario.
     *
     * @param scenario données du scénario
     * @return le scénario créé
     */
    public TNRScenario createScenario(TNRScenario scenario) {
        if (scenario.getId() == null || scenario.getId().isBlank()) {
            scenario.setId(UUID.randomUUID().toString());
        }
        scenario.setCreatedAt(LocalDateTime.now());
        scenario.setUpdatedAt(LocalDateTime.now());
        scenario.setStatus(ScenarioStatus.PENDING);

        TNRScenario saved = repository.saveTNRScenario(scenario);
        log.info("Created TNR scenario: {} - {}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Met à jour un scénario.
     *
     * @param id identifiant du scénario
     * @param updates données de mise à jour
     * @return le scénario mis à jour
     */
    public TNRScenario updateScenario(String id, TNRScenario updates) {
        TNRScenario scenario = getScenario(id);

        if (updates.getName() != null) {
            scenario.setName(updates.getName());
        }
        if (updates.getDescription() != null) {
            scenario.setDescription(updates.getDescription());
        }
        if (updates.getCategory() != null) {
            scenario.setCategory(updates.getCategory());
        }
        if (updates.getTags() != null) {
            scenario.setTags(updates.getTags());
        }
        if (updates.getSteps() != null) {
            scenario.setSteps(updates.getSteps());
        }
        if (updates.getConfig() != null) {
            scenario.setConfig(updates.getConfig());
        }

        scenario.setUpdatedAt(LocalDateTime.now());

        return repository.saveTNRScenario(scenario);
    }

    /**
     * Supprime un scénario.
     *
     * @param id identifiant du scénario
     */
    public void deleteScenario(String id) {
        getScenario(id); // Vérifie l'existence
        repository.deleteTNRScenario(id);
        log.info("Deleted TNR scenario: {}", id);
    }

    // =========================================================================
    // Execution
    // =========================================================================

    /**
     * Exécute un scénario de test.
     *
     * @param scenarioId ID du scénario
     * @return CompletableFuture avec le résultat
     */
    @Async("taskExecutor")
    public CompletableFuture<TNRResult> runScenario(String scenarioId) {
        TNRScenario scenario = getScenario(scenarioId);

        if (runningTests.containsKey(scenarioId)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Scenario already running"));
        }

        log.info("Starting TNR scenario: {} - {}", scenarioId, scenario.getName());

        scenario.setStatus(ScenarioStatus.RUNNING);
        scenario.setLastRunAt(LocalDateTime.now());
        repository.saveTNRScenario(scenario);

        TNRResult result = TNRResult.builder()
                .scenarioId(scenarioId)
                .status(ScenarioStatus.RUNNING)
                .totalSteps(scenario.getSteps().size())
                .executedAt(LocalDateTime.now())
                .build();

        runningTests.put(scenarioId, result);

        try {
            // Créer ou récupérer la session de test
            Session testSession = getOrCreateTestSession(scenario.getConfig());

            long startTime = System.currentTimeMillis();
            List<StepResult> stepResults = new ArrayList<>();
            int passedSteps = 0;
            int failedSteps = 0;

            // Exécuter chaque étape
            for (int i = 0; i < scenario.getSteps().size(); i++) {
                TNRStep step = scenario.getSteps().get(i);
                result.getLogs().add("Executing step " + (i + 1) + ": " + step.getName());

                StepResult stepResult = executeStep(step, testSession, scenario.getConfig());
                stepResults.add(stepResult);
                step.setResult(stepResult);

                if (stepResult.isPassed()) {
                    passedSteps++;
                    result.getLogs().add("  ✓ Step passed");
                } else {
                    failedSteps++;
                    result.getLogs().add("  ✗ Step failed: " + stepResult.getMessage());

                    if (!scenario.getConfig().isContinueOnError()) {
                        result.getLogs().add("Stopping execution due to error");
                        break;
                    }
                }
            }

            // Finaliser le résultat
            result.setDurationMs(System.currentTimeMillis() - startTime);
            result.setPassedSteps(passedSteps);
            result.setFailedSteps(failedSteps);
            result.setStepResults(stepResults);

            if (failedSteps == 0) {
                result.setStatus(ScenarioStatus.PASSED);
                scenario.setStatus(ScenarioStatus.PASSED);
            } else {
                result.setStatus(ScenarioStatus.FAILED);
                scenario.setStatus(ScenarioStatus.FAILED);
            }

            scenario.setLastResult(result);
            repository.saveTNRScenario(scenario);

            log.info("TNR scenario {} completed: {} passed, {} failed, {}ms",
                    scenarioId, passedSteps, failedSteps, result.getDurationMs());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("TNR scenario {} failed with error: {}", scenarioId, e.getMessage(), e);

            result.setStatus(ScenarioStatus.ERROR);
            result.setErrorMessage(e.getMessage());
            scenario.setStatus(ScenarioStatus.ERROR);
            scenario.setLastResult(result);
            repository.saveTNRScenario(scenario);

            return CompletableFuture.completedFuture(result);

        } finally {
            runningTests.remove(scenarioId);
        }
    }

    /**
     * Exécute une étape du scénario.
     */
    private StepResult executeStep(TNRStep step, Session session, TNRConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            // Délai avant exécution
            if (step.getDelayMs() > 0) {
                Thread.sleep(step.getDelayMs());
            }

            Object response = null;

            switch (step.getType()) {
                case CONNECT -> {
                    boolean connected = ocppService.connect(session.getId())
                            .get(step.getTimeoutMs(), TimeUnit.MILLISECONDS);
                    response = Map.of("connected", connected);
                    if (!connected) {
                        return StepResult.builder()
                                .passed(false)
                                .message("Failed to connect")
                                .durationMs(System.currentTimeMillis() - startTime)
                                .build();
                    }
                }

                case DISCONNECT -> {
                    ocppService.disconnect(session.getId());
                    response = Map.of("disconnected", true);
                }

                case SEND_OCPP -> {
                    response = sendOcppAction(session.getId(), step.getAction(),
                            (Map<String, Object>) step.getPayload(), step.getTimeoutMs());
                }

                case ASSERT_STATE -> {
                    Session current = sessionService.getSession(session.getId());
                    String expectedState = (String) step.getPayload();
                    if (!current.getState().name().equalsIgnoreCase(expectedState)) {
                        return StepResult.builder()
                                .passed(false)
                                .message("State mismatch: expected " + expectedState +
                                        ", got " + current.getState())
                                .durationMs(System.currentTimeMillis() - startTime)
                                .response(Map.of("actualState", current.getState().name()))
                                .build();
                    }
                    response = Map.of("state", current.getState().name());
                }

                case DELAY -> {
                    long delayMs = step.getPayload() instanceof Number ?
                            ((Number) step.getPayload()).longValue() : 1000;
                    Thread.sleep(delayMs);
                    response = Map.of("delayed", delayMs);
                }

                case SET_VALUE -> {
                    Map<String, Object> values = (Map<String, Object>) step.getPayload();
                    session = sessionService.getSession(session.getId());
                    if (values.containsKey("soc")) {
                        session.setSoc(((Number) values.get("soc")).doubleValue());
                    }
                    if (values.containsKey("powerKw")) {
                        session.setCurrentPowerKw(((Number) values.get("powerKw")).doubleValue());
                    }
                    if (values.containsKey("idTag")) {
                        session.setIdTag((String) values.get("idTag"));
                    }
                    response = Map.of("updated", values);
                }

                default -> throw new IllegalArgumentException("Unknown step type: " + step.getType());
            }

            // Valider les assertions
            if (step.getAssertions() != null && !step.getAssertions().isEmpty()) {
                for (TNRAssertion assertion : step.getAssertions()) {
                    boolean passed = validateAssertion(assertion, response, session);
                    assertion.setPassed(passed);
                    if (!passed) {
                        return StepResult.builder()
                                .passed(false)
                                .message("Assertion failed: " + assertion.getMessage())
                                .durationMs(System.currentTimeMillis() - startTime)
                                .response(response)
                                .build();
                    }
                }
            }

            return StepResult.builder()
                    .passed(true)
                    .message("Success")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .response(response)
                    .build();

        } catch (Exception e) {
            return StepResult.builder()
                    .passed(false)
                    .message("Error: " + e.getMessage())
                    .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * Envoie une action OCPP.
     */
    private Map<String, Object> sendOcppAction(String sessionId, String action,
                                                Map<String, Object> payload, long timeoutMs)
            throws Exception {
        return switch (action.toLowerCase()) {
            case "bootnotification" -> ocppService.sendBootNotification(sessionId)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            case "authorize" -> ocppService.sendAuthorize(sessionId)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            case "starttransaction" -> ocppService.sendStartTransaction(sessionId)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            case "stoptransaction" -> ocppService.sendStopTransaction(sessionId)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            case "heartbeat" -> ocppService.sendHeartbeat(sessionId)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            case "metervalues" -> ocppService.sendMeterValues(sessionId)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            default -> throw new IllegalArgumentException("Unknown OCPP action: " + action);
        };
    }

    /**
     * Valide une assertion.
     */
    private boolean validateAssertion(TNRAssertion assertion, Object response, Session session) {
        Object actual = null;

        switch (assertion.getType()) {
            case RESPONSE_STATUS -> {
                if (response instanceof Map) {
                    actual = ((Map<?, ?>) response).get("status");
                    if (actual == null) {
                        Map<?, ?> idTagInfo = (Map<?, ?>) ((Map<?, ?>) response).get("idTagInfo");
                        if (idTagInfo != null) {
                            actual = idTagInfo.get("status");
                        }
                    }
                }
            }
            case RESPONSE_FIELD -> {
                if (response instanceof Map && assertion.getPath() != null) {
                    actual = getValueByPath((Map<?, ?>) response, assertion.getPath());
                }
            }
            case SESSION_STATE -> {
                session = sessionService.getSession(session.getId());
                actual = session.getState().name();
            }
            case SESSION_FIELD -> {
                session = sessionService.getSession(session.getId());
                actual = getSessionFieldValue(session, assertion.getPath());
            }
        }

        assertion.setActual(actual);

        return compareValues(actual, assertion.getExpected(), assertion.getOperator());
    }

    private boolean compareValues(Object actual, Object expected, Operator operator) {
        if (operator == Operator.IS_NULL) {
            return actual == null;
        }
        if (operator == Operator.IS_NOT_NULL) {
            return actual != null;
        }
        if (actual == null || expected == null) {
            return false;
        }

        String actualStr = actual.toString();
        String expectedStr = expected.toString();

        return switch (operator) {
            case EQUALS -> actualStr.equalsIgnoreCase(expectedStr);
            case NOT_EQUALS -> !actualStr.equalsIgnoreCase(expectedStr);
            case CONTAINS -> actualStr.toLowerCase().contains(expectedStr.toLowerCase());
            case NOT_CONTAINS -> !actualStr.toLowerCase().contains(expectedStr.toLowerCase());
            case GREATER_THAN -> Double.parseDouble(actualStr) > Double.parseDouble(expectedStr);
            case LESS_THAN -> Double.parseDouble(actualStr) < Double.parseDouble(expectedStr);
            case REGEX -> actualStr.matches(expectedStr);
            default -> false;
        };
    }

    private Object getValueByPath(Map<?, ?> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private Object getSessionFieldValue(Session session, String field) {
        return switch (field.toLowerCase()) {
            case "soc" -> session.getSoc();
            case "state" -> session.getState().name();
            case "connected" -> session.isConnected();
            case "charging" -> session.isCharging();
            case "authorized" -> session.isAuthorized();
            case "powerKw", "currentpowerkw" -> session.getCurrentPowerKw();
            case "energykwh", "energydeliveredkwh" -> session.getEnergyDeliveredKwh();
            default -> null;
        };
    }

    /**
     * Récupère ou crée une session de test.
     */
    private Session getOrCreateTestSession(TNRConfig config) {
        String sessionId = "tnr-test-session";

        return sessionService.findSession(sessionId)
                .orElseGet(() -> sessionService.createSession(Session.builder()
                        .id(sessionId)
                        .title("TNR Test Session")
                        .url(config.getCsmsUrl() != null ? config.getCsmsUrl() :
                                "ws://localhost:8080/ocpp")
                        .cpId(config.getCpId() != null ? config.getCpId() : "TNR_TEST_001")
                        .idTag("TNR_TAG_001")
                        .soc(20.0)
                        .targetSoc(80.0)
                        .build()));
    }

    // =========================================================================
    // Results
    // =========================================================================

    /**
     * Récupère tous les résultats de tests.
     *
     * @return liste des résultats
     */
    public List<TNRResult> getAllResults() {
        return repository.findAllTNRScenarios().stream()
                .filter(s -> s.getLastResult() != null)
                .map(TNRScenario::getLastResult)
                .toList();
    }

    /**
     * Récupère le résultat d'un scénario.
     *
     * @param scenarioId ID du scénario
     * @return le résultat ou null
     */
    public TNRResult getResult(String scenarioId) {
        TNRScenario scenario = getScenario(scenarioId);
        return scenario.getLastResult();
    }

    /**
     * Vérifie si un scénario est en cours d'exécution.
     *
     * @param scenarioId ID du scénario
     * @return true si en cours
     */
    public boolean isRunning(String scenarioId) {
        return runningTests.containsKey(scenarioId);
    }

    /**
     * Exporte les résultats au format Jira Xray.
     *
     * @return export JSON
     */
    public Map<String, Object> exportToXray() {
        List<Map<String, Object>> tests = new ArrayList<>();

        for (TNRScenario scenario : repository.findAllTNRScenarios()) {
            if (scenario.getLastResult() != null) {
                Map<String, Object> test = new LinkedHashMap<>();
                test.put("testKey", "TNR-" + scenario.getId().substring(0, 8).toUpperCase());
                test.put("status", scenario.getStatus() == ScenarioStatus.PASSED ? "PASS" : "FAIL");
                test.put("comment", scenario.getName());

                if (scenario.getLastResult() != null) {
                    test.put("executedOn", scenario.getLastResult().getExecutedAt().toString());
                    test.put("duration", scenario.getLastResult().getDurationMs());
                }

                tests.add(test);
            }
        }

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("testExecutionKey", "TNR-EXEC-" + System.currentTimeMillis());
        export.put("tests", tests);
        export.put("info", Map.of(
                "summary", "TNR Execution " + LocalDateTime.now(),
                "description", "Automated TNR tests from EVSE Simulator"
        ));

        return export;
    }
}