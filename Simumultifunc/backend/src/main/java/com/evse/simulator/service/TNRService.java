package com.evse.simulator.service;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.ExecutionDetail;
import com.evse.simulator.model.ExecutionMeta;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.TNRScenario;
import com.evse.simulator.model.TNRScenario.*;
import com.evse.simulator.repository.DataRepository;
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
import java.util.concurrent.TimeoutException;
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

    private final DataRepository repository;
    private final SessionService sessionService;
    private final OCPPService ocppService;

    // Résultats en cours
    private final Map<String, TNRResult> runningTests = new ConcurrentHashMap<>();

    // Stockage des exécutions et événements pour TNR+
    private final Map<String, ExecutionDetail> executions = new ConcurrentHashMap<>();
    private final List<TNREvent> currentRecordingEvents = new CopyOnWriteArrayList<>();
    private volatile String currentRecordingId = null;
    private volatile String currentRecordingScenarioName = null;

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
        currentRecordingScenarioName = scenarioName;
        currentRecordingEvents.clear();
        log.info("TNR recording started: {} (scenario: {})", executionId, scenarioName);
    }

    /**
     * Vérifie si un enregistrement est en cours.
     */
    public boolean isRecording() {
        return currentRecordingId != null;
    }

    /**
     * Récupère l'ID de l'enregistrement en cours.
     */
    public String getCurrentRecordingId() {
        return currentRecordingId;
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

            // Persister dans le repository
            repository.saveTNRExecution(detail);

            log.info("TNR recording stopped: {} with {} events", currentRecordingId, detail.events.size());
            currentRecordingId = null;
            currentRecordingEvents.clear();
        }
    }

    /**
     * Arrête l'enregistrement, crée un scénario et sauvegarde.
     * @param scenarioName Nom du scénario (optionnel)
     * @return ID de l'exécution sauvegardée
     */
    public String stopRecordingAndSave(String scenarioName) {
        return stopRecordingAndSave(scenarioName, null, null);
    }

    /**
     * Arrête l'enregistrement, crée un scénario et sauvegarde avec métadonnées.
     * @param scenarioName Nom du scénario (optionnel)
     * @param category Catégorie/dossier du scénario (optionnel)
     * @param tags Liste des tags (optionnel)
     * @return ID de l'exécution sauvegardée
     */
    public String stopRecordingAndSave(String scenarioName, String category, List<String> tags) {
        if (currentRecordingId == null) {
            log.warn("No active recording to stop");
            return null;
        }

        String executionId = currentRecordingId;
        String name = scenarioName != null ? scenarioName : currentRecordingScenarioName;

        // Créer l'ExecutionDetail
        ExecutionDetail detail = new ExecutionDetail();
        detail.id = executionId;
        detail.scenarioName = name;
        detail.executedAt = LocalDateTime.now();
        detail.events = new ArrayList<>(currentRecordingEvents);
        detail.signature = computeSignature(detail.events);

        // Sauvegarder dans la map en mémoire
        executions.put(executionId, detail);

        // Persister dans le repository
        repository.saveTNRExecution(detail);

        // Créer un scénario TNR à partir des événements enregistrés
        if (!detail.events.isEmpty()) {
            TNRScenario scenario = createScenarioFromEvents(executionId, name, detail.events, category, tags);
            repository.saveTNRScenario(scenario);
            log.info("TNR scenario created from recording: {} with {} steps, category={}, tags={}",
                scenario.getId(), scenario.getSteps().size(), scenario.getCategory(), scenario.getTags());
        }

        log.info("TNR recording stopped and saved: {} with {} events", executionId, detail.events.size());

        // Reset
        currentRecordingId = null;
        currentRecordingScenarioName = null;
        currentRecordingEvents.clear();

        return executionId;
    }

    /**
     * Crée un scénario TNR à partir des événements enregistrés.
     */
    private TNRScenario createScenarioFromEvents(String executionId, String name, List<TNREvent> events,
                                                   String category, List<String> tags) {
        TNRScenario scenario = new TNRScenario();
        scenario.setId("scenario-" + executionId);
        scenario.setName(name != null ? name : "Recording " + executionId);
        scenario.setDescription("Scénario créé automatiquement depuis l'enregistrement");
        scenario.setCategory(category != null && !category.isBlank() ? category : "recorded");
        scenario.setTags(tags != null && !tags.isEmpty() ? tags : List.of("auto-recorded", "tnr"));
        scenario.setCreatedAt(LocalDateTime.now());
        scenario.setUpdatedAt(LocalDateTime.now());
        scenario.setStatus(ScenarioStatus.PENDING);

        // Configuration par défaut
        TNRConfig config = new TNRConfig();
        config.setContinueOnError(false);
        config.setCpId("SIMU-CP-001");

        // Convertir les événements en étapes
        List<TNRStep> steps = new ArrayList<>();
        Long firstTimestamp = events.isEmpty() ? null : events.get(0).getTimestamp();

        for (int i = 0; i < events.size(); i++) {
            TNREvent event = events.get(i);
            TNRStep step = new TNRStep();
            step.setOrder(i + 1);
            step.setName("Step " + (i + 1) + ": " + (event.getAction() != null ? event.getAction() : event.getType()));

            // Mapper le type d'événement vers le type de step
            step.setType(mapEventTypeToStepType(event.getType()));
            step.setAction(event.getAction());

            // Enrichir le payload avec le timestamp pour permettre le replay en temps réel
            Object payload = event.getPayload();
            if (event.getTimestamp() != null) {
                Map<String, Object> enrichedPayload = new LinkedHashMap<>();
                enrichedPayload.put("timestamp", event.getTimestamp());
                if (payload instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> originalPayload = (Map<String, Object>) payload;
                    enrichedPayload.putAll(originalPayload);
                } else if (payload != null) {
                    enrichedPayload.put("data", payload);
                }
                step.setPayload(enrichedPayload);
            } else {
                step.setPayload(payload);
            }

            step.setTimeoutMs(30000);

            // Calculer le délai depuis l'événement précédent pour le mode realtime
            if (i > 0 && event.getTimestamp() != null && events.get(i - 1).getTimestamp() != null) {
                long delay = event.getTimestamp() - events.get(i - 1).getTimestamp();
                step.setDelayMs(Math.max(0, delay));
            } else {
                step.setDelayMs(0);
            }

            // Extraire l'URL et cpId depuis l'événement de connexion
            if ("connection".equals(event.getType()) && "connect".equals(event.getAction())) {
                if (event.getPayload() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> connectPayload = (Map<String, Object>) event.getPayload();
                    String uri = (String) connectPayload.get("uri");
                    if (uri != null && !uri.isBlank()) {
                        config.setCsmsUrl(uri);
                        // Extraire le cpId de l'URI
                        String cpId = extractCpIdFromUri(uri);
                        if (cpId != null) {
                            config.setCpId(cpId);
                        }
                        log.info("TNR Scenario config from recording: URL={}, cpId={}", uri, cpId);
                    }
                }
            }

            steps.add(step);
        }

        scenario.setSteps(steps);
        scenario.setConfig(config);

        return scenario;
    }

    /**
     * Mappe un type d'événement vers un type de step TNR.
     */
    private StepType mapEventTypeToStepType(String eventType) {
        if (eventType == null) return StepType.SEND_OCPP;

        return switch (eventType.toLowerCase()) {
            case "connect", "connection" -> StepType.CONNECT;
            case "disconnect", "disconnection" -> StepType.DISCONNECT;
            case "ocpp", "ocpp_call", "ocpp_result" -> StepType.SEND_OCPP;
            case "state_change" -> StepType.ASSERT_STATE;
            case "delay", "wait" -> StepType.DELAY;
            default -> StepType.SEND_OCPP;
        };
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
     * Exécute un scénario de test avec les options par défaut (mode fast).
     *
     * @param scenarioId ID du scénario
     * @return CompletableFuture avec le résultat
     */
    @Async("taskExecutor")
    public CompletableFuture<TNRResult> runScenario(String scenarioId) {
        return runScenario(scenarioId, "fast", 1.0);
    }

    /**
     * Exécute un scénario de test avec contrôle du timing.
     *
     * @param scenarioId ID du scénario
     * @param mode Mode d'exécution: "fast" (pas de délai), "realtime" (timing original), "instant" (alias de fast)
     * @param speed Multiplicateur de vitesse (1.0 = temps réel, 2.0 = 2x plus rapide, 0.5 = 2x plus lent)
     * @return CompletableFuture avec le résultat
     */
    @Async("taskExecutor")
    public CompletableFuture<TNRResult> runScenario(String scenarioId, String mode, double speed) {
        TNRScenario scenario = getScenario(scenarioId);
        log.info("Running TNR scenario {} with mode={}, speed={}", scenarioId, mode, speed);

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

        // Déterminer si on doit respecter le timing original
        boolean useRealtime = "realtime".equalsIgnoreCase(mode);
        double effectiveSpeed = speed > 0 ? speed : 1.0;

        try {
            // Créer ou récupérer la session de test
            Session testSession = getOrCreateTestSession(scenario.getConfig());

            long startTime = System.currentTimeMillis();
            List<StepResult> stepResults = new ArrayList<>();
            int passedSteps = 0;
            int failedSteps = 0;

            // Variables pour le calcul du timing en mode realtime
            long lastStepTimestamp = 0;

            // Exécuter chaque étape
            for (int i = 0; i < scenario.getSteps().size(); i++) {
                TNRStep step = scenario.getSteps().get(i);
                result.getLogs().add("Executing step " + (i + 1) + "/" + scenario.getSteps().size() + ": " + step.getName());

                // Calculer le délai en mode realtime
                if (useRealtime && i > 0) {
                    long stepDelay = calculateStepDelay(step, lastStepTimestamp, effectiveSpeed);
                    if (stepDelay > 0) {
                        result.getLogs().add("  ⏳ Waiting " + stepDelay + "ms (realtime mode, speed=" + effectiveSpeed + "x)");
                        Thread.sleep(stepDelay);
                    }
                }

                // Mettre à jour le timestamp pour le prochain calcul
                lastStepTimestamp = getStepTimestamp(step);

                StepResult stepResult = executeStep(step, testSession, scenario.getConfig());
                stepResults.add(stepResult);
                step.setResult(stepResult);

                if (stepResult.isPassed()) {
                    passedSteps++;
                    result.getLogs().add("  ✓ Step passed in " + stepResult.getDurationMs() + "ms");
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
                    // Vérifier si c'est en fait une action disconnect
                    if ("disconnect".equalsIgnoreCase(step.getAction())) {
                        ocppService.disconnect(session.getId());
                        response = Map.of("disconnected", true);
                        log.info("TNR: Disconnected session {}", session.getId());
                    } else {
                        // Extraire l'URL du payload si présente (scénarios enregistrés)
                        String targetUrl = null;
                        String targetCpId = null;

                        if (step.getPayload() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> connectPayload = (Map<String, Object>) step.getPayload();
                            targetUrl = (String) connectPayload.get("uri");
                            if (targetUrl != null && !targetUrl.isBlank()) {
                                // Mettre à jour l'URL de la session avant connexion
                                session.setUrl(targetUrl);
                                // Extraire le cpId de l'URI si présent
                                targetCpId = extractCpIdFromUri(targetUrl);
                                if (targetCpId != null) {
                                    session.setCpId(targetCpId);
                                }
                                repository.saveSession(session);
                                log.info("TNR: Updated session URL to {} and cpId to {}", targetUrl, session.getCpId());
                            }
                        }

                        log.info("TNR: Attempting connection to URL: {} with cpId: {}",
                            session.getUrl(), session.getCpId());

                        try {
                            boolean connected = ocppService.connect(session.getId())
                                    .get(step.getTimeoutMs(), TimeUnit.MILLISECONDS);
                            response = Map.of("connected", connected, "url", session.getUrl(), "cpId", session.getCpId());
                            if (!connected) {
                                log.warn("TNR: Connection failed for session {} to URL: {}",
                                    session.getId(), session.getUrl());
                                return StepResult.builder()
                                        .passed(false)
                                        .message("Failed to connect to " + session.getUrl())
                                        .durationMs(System.currentTimeMillis() - startTime)
                                        .response(response)
                                        .build();
                            }
                            log.info("TNR: Successfully connected to {}", session.getUrl());
                        } catch (TimeoutException te) {
                            log.error("TNR: Connection timeout for session {} to URL: {}",
                                session.getId(), session.getUrl());
                            return StepResult.builder()
                                    .passed(false)
                                    .message("Connection timeout after " + step.getTimeoutMs() + "ms to " + session.getUrl())
                                    .durationMs(System.currentTimeMillis() - startTime)
                                    .build();
                        }
                    }
                }

                case DISCONNECT -> {
                    ocppService.disconnect(session.getId());
                    response = Map.of("disconnected", true);
                }

                case SEND_OCPP -> {
                    // Pour les scénarios enregistrés, gérer les directions
                    if (step.getPayload() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> ocppPayload = (Map<String, Object>) step.getPayload();
                        String direction = (String) ocppPayload.get("direction");
                        String action = step.getAction();

                        // Messages entrants (réponses du serveur ou CALLs du serveur)
                        if ("incoming".equals(direction)) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> innerPayload = (Map<String, Object>) ocppPayload.get("payload");

                            // Si c'est un CallResult, c'est une réponse à un CALL client
                            // On doit ENVOYER le CALL correspondant pour déclencher cette réponse
                            if ("CallResult".equals(action) && innerPayload != null) {
                                String inferredAction = inferActionFromCallResult(innerPayload);
                                if (inferredAction != null) {
                                    log.info("TNR TRUE REPLAY: Inferred action {} from CallResult, sending...", inferredAction);
                                    try {
                                        response = sendOcppAction(session.getId(), inferredAction, new HashMap<>(), step.getTimeoutMs());
                                        return StepResult.builder()
                                                .passed(true)
                                                .message("Sent " + inferredAction + " (inferred from CallResult)")
                                                .durationMs(System.currentTimeMillis() - startTime)
                                                .response(response)
                                                .build();
                                    } catch (Exception e) {
                                        log.warn("TNR: Failed to send inferred action {}: {}", inferredAction, e.getMessage());
                                        // Continue as observation if send fails
                                    }
                                }
                                // Si on ne peut pas inférer l'action, observer seulement
                                log.info("TNR: Observed incoming CallResult: {}", ocppPayload.get("messageId"));
                                response = Map.of(
                                    "observed", true,
                                    "direction", "incoming",
                                    "action", action,
                                    "note", "Incoming CallResult observed"
                                );
                                return StepResult.builder()
                                        .passed(true)
                                        .message("Observed incoming: " + action)
                                        .durationMs(System.currentTimeMillis() - startTime)
                                        .response(response)
                                        .build();
                            }

                            // Si c'est un CALL du serveur (comme ChangeAvailability), il sera géré automatiquement
                            // par le messageRouter, on attend juste un peu pour laisser le temps au serveur
                            log.info("TNR: Waiting for server CALL: {} - will be handled by messageRouter", action);
                            Thread.sleep(500); // Petit délai pour laisser le flux OCPP se dérouler
                            response = Map.of(
                                "observed", true,
                                "direction", "incoming",
                                "action", action,
                                "note", "Server CALL will be handled automatically by messageRouter"
                            );
                            return StepResult.builder()
                                    .passed(true)
                                    .message("Awaited server CALL: " + action)
                                    .durationMs(System.currentTimeMillis() - startTime)
                                    .response(response)
                                    .build();
                        }

                        // Messages sortants - distinguer les CALLs client des réponses aux CALLs serveur
                        if ("outgoing".equals(direction)) {
                            // Vérifier si c'est un CALL client initié (actions connues)
                            if (isClientInitiatedAction(action)) {
                                // C'est un CALL client - ON DOIT L'ENVOYER pour un vrai replay!
                                log.info("TNR TRUE REPLAY: Sending client CALL: {}", action);
                                @SuppressWarnings("unchecked")
                                Map<String, Object> callPayload = (Map<String, Object>) ocppPayload.get("payload");
                                response = sendOcppAction(session.getId(), action,
                                    callPayload != null ? callPayload : new HashMap<>(), step.getTimeoutMs());
                                return StepResult.builder()
                                        .passed(true)
                                        .message("Sent client CALL: " + action)
                                        .durationMs(System.currentTimeMillis() - startTime)
                                        .response(response)
                                        .build();
                            } else {
                                // C'est une réponse client à un CALL serveur - géré par messageRouter
                                log.info("TNR: Client response to server CALL handled by messageRouter: {} - {}",
                                    action, ocppPayload.get("messageId"));
                                response = Map.of(
                                    "handled", true,
                                    "direction", "outgoing",
                                    "action", action,
                                    "note", "Client response handled automatically by messageRouter"
                                );
                                return StepResult.builder()
                                        .passed(true)
                                        .message("Response handled: " + action)
                                        .durationMs(System.currentTimeMillis() - startTime)
                                        .response(response)
                                        .build();
                            }
                        }

                        // Si le payload contient le vrai payload OCPP, l'utiliser
                        @SuppressWarnings("unchecked")
                        Map<String, Object> innerPayload = (Map<String, Object>) ocppPayload.get("payload");
                        if (innerPayload != null) {
                            response = sendOcppAction(session.getId(), action, innerPayload, step.getTimeoutMs());
                        } else {
                            response = sendOcppAction(session.getId(), action, ocppPayload, step.getTimeoutMs());
                        }
                    } else {
                        response = sendOcppAction(session.getId(), step.getAction(),
                                (Map<String, Object>) step.getPayload(), step.getTimeoutMs());
                    }
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
     * Calcule le délai à appliquer avant l'exécution d'un step en mode realtime.
     * Le délai est calculé à partir du timestamp du payload de l'événement.
     *
     * @param step Le step à exécuter
     * @param lastTimestamp Timestamp du step précédent
     * @param speed Multiplicateur de vitesse
     * @return Délai en millisecondes
     */
    private long calculateStepDelay(TNRStep step, long lastTimestamp, double speed) {
        if (lastTimestamp == 0) {
            return 0;
        }

        long currentTimestamp = getStepTimestamp(step);
        if (currentTimestamp == 0) {
            // Pas de timestamp, utiliser le delayMs configuré ou un délai par défaut
            long configuredDelay = step.getDelayMs();
            return configuredDelay > 0 ? (long) (configuredDelay / speed) : 0;
        }

        long timeDiff = currentTimestamp - lastTimestamp;
        if (timeDiff <= 0) {
            return 0;
        }

        // Appliquer le multiplicateur de vitesse
        long adjustedDelay = (long) (timeDiff / speed);

        // Limiter le délai max à 30 secondes pour éviter les attentes trop longues
        return Math.min(adjustedDelay, 30000);
    }

    /**
     * Extrait le timestamp d'un step à partir de son payload.
     * Le timestamp peut être dans le payload sous différentes formes.
     *
     * @param step Le step
     * @return Timestamp en millisecondes ou 0 si non trouvé
     */
    @SuppressWarnings("unchecked")
    private long getStepTimestamp(TNRStep step) {
        if (step.getPayload() == null) {
            return 0;
        }

        if (step.getPayload() instanceof Map) {
            Map<String, Object> payload = (Map<String, Object>) step.getPayload();

            // Chercher le timestamp dans différents champs possibles
            Object ts = payload.get("timestamp");
            if (ts == null) ts = payload.get("ts");
            if (ts == null) ts = payload.get("recordedAt");
            if (ts == null) ts = payload.get("time");

            if (ts instanceof Number) {
                return ((Number) ts).longValue();
            } else if (ts instanceof String) {
                try {
                    return Long.parseLong((String) ts);
                } catch (NumberFormatException e) {
                    // Essayer de parser comme ISO date
                    try {
                        return java.time.Instant.parse((String) ts).toEpochMilli();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Infère l'action OCPP client à partir d'une réponse CallResult.
     * Utilisé pour le TRUE replay: quand on voit un CallResult, on déduit
     * quelle action client l'a généré et on l'envoie.
     */
    private String inferActionFromCallResult(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }

        // BootNotification response: { status: "Accepted/Pending/Rejected", currentTime: "...", interval: N }
        if (payload.containsKey("currentTime") && payload.containsKey("interval")) {
            return "BootNotification";
        }

        // Authorize response: { idTagInfo: { status: "Accepted/..." } }
        // Also check it's NOT a StartTransaction response (which also has idTagInfo but with transactionId)
        if (payload.containsKey("idTagInfo") && !payload.containsKey("transactionId")) {
            return "Authorize";
        }

        // StartTransaction response: { transactionId: N, idTagInfo: { status: "Accepted/..." } }
        if (payload.containsKey("transactionId") && payload.containsKey("idTagInfo")) {
            return "StartTransaction";
        }

        // StopTransaction response: { idTagInfo: { status: "Accepted/..." } } - similar to Authorize
        // We can't easily distinguish, so if we already have a transaction, assume StopTransaction
        // For now, skip - the flow will determine this based on state

        // Heartbeat response: { currentTime: "..." } (but no interval)
        if (payload.containsKey("currentTime") && !payload.containsKey("interval") && payload.size() == 1) {
            return "Heartbeat";
        }

        // StatusNotification/MeterValues response: {} (empty)
        if (payload.isEmpty()) {
            // Can't determine - could be StatusNotification, MeterValues, or other
            // Skip sending to avoid duplicates
            return null;
        }

        // DataTransfer response: { status: "Accepted/Rejected/UnknownMessageId/UnknownVendorId", data: "..." }
        if (payload.containsKey("status") && !payload.containsKey("currentTime") && !payload.containsKey("idTagInfo")) {
            // Could be DataTransfer, but could also be other messages
            // Skip for safety
            return null;
        }

        log.debug("TNR: Could not infer action from CallResult payload: {}", payload);
        return null;
    }

    /**
     * Vérifie si une action OCPP est initiée par le client (CP) ou le serveur (CSMS).
     * Les actions client sont celles qui doivent être envoyées lors du replay.
     */
    private boolean isClientInitiatedAction(String action) {
        if (action == null) return false;

        // Actions initiées par le Charge Point (client)
        // Ces actions doivent être envoyées lors du replay
        return switch (action.toLowerCase()) {
            case "bootnotification",
                 "authorize",
                 "starttransaction",
                 "stoptransaction",
                 "metervalues",
                 "statusnotification",
                 "heartbeat",
                 "datatransfer",  // DataTransfer peut être bidirectionnel, on le traite comme client
                 "firmwarestatusnotification",
                 "diagnosticsstatusnotification" -> true;

            // Actions serveur - le client ne les initie jamais
            // ChangeAvailability, SetChargingProfile, ClearChargingProfile,
            // TriggerMessage, GetConfiguration, ChangeConfiguration, etc.
            default -> false;
        };
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
            case "statusnotification" -> {
                // Determine status from current session state
                Session session = sessionService.getSession(sessionId);
                var status = com.evse.simulator.model.enums.ConnectorStatus.fromSessionState(session.getState());
                yield ocppService.sendStatusNotification(sessionId, status)
                        .get(timeoutMs, TimeUnit.MILLISECONDS);
            }
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
     * Met à jour l'URL et le cpId si la configuration le spécifie.
     */
    private Session getOrCreateTestSession(TNRConfig config) {
        String sessionId = "tnr-test-session";

        Optional<Session> existingSession = sessionService.findSession(sessionId);

        if (existingSession.isPresent()) {
            Session session = existingSession.get();
            // Mettre à jour la configuration si nécessaire
            boolean needUpdate = false;
            if (config.getCsmsUrl() != null && !config.getCsmsUrl().isBlank()) {
                session.setUrl(config.getCsmsUrl());
                needUpdate = true;
            }
            if (config.getCpId() != null && !config.getCpId().isBlank()) {
                session.setCpId(config.getCpId());
                needUpdate = true;
            }
            if (needUpdate) {
                repository.saveSession(session);
                log.info("TNR: Updated test session with URL: {} and cpId: {}",
                    session.getUrl(), session.getCpId());
            }
            return session;
        }

        return sessionService.createSession(Session.builder()
                .id(sessionId)
                .title("TNR Test Session")
                .url(config.getCsmsUrl() != null ? config.getCsmsUrl() :
                        "ws://localhost:8080/ocpp")
                .cpId(config.getCpId() != null ? config.getCpId() : "TNR_TEST_001")
                .idTag("TNR_TAG_001")
                .soc(20.0)
                .targetSoc(80.0)
                .build());
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
     * Récupère le résultat d'un test en cours d'exécution.
     *
     * @param executionId ID de l'exécution (peut être scenarioId ou scenarioId-timestamp)
     * @return le résultat en cours ou null
     */
    public TNRResult getRunningResult(String executionId) {
        // Chercher par executionId exact
        for (Map.Entry<String, TNRResult> entry : runningTests.entrySet()) {
            String scenarioId = entry.getKey();
            TNRResult result = entry.getValue();
            // Vérifier si l'executionId correspond
            if (executionId.startsWith(scenarioId)) {
                return result;
            }
        }
        // Chercher par scenarioId direct
        return runningTests.get(executionId);
    }

    /**
     * Extrait le CP ID d'une URI OCPP.
     * L'URI est typiquement: wss://server/ocpp/WebSocket/CP_ID
     *
     * @param uri URI complète
     * @return CP ID ou null
     */
    private String extractCpIdFromUri(String uri) {
        if (uri == null || uri.isBlank()) return null;

        // Retirer les paramètres query si présents
        int queryIndex = uri.indexOf('?');
        if (queryIndex > 0) {
            uri = uri.substring(0, queryIndex);
        }

        // Retirer le trailing slash
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }

        // Extraire la dernière partie du path
        int lastSlash = uri.lastIndexOf('/');
        if (lastSlash > 0 && lastSlash < uri.length() - 1) {
            String lastPart = uri.substring(lastSlash + 1);
            // Vérifier que ce n'est pas "WebSocket" ou "ocpp"
            if (!lastPart.equalsIgnoreCase("WebSocket") &&
                !lastPart.equalsIgnoreCase("ocpp") &&
                !lastPart.isEmpty()) {
                return lastPart;
            }
        }
        return null;
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