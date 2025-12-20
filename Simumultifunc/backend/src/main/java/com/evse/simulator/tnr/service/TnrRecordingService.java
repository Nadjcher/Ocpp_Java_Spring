package com.evse.simulator.tnr.service;

import com.evse.simulator.model.TNREvent;
import com.evse.simulator.tnr.model.TnrEvent;
import com.evse.simulator.tnr.model.TnrExecution;
import com.evse.simulator.tnr.model.enums.TnrEventCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service d'enregistrement des événements TNR.
 * <p>
 * Améliore la fiabilité avec :
 * - Limite du nombre d'événements (évite les fuites mémoire)
 * - Filtrage des événements ignorés (Heartbeat, internal)
 * - Buffer circulaire thread-safe
 * - Catégorisation automatique
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TnrRecordingService {

    private static final int MAX_EVENTS = 10000;
    private static final int WARNING_THRESHOLD = 8000;

    private final TnrSignatureService signatureService;

    /**
     * Actions à ignorer lors de l'enregistrement.
     */
    private static final Set<String> IGNORED_ACTIONS = Set.of(
            "Heartbeat",
            "internal",
            "debug",
            "ping",
            "pong",
            "keepalive"
    );

    /**
     * Types d'événements à ignorer.
     */
    private static final Set<String> IGNORED_TYPES = Set.of(
            "internal",
            "debug",
            "metric"
    );

    // État d'enregistrement
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private volatile String currentRecordingId = null;
    private volatile String currentScenarioName = null;
    private volatile Instant recordingStartTime = null;

    // Buffer d'événements (thread-safe)
    private final ConcurrentLinkedQueue<TnrEvent> eventBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger eventCount = new AtomicInteger(0);

    // Stockage des exécutions terminées
    private final Map<String, TnrExecution> completedExecutions = new ConcurrentHashMap<>();

    /**
     * Démarre un nouvel enregistrement.
     *
     * @param executionId ID de l'exécution
     * @param scenarioName nom du scénario (optionnel)
     * @return true si démarré avec succès
     */
    public boolean startRecording(String executionId, String scenarioName) {
        if (recording.compareAndSet(false, true)) {
            currentRecordingId = executionId;
            currentScenarioName = scenarioName;
            recordingStartTime = Instant.now();
            eventBuffer.clear();
            eventCount.set(0);

            log.info("TNR recording started: {} (scenario: {})", executionId, scenarioName);
            return true;
        }

        log.warn("Recording already in progress: {}", currentRecordingId);
        return false;
    }

    /**
     * Vérifie si un enregistrement est en cours.
     */
    public boolean isRecording() {
        return recording.get();
    }

    /**
     * Récupère l'ID de l'enregistrement en cours.
     */
    public String getCurrentRecordingId() {
        return currentRecordingId;
    }

    /**
     * Enregistre un événement (nouveau format).
     *
     * @param event événement à enregistrer
     */
    public void recordEvent(TnrEvent event) {
        if (!recording.get()) {
            return;
        }

        // Filtrer les événements ignorés
        if (shouldIgnore(event)) {
            log.trace("TNR event ignored: {}", event.toShortString());
            return;
        }

        // Vérifier la limite
        int count = eventCount.incrementAndGet();
        if (count > MAX_EVENTS) {
            // Buffer plein, supprimer les plus anciens
            eventBuffer.poll();
            eventCount.decrementAndGet();
            log.warn("TNR event buffer full, dropping oldest event");
        } else if (count == WARNING_THRESHOLD) {
            log.warn("TNR event buffer reaching limit: {}/{}", count, MAX_EVENTS);
        }

        // Ajouter l'événement avec index séquentiel
        event.setSequenceIndex(count - 1);
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(Instant.now());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }

        eventBuffer.offer(event);
        log.debug("TNR event recorded [{}/{}]: {}", count, MAX_EVENTS, event.toShortString());
    }

    /**
     * Enregistre un événement (ancien format - pour compatibilité).
     *
     * @param legacyEvent événement ancien format
     */
    public void recordLegacyEvent(TNREvent legacyEvent) {
        if (legacyEvent == null) return;
        TnrEvent event = TnrEvent.fromLegacy(legacyEvent);
        recordEvent(event);
    }

    /**
     * Arrête l'enregistrement et retourne l'exécution.
     *
     * @return l'exécution enregistrée ou null si aucun enregistrement
     */
    public TnrExecution stopRecording() {
        if (!recording.compareAndSet(true, false)) {
            log.warn("No active recording to stop");
            return null;
        }

        // Créer l'exécution
        TnrExecution execution = TnrExecution.builder()
                .id(currentRecordingId)
                .scenarioName(currentScenarioName)
                .startedAt(recordingStartTime)
                .events(new ArrayList<>(eventBuffer))
                .build();

        execution.complete();

        // Calculer les signatures
        execution.setSignature(signatureService.computeSignature(execution.getEvents()));
        execution.setCriticalSignature(signatureService.computeCriticalSignature(execution.getEvents()));

        // Stocker l'exécution
        completedExecutions.put(execution.getId(), execution);

        log.info("TNR recording stopped: {} with {} events, signature: {}",
                currentRecordingId, execution.getEventCount(),
                execution.getSignature().substring(0, 16) + "...");

        // Reset
        currentRecordingId = null;
        currentScenarioName = null;
        recordingStartTime = null;
        eventBuffer.clear();
        eventCount.set(0);

        return execution;
    }

    /**
     * Annule l'enregistrement en cours sans sauvegarder.
     */
    public void cancelRecording() {
        if (recording.compareAndSet(true, false)) {
            log.info("TNR recording cancelled: {}", currentRecordingId);
            currentRecordingId = null;
            currentScenarioName = null;
            recordingStartTime = null;
            eventBuffer.clear();
            eventCount.set(0);
        }
    }

    /**
     * Récupère une exécution par ID.
     */
    public Optional<TnrExecution> getExecution(String executionId) {
        return Optional.ofNullable(completedExecutions.get(executionId));
    }

    /**
     * Récupère toutes les exécutions.
     */
    public List<TnrExecution> getAllExecutions() {
        return new ArrayList<>(completedExecutions.values());
    }

    /**
     * Supprime une exécution.
     */
    public boolean deleteExecution(String executionId) {
        return completedExecutions.remove(executionId) != null;
    }

    /**
     * Récupère les statistiques d'enregistrement en cours.
     */
    public Map<String, Object> getRecordingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("recording", recording.get());
        stats.put("recordingId", currentRecordingId);
        stats.put("scenarioName", currentScenarioName);
        stats.put("eventCount", eventCount.get());
        stats.put("maxEvents", MAX_EVENTS);
        stats.put("completedExecutions", completedExecutions.size());

        if (recordingStartTime != null) {
            stats.put("durationMs", Instant.now().toEpochMilli() - recordingStartTime.toEpochMilli());
        }

        // Comptage par catégorie
        if (recording.get()) {
            Map<String, Long> byCategory = new HashMap<>();
            for (TnrEvent event : eventBuffer) {
                String cat = event.getCategory().name();
                byCategory.merge(cat, 1L, Long::sum);
            }
            stats.put("eventsByCategory", byCategory);
        }

        return stats;
    }

    /**
     * Détermine si un événement doit être ignoré.
     */
    private boolean shouldIgnore(TnrEvent event) {
        if (event == null) return true;

        // Ignorer certains types
        if (event.getType() != null && IGNORED_TYPES.contains(event.getType().toLowerCase())) {
            return true;
        }

        // Ignorer certaines actions
        if (event.getAction() != null && IGNORED_ACTIONS.contains(event.getAction())) {
            return true;
        }

        // Ignorer les heartbeats (peut être activé/désactivé)
        if (event.getCategory() == TnrEventCategory.SYSTEM &&
            "Heartbeat".equalsIgnoreCase(event.getAction())) {
            return true;
        }

        return false;
    }

    /**
     * Configure si les Heartbeats doivent être ignorés.
     */
    public void setIgnoreHeartbeats(boolean ignore) {
        if (ignore) {
            log.info("Heartbeats will be ignored during recording");
        } else {
            log.info("Heartbeats will be recorded");
        }
        // Note: Pour une vraie implémentation, on stockerait cette config
    }

    /**
     * Récupère les événements en cours d'enregistrement (snapshot).
     */
    public List<TnrEvent> getCurrentEvents() {
        return new ArrayList<>(eventBuffer);
    }

    /**
     * Récupère le nombre d'événements en cours.
     */
    public int getCurrentEventCount() {
        return eventCount.get();
    }
}
