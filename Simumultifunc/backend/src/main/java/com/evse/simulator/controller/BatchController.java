package com.evse.simulator.controller;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.Session;
import com.evse.simulator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Contrôleur REST pour les opérations en lot.
 */
@RestController
@RequestMapping("/api/batch")
@Tag(name = "Batch", description = "Opérations en lot sur les sessions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class BatchController {

    private final SessionService sessionService;
    private final OCPPService ocppService;

    @PostMapping("/create-sessions")
    @Operation(summary = "Crée N sessions en lot")
    public ResponseEntity<Map<String, Object>> createSessions(
            @RequestParam(defaultValue = "10") int count,
            @RequestBody Session template) {

        List<Session> created = sessionService.createBatchSessions(count, template);

        return ResponseEntity.ok(Map.of(
                "created", created.size(),
                "sessionIds", created.stream().map(Session::getId).toList()
        ));
    }

    @PostMapping("/connect-all")
    @Operation(summary = "Connecte toutes les sessions déconnectées")
    public ResponseEntity<Map<String, Object>> connectAll() {
        List<Session> disconnected = sessionService.getAllSessions().stream()
                .filter(s -> !s.isConnected())
                .toList();

        List<String> connecting = new ArrayList<>();
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (Session session : disconnected) {
            connecting.add(session.getId());
            futures.add(ocppService.connect(session.getId()));
        }

        // Attendre toutes les connexions
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, ex) -> {
                    long connected = futures.stream()
                            .filter(f -> {
                                try {
                                    return f.get();
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .count();
                    log.info("Batch connect: {}/{} succeeded", connected, futures.size());
                });

        return ResponseEntity.ok(Map.of(
                "connecting", connecting.size(),
                "sessionIds", connecting
        ));
    }

    @PostMapping("/disconnect-all")
    @Operation(summary = "Déconnecte toutes les sessions")
    public ResponseEntity<Map<String, Object>> disconnectAll() {
        List<Session> connected = sessionService.getConnectedSessions();
        List<String> disconnected = new ArrayList<>();

        for (Session session : connected) {
            ocppService.disconnect(session.getId());
            disconnected.add(session.getId());
        }

        return ResponseEntity.ok(Map.of(
                "disconnected", disconnected.size(),
                "sessionIds", disconnected
        ));
    }

    @PostMapping("/boot-all")
    @Operation(summary = "Envoie BootNotification à toutes les sessions connectées")
    public ResponseEntity<Map<String, Object>> bootAll() {
        List<Session> connected = sessionService.getConnectedSessions();
        List<String> booting = new ArrayList<>();

        for (Session session : connected) {
            if (!session.getState().name().equals("AVAILABLE")) {
                ocppService.sendBootNotification(session.getId());
                booting.add(session.getId());
            }
        }

        return ResponseEntity.ok(Map.of(
                "booting", booting.size(),
                "sessionIds", booting
        ));
    }

    @PostMapping("/start-all")
    @Operation(summary = "Démarre la charge sur toutes les sessions disponibles")
    public ResponseEntity<Map<String, Object>> startAll() {
        List<Session> available = sessionService.getAllSessions().stream()
                .filter(Session::canStartCharging)
                .toList();

        List<String> starting = new ArrayList<>();

        for (Session session : available) {
            ocppService.sendAuthorize(session.getId())
                    .thenCompose(r -> ocppService.sendStartTransaction(session.getId()));
            starting.add(session.getId());
        }

        return ResponseEntity.ok(Map.of(
                "starting", starting.size(),
                "sessionIds", starting
        ));
    }

    @PostMapping("/stop-all")
    @Operation(summary = "Arrête la charge sur toutes les sessions en charge")
    public ResponseEntity<Map<String, Object>> stopAll() {
        List<Session> charging = sessionService.getChargingSessions();
        List<String> stopping = new ArrayList<>();

        for (Session session : charging) {
            ocppService.sendStopTransaction(session.getId());
            stopping.add(session.getId());
        }

        return ResponseEntity.ok(Map.of(
                "stopping", stopping.size(),
                "sessionIds", stopping
        ));
    }

    @DeleteMapping("/delete-disconnected")
    @Operation(summary = "Supprime toutes les sessions déconnectées")
    public ResponseEntity<Map<String, Object>> deleteDisconnected() {
        int deleted = sessionService.deleteDisconnectedSessions();

        return ResponseEntity.ok(Map.of(
                "deleted", deleted
        ));
    }

    @PostMapping("/run-scenario")
    @Operation(summary = "Exécute un scénario sur toutes les sessions")
    public ResponseEntity<Map<String, Object>> runScenario(
            @RequestParam String scenario,
            @RequestParam(defaultValue = "false") boolean onlyConnected) {

        List<Session> sessions = onlyConnected ?
                sessionService.getConnectedSessions() :
                sessionService.getAllSessions();

        List<String> executed = new ArrayList<>();

        for (Session session : sessions) {
            executeScenario(session.getId(), scenario);
            executed.add(session.getId());
        }

        return ResponseEntity.ok(Map.of(
                "scenario", scenario,
                "executed", executed.size(),
                "sessionIds", executed
        ));
    }

    private void executeScenario(String sessionId, String scenario) {
        switch (scenario.toLowerCase()) {
            case "full-charge" -> {
                ocppService.connect(sessionId)
                        .thenCompose(c -> ocppService.sendBootNotification(sessionId))
                        .thenCompose(b -> ocppService.sendAuthorize(sessionId))
                        .thenCompose(a -> ocppService.sendStartTransaction(sessionId));
            }
            case "quick-connect" -> {
                ocppService.connect(sessionId)
                        .thenCompose(c -> ocppService.sendBootNotification(sessionId));
            }
            case "heartbeat-test" -> {
                if (sessionService.getSession(sessionId).isConnected()) {
                    for (int i = 0; i < 5; i++) {
                        ocppService.sendHeartbeat(sessionId);
                    }
                }
            }
            default -> log.warn("Unknown scenario: {}", scenario);
        }
    }
}