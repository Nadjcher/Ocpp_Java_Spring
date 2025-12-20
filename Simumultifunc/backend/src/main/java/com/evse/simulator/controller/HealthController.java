package com.evse.simulator.controller;

import com.evse.simulator.domain.service.MetricsService;
import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.dto.response.HealthStatusResponse;
import com.evse.simulator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controleur REST pour les endpoints de sante.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "Etat de sante de l'application")
@RequiredArgsConstructor
@CrossOrigin
public class HealthController {

    private final SessionService sessionService;
    private final OCPPService ocppService;
    private final MetricsService metricsService;
    private final Optional<BuildProperties> buildProperties;

    private final LocalDateTime startTime = LocalDateTime.now();

    @GetMapping("/health")
    @Operation(
            summary = "Etat de sante",
            description = "Retourne l'etat de sante global avec informations sur les sessions, WebSocket et systeme"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Systeme operationnel",
                    content = @Content(schema = @Schema(implementation = HealthStatusResponse.class))),
            @ApiResponse(responseCode = "503", description = "Systeme degrade")
    })
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();

        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("uptime", getUptime());
        health.put("uptimeSeconds", getUptimeSeconds());

        // Version info
        Map<String, String> version = new LinkedHashMap<>();
        version.put("name", "EVSE Simulator");
        version.put("version", buildProperties.map(BuildProperties::getVersion).orElse("2.0.0"));
        buildProperties.ifPresent(props -> version.put("artifact", props.getArtifact()));
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
        system.put("usedMemoryMb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        system.put("activeThreads", Thread.activeCount());
        health.put("system", system);

        return ResponseEntity.ok(health);
    }

    @GetMapping("/info")
    @Operation(
            summary = "Informations application",
            description = "Retourne les informations sur l'application, version, endpoints disponibles"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Informations retournees")
    })
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("name", "EVSE Simulator Backend");
        info.put("description", "Spring Boot backend for EVSE OCPP 1.6 simulator");
        info.put("version", buildProperties.map(BuildProperties::getVersion).orElse("2.0.0"));

        buildProperties.ifPresent(props -> info.put("buildTime", props.getTime()));

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

        // Tags disponibles
        Map<String, String> apiGroups = new LinkedHashMap<>();
        apiGroups.put("core", "Sessions, Smart Charging, Vehicles, OCPP");
        apiGroups.put("testing", "TNR, OCPI, Performance");
        info.put("apiGroups", apiGroups);

        return ResponseEntity.ok(info);
    }

    @GetMapping("/ready")
    @Operation(
            summary = "Readiness check",
            description = "Verifie si l'application est prete a recevoir du trafic (Kubernetes probe)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application prete"),
            @ApiResponse(responseCode = "503", description = "Application non prete")
    })
    public ResponseEntity<Map<String, Object>> ready() {
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
        response.put("status", ready ? "UP" : "DOWN");
        response.put("checks", checks);
        response.put("timestamp", LocalDateTime.now());

        return ready ?
                ResponseEntity.ok(response) :
                ResponseEntity.status(503).body(response);
    }

    @GetMapping("/live")
    @Operation(
            summary = "Liveness check",
            description = "Verifie si l'application est en vie (Kubernetes probe)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application en vie")
    })
    public ResponseEntity<Map<String, Object>> live() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    private String getUptime() {
        Duration duration = Duration.between(startTime, LocalDateTime.now());
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

    private long getUptimeSeconds() {
        return Duration.between(startTime, LocalDateTime.now()).getSeconds();
    }
}
