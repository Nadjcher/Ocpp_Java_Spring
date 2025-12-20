package com.evse.simulator.controller;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.dto.request.session.CreateSessionRequest;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * Controleur REST pour la gestion des sessions de charge.
 */
@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Sessions", description = "Gestion des sessions de charge EVSE - CRUD et actions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class SessionController {

    private final SessionService sessionService;
    private final OCPPService ocppService;

    // =========================================================================
    // CRUD Operations
    // =========================================================================

    @GetMapping
    @Operation(summary = "Liste des sessions")
    public ResponseEntity<List<Map<String, Object>>> getAllSessions() {
        List<Map<String, Object>> summaries = sessionService.getAllSessions().stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Details d'une session")
    public ResponseEntity<Map<String, Object>> getSession(
            @Parameter(description = "ID session", example = "sess-123")
            @PathVariable String id) {
        Session s = sessionService.getSession(id);
        return ResponseEntity.ok(toDetail(s));
    }

    private Map<String, Object> toSummary(Session s) {
        return Map.of(
                "id", s.getId(),
                "cpId", s.getCpId(),
                "status", s.getStatus(),
                "soc", (int) s.getSoc(),
                "powerKw", s.getCurrentPowerKw(),
                "connected", s.isConnected(),
                "charging", s.isCharging()
        );
    }

    private Map<String, Object> toDetail(Session s) {
        return Map.ofEntries(
                Map.entry("id", s.getId()),
                Map.entry("cpId", s.getCpId()),
                Map.entry("url", s.getUrl() != null ? s.getUrl() : ""),
                Map.entry("status", s.getStatus()),
                Map.entry("soc", (int) s.getSoc()),
                Map.entry("targetSoc", (int) s.getTargetSoc()),
                Map.entry("powerKw", s.getCurrentPowerKw()),
                Map.entry("maxPowerKw", s.getMaxPowerKw()),
                Map.entry("energyKwh", s.getEnergyDeliveredKwh()),
                Map.entry("connected", s.isConnected()),
                Map.entry("charging", s.isCharging()),
                Map.entry("connectorId", s.getConnectorId()),
                Map.entry("idTag", s.getIdTag() != null ? s.getIdTag() : ""),
                Map.entry("transactionId", s.getTransactionId() != null ? s.getTransactionId() : "")
        );
    }

    @PostMapping
    @Operation(summary = "Creer une session")
    public ResponseEntity<Map<String, Object>> createSession(
            @Valid @RequestBody CreateSessionRequest request) {
        // Convertir DTO en Session
        Session session = Session.builder()
                .url(request.csmsUrl())
                .cpId(request.cpId())
                .title(request.cpId())
                .connectorId(request.connectorId())
                .vehicleProfile(request.vehicleId())
                .chargerType(request.chargerType() != null ? ChargerType.valueOf(request.chargerType()) : ChargerType.AC_TRI)
                .idTag(request.idTag())
                .soc(request.initialSoc())
                .targetSoc(request.targetSoc())
                .bearerToken(request.bearerToken())
                .build();

        Session created = sessionService.createSession(session);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", created.getId(),
                "cpId", created.getCpId(),
                "status", created.getStatus(),
                "message", "Session creee"
        ));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre a jour une session")
    public ResponseEntity<Map<String, Object>> updateSession(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {
        Session session = sessionService.getSession(id);
        // Appliquer les updates
        if (updates.containsKey("idTag")) session.setIdTag((String) updates.get("idTag"));
        if (updates.containsKey("targetSoc")) session.setTargetSoc(((Number) updates.get("targetSoc")).doubleValue());
        if (updates.containsKey("maxPowerKw")) session.setMaxPowerKw(((Number) updates.get("maxPowerKw")).doubleValue());
        Session updated = sessionService.updateSession(id, session);
        return ResponseEntity.ok(Map.of("id", id, "status", "updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une session")
    public ResponseEntity<Void> deleteSession(@PathVariable String id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Connection Operations
    // =========================================================================

    @PostMapping("/{id}/connect")
    @Operation(summary = "Connecter au CSMS")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> connect(@PathVariable String id) {
        return ocppService.connect(id)
                .thenApply(connected -> {
                    if (connected) {
                        return ResponseEntity.ok(Map.of(
                                "status", "connected",
                                "sessionId", id
                        ));
                    } else {
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(Map.of(
                                        "status", "failed",
                                        "sessionId", id,
                                        "message", "Connection failed"
                                ));
                    }
                });
    }

    @PostMapping("/{id}/disconnect")
    @Operation(summary = "Deconnecter du CSMS")
    public ResponseEntity<Map<String, Object>> disconnect(@PathVariable String id) {
        ocppService.disconnect(id);
        return ResponseEntity.ok(Map.of(
                "status", "disconnected",
                "sessionId", id
        ));
    }

    // =========================================================================
    // OCPP Operations
    // =========================================================================

    @PostMapping("/{id}/boot")
    @Operation(summary = "BootNotification")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendBootNotification(@PathVariable String id) {
        return ocppService.sendBootNotification(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/authorize")
    @Operation(summary = "Authorize")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendAuthorize(@PathVariable String id) {
        return ocppService.sendAuthorize(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "StartTransaction")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendStartTransaction(@PathVariable String id) {
        return ocppService.sendStartTransaction(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "StopTransaction")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendStopTransaction(@PathVariable String id) {
        return ocppService.sendStopTransaction(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "StatusNotification")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendStatusNotification(
            @PathVariable String id,
            @Parameter(schema = @Schema(allowableValues = {"Available", "Preparing", "Charging", "Finishing", "Faulted"}))
            @RequestParam(defaultValue = "Available") String status) {
        ConnectorStatus connectorStatus = ConnectorStatus.fromValue(status);
        return ocppService.sendStatusNotification(id, connectorStatus)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/metervalues")
    @Operation(summary = "MeterValues")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendMeterValues(@PathVariable String id) {
        return ocppService.sendMeterValues(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/heartbeat")
    @Operation(summary = "Heartbeat")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendHeartbeat(@PathVariable String id) {
        return ocppService.sendHeartbeat(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    // =========================================================================
    // State Management
    // =========================================================================

    @PatchMapping("/{id}/state")
    @Operation(summary = "Modifier l'etat")
    public ResponseEntity<Map<String, Object>> updateState(
            @PathVariable String id,
            @Parameter(schema = @Schema(allowableValues = {"Available", "Preparing", "Charging", "Finishing", "Faulted"}))
            @RequestParam String state) {
        SessionState sessionState = SessionState.fromValue(state);
        Session s = sessionService.updateState(id, sessionState);
        return ResponseEntity.ok(Map.of("id", id, "state", s.getStatus()));
    }

    // =========================================================================
    // Queries
    // =========================================================================

    @GetMapping("/connected")
    @Operation(summary = "Sessions connectees")
    public ResponseEntity<List<Map<String, Object>>> getConnectedSessions() {
        return ResponseEntity.ok(sessionService.getConnectedSessions().stream().map(this::toSummary).toList());
    }

    @GetMapping("/charging")
    @Operation(summary = "Sessions en charge")
    public ResponseEntity<List<Map<String, Object>>> getChargingSessions() {
        return ResponseEntity.ok(sessionService.getChargingSessions().stream().map(this::toSummary).toList());
    }

    @GetMapping("/count")
    @Operation(summary = "Comptage sessions")
    public ResponseEntity<Map<String, Object>> countSessions() {
        return ResponseEntity.ok(Map.of(
                "total", sessionService.countSessions(),
                "byState", sessionService.countSessionsByState()
        ));
    }

    // =========================================================================
    // Session Persistence & Keepalive
    // =========================================================================

    @PostMapping("/{id}/keepalive")
    @Operation(summary = "Keepalive HTTP")
    public ResponseEntity<Map<String, Object>> keepalive(@PathVariable String id) {
        try {
            Session session = sessionService.keepalive(id);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "sessionId", id,
                    "connected", session.isConnected(),
                    "state", session.getState().getValue()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/voluntary-stop")
    @Operation(summary = "Arret volontaire")
    public ResponseEntity<Map<String, Object>> voluntaryStop(
            @PathVariable String id,
            @RequestParam(defaultValue = "User request") String reason) {
        try {
            Session session = sessionService.setVoluntaryStop(id, reason);
            ocppService.disconnect(id);
            return ResponseEntity.ok(Map.of(
                    "status", "stopped",
                    "sessionId", id,
                    "reason", reason,
                    "voluntaryStop", session.isVoluntaryStop()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/clear-voluntary-stop")
    @Operation(summary = "Annuler arret volontaire")
    public ResponseEntity<Map<String, Object>> clearVoluntaryStop(@PathVariable String id) {
        try {
            Session session = sessionService.clearVoluntaryStop(id);
            return ResponseEntity.ok(Map.of(
                    "status", "cleared",
                    "sessionId", id,
                    "canReconnect", session.canReconnect()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/backgrounded")
    @Operation(summary = "Mode arriere-plan")
    public ResponseEntity<Map<String, Object>> setBackgrounded(
            @PathVariable String id,
            @RequestParam boolean backgrounded) {
        try {
            Session session = sessionService.setBackgrounded(id, backgrounded);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "sessionId", id,
                    "backgrounded", session.isBackgrounded()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/reconnectable")
    @Operation(summary = "Sessions reconnectables")
    public ResponseEntity<List<Map<String, Object>>> getReconnectableSessions() {
        return ResponseEntity.ok(sessionService.getReconnectableSessions().stream().map(this::toSummary).toList());
    }

    @GetMapping("/stale")
    @Operation(summary = "Sessions abandonnees")
    public ResponseEntity<List<Map<String, Object>>> getStaleSessions() {
        return ResponseEntity.ok(sessionService.getStaleSessions().stream().map(this::toSummary).toList());
    }
}
