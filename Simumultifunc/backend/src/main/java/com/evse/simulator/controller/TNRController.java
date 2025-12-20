package com.evse.simulator.controller;

import com.evse.simulator.domain.service.TNRService;
import com.evse.simulator.model.TNRScenario;
import com.evse.simulator.model.TNRScenario.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Contrôleur REST pour les Tests Non-Régressifs (TNR).
 */
@RestController
@RequestMapping("/api/tnr")
@Tag(name = "TNR", description = "Tests non-régressifs et scénarios")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class TNRController {

    private final TNRService tnrService;

    @GetMapping("/status")
    @Operation(summary = "Statut global TNR (compat frontend)")
    public ResponseEntity<Map<String, Object>> getGlobalStatus() {
        return ResponseEntity.ok(Map.of(
            "running", false,
            "recording", false,
            "scenariosCount", tnrService.getAllScenarios().size(),
            "resultsCount", tnrService.getAllResults().size()
        ));
    }

    // =========================================================================
    // Compatibility endpoints for frontend (TnrTab.tsx)
    // =========================================================================

    @GetMapping("")
    @Operation(summary = "Liste les IDs des scénarios (compat frontend)")
    public ResponseEntity<List<String>> listScenarioIds() {
        List<String> ids = tnrService.getAllScenarios().stream()
            .map(TNRScenario::getId)
            .toList();
        return ResponseEntity.ok(ids);
    }

    @GetMapping("/list")
    @Operation(summary = "Liste tous les scénarios (compat frontend /api/tnr/list)")
    public ResponseEntity<List<Map<String, Object>>> listScenarios() {
        List<Map<String, Object>> list = tnrService.getAllScenarios().stream()
            .map(s -> {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", s.getId());
                m.put("name", s.getName() != null ? s.getName() : s.getId());
                m.put("description", s.getDescription());
                m.put("eventsCount", s.getSteps() != null ? s.getSteps().size() : 0);

                // Category/folder for organization
                String category = s.getCategory() != null ? s.getCategory() : "recorded";
                m.put("category", category);
                m.put("folder", category);

                // Tags for filtering
                m.put("tags", s.getTags() != null ? s.getTags() : List.of("auto-recorded", "tnr"));

                // Status
                m.put("status", s.getStatus() != null ? s.getStatus().name() : "PENDING");

                // Config with URL and cpId
                Map<String, Object> config = new java.util.HashMap<>();
                if (s.getConfig() != null) {
                    config.put("url", s.getConfig().getCsmsUrl() != null ? s.getConfig().getCsmsUrl() : "");
                    config.put("cpId", s.getConfig().getCpId() != null ? s.getConfig().getCpId() : "");
                } else {
                    config.put("url", "");
                    config.put("cpId", "");
                }
                m.put("config", config);

                return m;
            })
            .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/executions")
    @Operation(summary = "Liste toutes les exécutions (compat frontend)")
    public ResponseEntity<List<Map<String, Object>>> listExecutions() {
        List<Map<String, Object>> execs = tnrService.getAllResults().stream()
            .map(r -> {
                Map<String, Object> m = new java.util.HashMap<>();
                String execId = r.getScenarioId() + "-" + (r.getExecutedAt() != null ? r.getExecutedAt().toString() : System.currentTimeMillis());
                m.put("executionId", execId);
                m.put("scenarioId", r.getScenarioId());
                m.put("timestamp", r.getExecutedAt() != null ? r.getExecutedAt().toString() : "");
                m.put("passed", r.getStatus() == ScenarioStatus.PASSED);
                m.put("metrics", Map.of(
                    "differences", 0,
                    "totalEvents", r.getStepResults() != null ? r.getStepResults().size() : 0,
                    "durationMs", r.getDurationMs()
                ));
                return m;
            })
            .toList();
        return ResponseEntity.ok(execs);
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "Récupère les détails d'une exécution (compat frontend)")
    public ResponseEntity<Map<String, Object>> getExecution(@PathVariable String executionId) {
        // Extraire le scenarioId de l'executionId (format: scenarioId-timestamp)
        String scenarioId = executionId;
        if (executionId.contains("-") && executionId.lastIndexOf("-") > 0) {
            int lastDash = executionId.lastIndexOf("-");
            String lastPart = executionId.substring(lastDash + 1);
            if (lastPart.matches("\\d+")) {
                scenarioId = executionId.substring(0, lastDash);
            }
        }

        // Vérifier d'abord les tests en cours
        final String finalScenarioId = scenarioId;
        TNRResult running = tnrService.getRunningResult(scenarioId);
        if (running != null) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("executionId", executionId);
            m.put("scenarioId", running.getScenarioId());
            m.put("status", "running");
            m.put("startedAt", running.getExecutedAt() != null ? running.getExecutedAt().toString() : "");
            m.put("metrics", Map.of(
                "differences", 0,
                "totalEvents", running.getStepResults() != null ? running.getStepResults().size() : 0,
                "durationMs", running.getDurationMs()
            ));
            m.put("logs", running.getLogs() != null ? running.getLogs() : List.of());
            m.put("events", List.of());
            m.put("differences", List.of());
            return ResponseEntity.ok(m);
        }

        // Try to find a matching result in completed tests
        TNRResult result = tnrService.getAllResults().stream()
            .filter(r -> r.getScenarioId().equals(finalScenarioId) ||
                        r.getScenarioId().equals(executionId))
            .findFirst()
            .orElse(null);

        if (result == null) {
            // Return a placeholder if not found
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("executionId", executionId);
            m.put("scenarioId", executionId);
            m.put("status", "not_found");
            m.put("startedAt", "");
            m.put("logs", List.of());
            m.put("events", List.of());
            m.put("differences", List.of());
            return ResponseEntity.ok(m);
        }

        Map<String, Object> m = new java.util.HashMap<>();
        m.put("executionId", executionId);
        m.put("scenarioId", result.getScenarioId());
        m.put("status", result.getStatus() == ScenarioStatus.PASSED ? "success" : "failed");
        m.put("startedAt", result.getExecutedAt() != null ? result.getExecutedAt().toString() : "");
        m.put("finishedAt", ""); // TNRResult n'a pas de endTime séparé
        m.put("metrics", Map.of(
            "differences", 0,
            "totalEvents", result.getStepResults() != null ? result.getStepResults().size() : 0,
            "durationMs", result.getDurationMs()
        ));
        m.put("logs", result.getLogs() != null ? result.getLogs() : List.of());
        m.put("events", List.of());
        m.put("differences", List.of());
        return ResponseEntity.ok(m);
    }

    @GetMapping("/executions/{executionId}/logs")
    @Operation(summary = "Récupère les logs d'une exécution (compat frontend)")
    public ResponseEntity<List<String>> getExecutionLogs(@PathVariable String executionId) {
        log.debug("Getting logs for execution: {}", executionId);

        // Extraire le scenarioId de l'executionId (format: scenarioId-timestamp)
        String scenarioId = executionId;
        if (executionId.contains("-") && executionId.lastIndexOf("-") > 0) {
            // Essayer d'extraire scenarioId (tout avant le dernier segment numérique)
            int lastDash = executionId.lastIndexOf("-");
            String lastPart = executionId.substring(lastDash + 1);
            if (lastPart.matches("\\d+")) {
                scenarioId = executionId.substring(0, lastDash);
            }
        }

        // Vérifier d'abord les tests en cours (priorité)
        TNRResult running = tnrService.getRunningResult(scenarioId);
        if (running != null && running.getLogs() != null) {
            log.debug("Found running test logs for {}: {} entries", scenarioId, running.getLogs().size());
            return ResponseEntity.ok(running.getLogs());
        }

        // Chercher dans les résultats terminés
        final String finalScenarioId = scenarioId;
        TNRResult result = tnrService.getAllResults().stream()
            .filter(r -> r.getScenarioId().equals(finalScenarioId) ||
                        r.getScenarioId().equals(executionId))
            .findFirst()
            .orElse(null);

        if (result != null && result.getLogs() != null) {
            log.debug("Found completed test logs for {}: {} entries", scenarioId, result.getLogs().size());
            return ResponseEntity.ok(result.getLogs());
        }

        log.debug("No logs found for execution: {}", executionId);
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/executions/{executionId}/events")
    @Operation(summary = "Récupère les events d'une exécution (compat frontend)")
    public ResponseEntity<List<Map<String, Object>>> getExecutionEvents(@PathVariable String executionId) {
        // Chercher le résultat correspondant
        TNRResult result = tnrService.getAllResults().stream()
            .filter(r -> {
                String execId = r.getScenarioId() + "-" + (r.getExecutedAt() != null ? r.getExecutedAt().toString() : "");
                return execId.equals(executionId) || r.getScenarioId().equals(executionId);
            })
            .findFirst()
            .orElse(null);

        if (result != null && result.getStepResults() != null) {
            List<Map<String, Object>> events = new ArrayList<>();
            int index = 0;
            for (var stepResult : result.getStepResults()) {
                Map<String, Object> event = new HashMap<>();
                event.put("index", index++);
                event.put("ts", result.getExecutedAt() != null ? result.getExecutedAt().toString() : "");
                event.put("direction", "out");
                event.put("action", stepResult.getMessage());
                event.put("payload", stepResult.getResponse());
                event.put("passed", stepResult.isPassed());
                events.add(event);
            }
            return ResponseEntity.ok(events);
        }

        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/run/{scenarioId}")
    @Operation(summary = "Lance un scénario TNR (compat frontend)")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> runScenarioCompat(
            @PathVariable String scenarioId,
            @RequestParam(required = false) String url,
            @RequestParam(required = false, defaultValue = "fast") String mode,
            @RequestParam(required = false, defaultValue = "1") double speed) {
        log.info("Running TNR scenario {} with url={}, mode={}, speed={}", scenarioId, url, mode, speed);
        String executionId = scenarioId + "-" + System.currentTimeMillis();
        return tnrService.runScenario(scenarioId)
            .thenApply(result -> {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("executionId", executionId);
                m.put("ok", result.getStatus() == ScenarioStatus.PASSED);
                return ResponseEntity.ok(m);
            })
            .exceptionally(ex -> {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("executionId", executionId);
                m.put("ok", false);
                m.put("error", ex.getMessage());
                return ResponseEntity.ok(m);
            });
    }

    @GetMapping("/scenarios")
    @Operation(summary = "Liste tous les scénarios")
    public ResponseEntity<List<TNRScenario>> getAllScenarios() {
        return ResponseEntity.ok(tnrService.getAllScenarios());
    }

    @GetMapping("/scenarios/{id}")
    @Operation(summary = "Récupère un scénario par ID")
    public ResponseEntity<TNRScenario> getScenario(@PathVariable String id) {
        return ResponseEntity.ok(tnrService.getScenario(id));
    }

    @PostMapping("/scenarios")
    @Operation(summary = "Crée un nouveau scénario")
    public ResponseEntity<TNRScenario> createScenario(@Valid @RequestBody TNRScenario scenario) {
        TNRScenario created = tnrService.createScenario(scenario);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/scenarios/{id}")
    @Operation(summary = "Met à jour un scénario")
    public ResponseEntity<TNRScenario> updateScenario(
            @PathVariable String id,
            @RequestBody TNRScenario updates) {
        return ResponseEntity.ok(tnrService.updateScenario(id, updates));
    }

    @DeleteMapping("/scenarios/{id}")
    @Operation(summary = "Supprime un scénario")
    public ResponseEntity<Void> deleteScenario(@PathVariable String id) {
        tnrService.deleteScenario(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/scenarios/{id}/run")
    @Operation(summary = "Exécute un scénario")
    public CompletableFuture<ResponseEntity<TNRResult>> runScenario(@PathVariable String id) {
        return tnrService.runScenario(id)
                .thenApply(result -> {
                    HttpStatus status = result.getStatus() == ScenarioStatus.PASSED ?
                            HttpStatus.OK : HttpStatus.EXPECTATION_FAILED;
                    return ResponseEntity.status(status).body(result);
                });
    }

    @GetMapping("/scenarios/{id}/status")
    @Operation(summary = "Vérifie si un scénario est en cours")
    public ResponseEntity<Map<String, Object>> getScenarioStatus(@PathVariable String id) {
        boolean running = tnrService.isRunning(id);
        TNRResult result = tnrService.getResult(id);

        return ResponseEntity.ok(Map.of(
                "scenarioId", id,
                "running", running,
                "lastResult", result != null ? result : "no result"
        ));
    }

    @GetMapping("/results")
    @Operation(summary = "Récupère tous les résultats")
    public ResponseEntity<List<TNRResult>> getAllResults() {
        return ResponseEntity.ok(tnrService.getAllResults());
    }

    @GetMapping("/results/{scenarioId}")
    @Operation(summary = "Récupère le résultat d'un scénario")
    public ResponseEntity<TNRResult> getResult(@PathVariable String scenarioId) {
        TNRResult result = tnrService.getResult(scenarioId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/export/xray")
    @Operation(summary = "Exporte les résultats au format Jira Xray")
    public ResponseEntity<Map<String, Object>> exportToXray() {
        return ResponseEntity.ok(tnrService.exportToXray());
    }

    // =========================================================================
    // TNR Recorder endpoints (compatibility with frontend)
    // =========================================================================

    @PostMapping("/recorder/start")
    @Operation(summary = "Démarre l'enregistrement TNR")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> startRecorder(@RequestBody(required = false) Map<String, Object> body) {
        String sessionId = body != null ? (String) body.get("sessionId") : null;
        String name = body != null ? (String) body.get("name") : "Recording-" + System.currentTimeMillis();
        String category = body != null ? (String) body.get("category") : "recorded";
        List<String> tags = body != null && body.get("tags") instanceof List ?
            (List<String>) body.get("tags") : List.of("auto-recorded", "tnr");
        String executionId = "exec-" + System.currentTimeMillis();

        log.info("Starting TNR recorder: name={}, category={}, tags={} (executionId: {})", name, category, tags, executionId);

        // Stocker les métadonnées pour l'utiliser lors du stop
        tnrService.startRecording(executionId, name);

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "recording", true,
            "executionId", executionId,
            "sessionId", sessionId != null ? sessionId : "",
            "name", name,
            "category", category,
            "tags", tags
        ));
    }

    @PostMapping("/recorder/stop")
    @Operation(summary = "Arrête l'enregistrement TNR et sauvegarde")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> stopRecorder(@RequestBody(required = false) Map<String, Object> body) {
        String sessionId = body != null ? (String) body.get("sessionId") : null;
        String scenarioName = body != null ? (String) body.get("name") : null;
        String category = body != null ? (String) body.get("category") : "recorded";
        List<String> tags = null;
        if (body != null && body.get("tags") instanceof List) {
            tags = (List<String>) body.get("tags");
        }

        log.info("Stopping TNR recorder: name={}, category={}, tags={}", scenarioName, category, tags);

        // Arrêter l'enregistrement et sauvegarder avec les métadonnées
        String executionId = tnrService.stopRecordingAndSave(scenarioName, category, tags);

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "recording", false,
            "executionId", executionId != null ? executionId : "",
            "sessionId", sessionId != null ? sessionId : "",
            "saved", executionId != null
        ));
    }

    @PostMapping("/record/start")
    @Operation(summary = "Démarre l'enregistrement TNR (alias)")
    public ResponseEntity<Map<String, Object>> startRecord(@RequestBody(required = false) Map<String, Object> body) {
        return startRecorder(body);
    }

    @PostMapping("/record/stop")
    @Operation(summary = "Arrête l'enregistrement TNR (alias)")
    public ResponseEntity<Map<String, Object>> stopRecord(@RequestBody(required = false) Map<String, Object> body) {
        return stopRecorder(body);
    }

    @PostMapping("/tap")
    @Operation(summary = "Enregistre un événement TNR (tap)")
    public ResponseEntity<Map<String, Object>> recordTap(@RequestBody(required = false) Map<String, Object> body) {
        String sessionId = body != null ? (String) body.get("sessionId") : null;
        String event = body != null ? (String) body.get("event") : null;
        log.info("TNR tap event for session {}: {}", sessionId, event);
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "event", event != null ? event : "",
            "sessionId", sessionId != null ? sessionId : ""
        ));
    }
}