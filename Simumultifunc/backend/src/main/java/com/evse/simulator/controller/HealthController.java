package com.evse.simulator.controller;

import com.evse.simulator.domain.service.MetricsService;
import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Contrôleur REST pour les endpoints de santé.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "État de santé de l'application")
@RequiredArgsConstructor
@CrossOrigin
public class HealthController {

    private final SessionService sessionService;
    private final OCPPService ocppService;
    private final MetricsService metricsService;
    private final Optional<BuildProperties> buildProperties;

    private final LocalDateTime startTime = LocalDateTime.now();

    @GetMapping("/health")
    @Operation(summary = "Vérifie l'état de santé de l'application")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();

        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("uptime", getUptime());

        // Version info
        Map<String, String> version = new LinkedHashMap<>();
        version.put("name", "EVSE Simulator");
        buildProperties.ifPresent(props -> {
            version.put("version", props.getVersion());
            version.put("artifact", props.getArtifact());
        });
        health.put("application", version);

        // Sessions info
        Map<String, Object> sessions = new LinkedHashMap<>();
        sessions.put("total", sessionService.countSessions());
        sessions.put("connected", sessionService.getConnectedSessions().size());
        sessions.put("charging", sessionService.getChargingSessions().size());
        health.put("sessions", sessions);

        // WebSocket info
        Map<String, Object> websocket = new LinkedHashMap<>();
        websocket.put("activeConnections", ocppService.getActiveConnectionsCount());
        health.put("websocket", websocket);

        // System info
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("availableProcessors", runtime.availableProcessors());
        system.put("freeMemoryMb", runtime.freeMemory() / (1024 * 1024));
        system.put("totalMemoryMb", runtime.totalMemory() / (1024 * 1024));
        system.put("maxMemoryMb", runtime.maxMemory() / (1024 * 1024));
        system.put("activeThreads", Thread.activeCount());
        health.put("system", system);

        return ResponseEntity.ok(health);
    }

    @GetMapping("/info")
    @Operation(summary = "Informations sur l'application")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("name", "EVSE Simulator Backend");
        info.put("description", "Spring Boot backend for EVSE OCPP 1.6 simulator");

        buildProperties.ifPresent(props -> {
            info.put("version", props.getVersion());
            info.put("buildTime", props.getTime());
        });

        info.put("ocppVersion", "1.6");
        info.put("maxSessions", 25000);
        info.put("startTime", startTime);
        info.put("uptime", getUptime());

        // Endpoints info
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("api", "/api/**");
        endpoints.put("swagger", "/swagger-ui.html");
        endpoints.put("apiDocs", "/v3/api-docs");
        endpoints.put("websocket", "/ws (STOMP)");
        endpoints.put("actuator", "/actuator");
        info.put("endpoints", endpoints);

        return ResponseEntity.ok(info);
    }

    @GetMapping("/ready")
    @Operation(summary = "Vérifie si l'application est prête")
    public ResponseEntity<Map<String, Object>> ready() {
        // Vérifier que tous les services sont initialisés
        boolean ready = true;
        Map<String, Boolean> checks = new LinkedHashMap<>();

        try {
            sessionService.countSessions();
            checks.put("sessionService", true);
        } catch (Exception e) {
            checks.put("sessionService", false);
            ready = false;
        }

        try {
            metricsService.collectMetrics();
            checks.put("metricsService", true);
        } catch (Exception e) {
            checks.put("metricsService", false);
            ready = false;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ready", ready);
        response.put("checks", checks);
        response.put("timestamp", LocalDateTime.now());

        return ready ?
                ResponseEntity.ok(response) :
                ResponseEntity.status(503).body(response);
    }

    private String getUptime() {
        java.time.Duration duration = java.time.Duration.between(startTime, LocalDateTime.now());
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}