package com.evse.simulator.controller;

import com.evse.simulator.domain.service.LoadTestService;
import com.evse.simulator.domain.service.LoadTestService.LoadTestStatus;
import com.evse.simulator.domain.service.LoadTestService.SessionStats;
import com.evse.simulator.domain.service.MetricsService;
import com.evse.simulator.model.PerformanceMetrics;
import com.evse.simulator.model.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour les métriques de performance et tests de charge.
 */
@RestController
@RequestMapping("/api/performance")
@Tag(name = "Performance", description = "Tests de performance et métriques")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class PerformanceController {

    private final MetricsService metricsService;
    private final LoadTestService loadTestService;

    // =========================================================================
    // Metrics Endpoints
    // =========================================================================

    @GetMapping("/metrics")
    @Operation(summary = "Récupère les métriques globales")
    public ResponseEntity<PerformanceMetrics> getMetrics() {
        return ResponseEntity.ok(metricsService.collectMetrics());
    }

    @PostMapping("/reset-counters")
    @Operation(summary = "Réinitialise les compteurs de métriques")
    public ResponseEntity<Map<String, String>> resetCounters() {
        metricsService.resetCounters();
        return ResponseEntity.ok(Map.of("status", "counters reset"));
    }

    // =========================================================================
    // Load Test Endpoints
    // =========================================================================

    @GetMapping("/sessions")
    @Operation(summary = "Récupère les statistiques par session")
    public ResponseEntity<List<SessionStats>> getSessionStats() {
        return ResponseEntity.ok(loadTestService.getSessionStats());
    }

    @PostMapping("/start-load-test")
    @Operation(summary = "Démarre un test de charge")
    public ResponseEntity<Map<String, Object>> startLoadTest(
            @RequestParam(defaultValue = "100") int targetSessions,
            @RequestParam(defaultValue = "60") int rampUpSeconds,
            @RequestParam(defaultValue = "60") int holdSeconds,
            @RequestBody Session sessionTemplate) {

        String testId = loadTestService.startLoadTest(targetSessions, rampUpSeconds, holdSeconds, sessionTemplate);

        return ResponseEntity.ok(Map.of(
                "testId", testId,
                "targetSessions", targetSessions,
                "rampUpSeconds", rampUpSeconds,
                "holdSeconds", holdSeconds,
                "status", "started"
        ));
    }

    @PostMapping("/stop-load-test")
    @Operation(summary = "Arrête le test de charge en cours")
    public ResponseEntity<Map<String, Object>> stopLoadTest() {
        loadTestService.stopLoadTest();

        LoadTestStatus status = loadTestService.getLoadTestStatus();
        return ResponseEntity.ok(Map.of(
                "status", "stopped",
                "finalStatus", status != null ? status : "no test was running"
        ));
    }

    @GetMapping("/load-test-status")
    @Operation(summary = "Récupère l'état du test de charge")
    public ResponseEntity<LoadTestStatus> getLoadTestStatus() {
        LoadTestStatus status = loadTestService.getLoadTestStatus();
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
