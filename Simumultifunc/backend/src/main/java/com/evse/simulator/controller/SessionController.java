package com.evse.simulator.controller;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Contrôleur REST pour la gestion des sessions de charge.
 */
@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Sessions", description = "Gestion des sessions de charge EVSE")
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
    @Operation(summary = "Liste toutes les sessions")
    public ResponseEntity<List<Session>> getAllSessions() {
        return ResponseEntity.ok(sessionService.getAllSessions());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupère une session par ID")
    public ResponseEntity<Session> getSession(
            @Parameter(description = "ID de la session") @PathVariable String id) {
        return ResponseEntity.ok(sessionService.getSession(id));
    }

    @PostMapping
    @Operation(summary = "Crée une nouvelle session")
    public ResponseEntity<Session> createSession(@Valid @RequestBody Session session) {
        Session created = sessionService.createSession(session);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Met à jour une session")
    public ResponseEntity<Session> updateSession(
            @PathVariable String id,
            @RequestBody Session updates) {
        return ResponseEntity.ok(sessionService.updateSession(id, updates));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprime une session")
    public ResponseEntity<Void> deleteSession(@PathVariable String id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Connection Operations
    // =========================================================================

    @PostMapping("/{id}/connect")
    @Operation(summary = "Connecte une session au CSMS")
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
    @Operation(summary = "Déconnecte une session du CSMS")
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
    @Operation(summary = "Envoie BootNotification")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendBootNotification(
            @PathVariable String id) {
        return ocppService.sendBootNotification(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/authorize")
    @Operation(summary = "Envoie Authorize")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendAuthorize(
            @PathVariable String id) {
        return ocppService.sendAuthorize(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Envoie StartTransaction")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendStartTransaction(
            @PathVariable String id) {
        return ocppService.sendStartTransaction(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Envoie StopTransaction")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendStopTransaction(
            @PathVariable String id) {
        return ocppService.sendStopTransaction(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Envoie StatusNotification")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendStatusNotification(
            @PathVariable String id,
            @RequestParam(defaultValue = "Available") String status) {
        ConnectorStatus connectorStatus = ConnectorStatus.fromValue(status);
        return ocppService.sendStatusNotification(id, connectorStatus)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/metervalues")
    @Operation(summary = "Envoie MeterValues")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendMeterValues(
            @PathVariable String id) {
        return ocppService.sendMeterValues(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    @PostMapping("/{id}/heartbeat")
    @Operation(summary = "Envoie Heartbeat")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendHeartbeat(
            @PathVariable String id) {
        return ocppService.sendHeartbeat(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage())));
    }

    // =========================================================================
    // State Management
    // =========================================================================

    @PatchMapping("/{id}/state")
    @Operation(summary = "Met à jour l'état d'une session")
    public ResponseEntity<Session> updateState(
            @PathVariable String id,
            @RequestParam String state) {
        SessionState sessionState = SessionState.fromValue(state);
        return ResponseEntity.ok(sessionService.updateState(id, sessionState));
    }

    // =========================================================================
    // Queries
    // =========================================================================

    @GetMapping("/connected")
    @Operation(summary = "Liste les sessions connectées")
    public ResponseEntity<List<Session>> getConnectedSessions() {
        return ResponseEntity.ok(sessionService.getConnectedSessions());
    }

    @GetMapping("/charging")
    @Operation(summary = "Liste les sessions en charge")
    public ResponseEntity<List<Session>> getChargingSessions() {
        return ResponseEntity.ok(sessionService.getChargingSessions());
    }

    @GetMapping("/count")
    @Operation(summary = "Compte les sessions")
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
    @Operation(summary = "Met à jour le keepalive d'une session (heartbeat HTTP)")
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
    @Operation(summary = "Marque une session pour arrêt volontaire")
    public ResponseEntity<Map<String, Object>> voluntaryStop(
            @PathVariable String id,
            @RequestParam(defaultValue = "User requested disconnect") String reason) {
        try {
            Session session = sessionService.setVoluntaryStop(id, reason);
            // Déconnecte effectivement la session après le marquage
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
    @Operation(summary = "Annule le flag d'arrêt volontaire pour permettre la reconnexion")
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
    @Operation(summary = "Marque une session comme en arrière-plan ou visible")
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
    @Operation(summary = "Liste les sessions qui peuvent être reconnectées")
    public ResponseEntity<List<Session>> getReconnectableSessions() {
        return ResponseEntity.ok(sessionService.getReconnectableSessions());
    }

    @GetMapping("/stale")
    @Operation(summary = "Liste les sessions abandonnées (pas de keepalive)")
    public ResponseEntity<List<Session>> getStaleSessions() {
        return ResponseEntity.ok(sessionService.getStaleSessions());
    }
}