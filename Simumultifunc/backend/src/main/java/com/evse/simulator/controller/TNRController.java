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
    public ResponseEntity<Map<String, Object>> startRecorder(@RequestBody(required = false) Map<String, Object> body) {
        String sessionId = body != null ? (String) body.get("sessionId") : null;
        String name = body != null ? (String) body.get("name") : "Recording";
        log.info("Starting TNR recorder for session {} with name {}", sessionId, name);
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "recording", true,
            "sessionId", sessionId != null ? sessionId : "",
            "name", name
        ));
    }

    @PostMapping("/recorder/stop")
    @Operation(summary = "Arrête l'enregistrement TNR")
    public ResponseEntity<Map<String, Object>> stopRecorder(@RequestBody(required = false) Map<String, Object> body) {
        String sessionId = body != null ? (String) body.get("sessionId") : null;
        log.info("Stopping TNR recorder for session {}", sessionId);
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "recording", false,
            "sessionId", sessionId != null ? sessionId : ""
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