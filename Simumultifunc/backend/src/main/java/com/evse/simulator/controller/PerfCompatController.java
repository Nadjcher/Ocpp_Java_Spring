package com.evse.simulator.controller;

import com.evse.simulator.domain.service.LoadTestService;
import com.evse.simulator.domain.service.MetricsService;
import com.evse.simulator.model.PerformanceMetrics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Contrôleur de compatibilité pour les endpoints /api/perf/* et /api/metrics
 * attendus par le frontend.
 */
@RestController
@Tag(name = "Perf Compat", description = "API de compatibilité pour le frontend (anciens endpoints /api/perf)")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class PerfCompatController {

    private final MetricsService metricsService;
    private final LoadTestService loadTestService;

    // =========================================================================
    // /api/metrics
    // =========================================================================

    @GetMapping("/api/metrics")
    @Operation(summary = "Récupère les métriques de performance (compat /api/metrics)")
    public ResponseEntity<PerformanceMetrics> getMetrics() {
        return ResponseEntity.ok(metricsService.collectMetrics());
    }

    // =========================================================================
    // /api/perf/*
    // =========================================================================

    @GetMapping("/api/perf/status")
    @Operation(summary = "Statut du test de charge (compat /api/perf/status)")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean running = loadTestService.isRunning();
        return ResponseEntity.ok(Map.of(
            "running", running,
            "status", running ? "RUNNING" : "IDLE",
            "runId", loadTestService.getCurrentRunId()
        ));
    }

    @PostMapping("/api/perf/start")
    @Operation(summary = "Démarre un test de charge (compat /api/perf/start)")
    public ResponseEntity<Map<String, Object>> startTest(@RequestBody Map<String, Object> config) {
        try {
            String runId = loadTestService.startLoadTest(config);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "runId", runId
            ));
        } catch (Exception e) {
            log.error("Error starting load test", e);
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/api/perf/stop")
    @Operation(summary = "Arrête le test de charge (compat /api/perf/stop)")
    public ResponseEntity<Map<String, Object>> stopTest() {
        try {
            loadTestService.stopLoadTest();
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/api/perf/csv-template")
    @Operation(summary = "Retourne un template CSV (compat /api/perf/csv-template)")
    public ResponseEntity<String> getCsvTemplate() {
        String template = """
            url,cpId,idTag,auto,holdSec,mvEverySec
            ws://localhost:8887/ocpp,CP001,TAG001,true,30,10
            ws://localhost:8887/ocpp,CP002,TAG002,true,30,10
            ws://localhost:8887/ocpp,CP003,TAG003,true,30,10
            """;
        return ResponseEntity.ok(template);
    }

    @PostMapping("/api/perf/import")
    @Operation(summary = "Importe des sessions depuis CSV (compat /api/perf/import)")
    public ResponseEntity<Map<String, Object>> importCsv(@RequestBody Map<String, Object> body) {
        try {
            String csv = (String) body.get("csv");
            if (csv == null || csv.isBlank()) {
                return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "error", "CSV content is required"
                ));
            }

            // Parse CSV and create sessions
            String[] lines = csv.split("\n");
            int count = 0;
            int errors = 0;

            // Parse header to get column indices
            String[] headers = lines[0].split(",");
            int urlIdx = -1, cpIdIdx = -1, idTagIdx = -1, autoIdx = -1, holdSecIdx = -1, mvEverySecIdx = -1;

            for (int h = 0; h < headers.length; h++) {
                String header = headers[h].trim().toLowerCase();
                switch (header) {
                    case "url" -> urlIdx = h;
                    case "cpid" -> cpIdIdx = h;
                    case "idtag" -> idTagIdx = h;
                    case "auto" -> autoIdx = h;
                    case "holdsec" -> holdSecIdx = h;
                    case "mveverysec" -> mvEverySecIdx = h;
                }
            }

            if (urlIdx == -1 || cpIdIdx == -1) {
                return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "error", "CSV must contain 'url' and 'cpId' columns"
                ));
            }

            java.util.List<Map<String, Object>> createdSessions = new java.util.ArrayList<>();

            for (int i = 1; i < lines.length; i++) { // Skip header
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                try {
                    String[] values = line.split(",");

                    String url = values.length > urlIdx ? values[urlIdx].trim() : "";
                    String cpId = values.length > cpIdIdx ? values[cpIdIdx].trim() : "";
                    String idTag = idTagIdx >= 0 && values.length > idTagIdx ? values[idTagIdx].trim() : "TEST-TAG";
                    boolean auto = autoIdx >= 0 && values.length > autoIdx && "true".equalsIgnoreCase(values[autoIdx].trim());
                    int holdSec = holdSecIdx >= 0 && values.length > holdSecIdx ? Integer.parseInt(values[holdSecIdx].trim()) : 30;
                    int mvEverySec = mvEverySecIdx >= 0 && values.length > mvEverySecIdx ? Integer.parseInt(values[mvEverySecIdx].trim()) : 10;

                    if (url.isEmpty() || cpId.isEmpty()) {
                        errors++;
                        continue;
                    }

                    // Create session via LoadTestService
                    Map<String, Object> sessionConfig = Map.of(
                        "url", url,
                        "cpId", cpId,
                        "idTag", idTag,
                        "auto", auto,
                        "holdSec", holdSec,
                        "mvEverySec", mvEverySec
                    );

                    createdSessions.add(sessionConfig);
                    count++;
                } catch (Exception lineError) {
                    log.warn("Error parsing CSV line {}: {}", i, lineError.getMessage());
                    errors++;
                }
            }

            // Start load test with parsed sessions if auto mode
            if (!createdSessions.isEmpty()) {
                loadTestService.startLoadTest(Map.of(
                    "sessions", createdSessions,
                    "type", "csv-import"
                ));
            }

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "count", count,
                "errors", errors,
                "sessions", createdSessions
            ));
        } catch (Exception e) {
            log.error("Error importing CSV", e);
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "error", e.getMessage()
            ));
        }
    }
}
