package com.evse.simulator.performance;

import com.evse.simulator.performance.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controleur REST pour les tests de performance haute capacite.
 * API compatible avec le frontend existant.
 */
@Slf4j
@RestController
@RequestMapping("/api/highperf")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HighPerfController {

    private final PerformanceEngine engine;
    private final CopyOnWriteArrayList<SseEmitter> sseEmitters = new CopyOnWriteArrayList<>();

    /**
     * Demarre un test de performance.
     * POST /api/perf/start
     */
    @PostMapping("/start")
    public ResponseEntity<PerfResult> startTest(@RequestBody PerfConfig config) {
        try {
            log.info("Demarrage test performance: scenario={}, target={}",
                    config.getScenario(), config.getTargetConnections());

            // Configurer callback pour SSE
            engine.setMetricsCallback(metrics -> {
                broadcastMetrics(metrics);
            });

            engine.setCompletionCallback(result -> {
                broadcastCompletion(result);
            });

            PerfResult result = engine.startTest(config);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                PerfResult.builder()
                    .status(PerfStatus.FAILED)
                    .error(e.getMessage())
                    .build()
            );
        } catch (Exception e) {
            log.error("Erreur demarrage test", e);
            return ResponseEntity.internalServerError().body(
                PerfResult.builder()
                    .status(PerfStatus.FAILED)
                    .error("Erreur interne: " + e.getMessage())
                    .build()
            );
        }
    }

    /**
     * Arrete le test en cours.
     * POST /api/perf/stop
     */
    @PostMapping("/stop")
    public ResponseEntity<PerfResult> stopTest() {
        try {
            PerfResult result = engine.stopTest();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erreur arret test", e);
            return ResponseEntity.internalServerError().body(
                PerfResult.builder()
                    .status(PerfStatus.FAILED)
                    .error("Erreur arret: " + e.getMessage())
                    .build()
            );
        }
    }

    /**
     * Retourne le statut actuel.
     * GET /api/perf/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        PerfResult result = engine.getCurrentResult();
        PerfMetrics metrics = engine.getCurrentMetrics();

        Map<String, Object> response = new ConcurrentHashMap<>();
        response.put("status", engine.getStatus());
        if (result != null) {
            response.put("testId", result.getTestId());
            response.put("config", result.getConfig());
            response.put("startTime", result.getStartTime());
        }
        if (metrics != null) {
            response.put("metrics", metrics);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Retourne les metriques actuelles.
     * GET /api/perf/metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<PerfMetrics> getMetrics() {
        return ResponseEntity.ok(engine.getCurrentMetrics());
    }

    /**
     * Retourne le resultat actuel ou final.
     * GET /api/perf/result
     */
    @GetMapping("/result")
    public ResponseEntity<PerfResult> getResult() {
        PerfResult result = engine.getCurrentResult();
        if (result == null) {
            return ResponseEntity.noContent().build();
        }

        // Mettre a jour avec les metriques actuelles
        if (engine.getStatus() == PerfStatus.RUNNING) {
            result.setFinalMetrics(engine.getCurrentMetrics());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Stream SSE des metriques temps reel.
     * GET /api/perf/stream
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics() {
        SseEmitter emitter = new SseEmitter(0L); // No timeout

        emitter.onCompletion(() -> {
            sseEmitters.remove(emitter);
            log.debug("SSE emitter completed");
        });

        emitter.onTimeout(() -> {
            sseEmitters.remove(emitter);
            log.debug("SSE emitter timeout");
        });

        emitter.onError(e -> {
            sseEmitters.remove(emitter);
            log.debug("SSE emitter error: {}", e.getMessage());
        });

        sseEmitters.add(emitter);
        log.debug("New SSE subscriber, total: {}", sseEmitters.size());

        // Envoyer le statut initial
        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(Map.of("status", engine.getStatus())));
        } catch (IOException e) {
            log.debug("Erreur envoi statut initial");
        }

        return emitter;
    }

    /**
     * Diffuse les metriques a tous les clients SSE.
     */
    private void broadcastMetrics(PerfMetrics metrics) {
        for (SseEmitter emitter : sseEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("metrics")
                        .data(metrics));
            } catch (Exception e) {
                sseEmitters.remove(emitter);
            }
        }
    }

    /**
     * Diffuse la completion du test.
     */
    private void broadcastCompletion(PerfResult result) {
        for (SseEmitter emitter : sseEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("completed")
                        .data(result));
            } catch (Exception e) {
                sseEmitters.remove(emitter);
            }
        }
    }

    // === API de compatibilite avec l'ancien format ===

    /**
     * API legacy: demarrage simple.
     * POST /api/highperf/legacy/start
     */
    @PostMapping("/legacy/start")
    public ResponseEntity<Map<String, Object>> legacyStart(
            @RequestParam(defaultValue = "1000") int targetConnections,
            @RequestParam(defaultValue = "60") int rampUpSeconds,
            @RequestParam(defaultValue = "30") int holdSeconds,
            @RequestParam String ocppUrl) {

        PerfConfig config = PerfConfig.builder()
                .scenarioType(PerfConfig.ScenarioType.CONNECTION)
                .targetConnections(targetConnections)
                .rampUpSeconds(rampUpSeconds)
                .holdSeconds(holdSeconds)
                .ocppUrl(ocppUrl)
                .build();

        try {
            engine.startTest(config);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "testId", engine.getCurrentResult().getTestId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * API legacy: statut simplifie.
     * GET /api/perf/legacy/status
     */
    @GetMapping("/legacy/status")
    public ResponseEntity<Map<String, Object>> legacyStatus() {
        PerfStatus status = engine.getStatus();
        PerfMetrics metrics = engine.getCurrentMetrics();

        Map<String, Object> response = new ConcurrentHashMap<>();
        response.put("running", status == PerfStatus.RUNNING);
        response.put("status", status.name().toLowerCase());

        if (metrics != null) {
            response.put("activeConnections", metrics.getActiveConnections());
            response.put("successfulConnections", metrics.getSuccessfulConnections());
            response.put("failedConnections", metrics.getFailedConnections());
            response.put("throughput", metrics.getThroughputMsgPerSec());
            response.put("latencyP95", metrics.getConnectionLatencyP95Ms());
            response.put("progress", metrics.getProgressPercent());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Health check.
     * GET /api/perf/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Runtime runtime = Runtime.getRuntime();
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "engine", engine.getStatus(),
            "memory", Map.of(
                "used", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + " MB",
                "max", runtime.maxMemory() / (1024 * 1024) + " MB"
            ),
            "threads", Thread.activeCount()
        ));
    }
}
