package com.evse.simulator.controller;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Contrôleur de compatibilité pour les endpoints /api/simu/* attendus par le frontend.
 * Mappe les anciennes routes vers les nouvelles implémentations.
 */
@RestController
@RequestMapping("/api/simu")
@Tag(name = "Simu Compat", description = "API de compatibilité pour le frontend (anciens endpoints /api/simu)")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class SimuCompatController {

    private final SessionService sessionService;
    private final OCPPService ocppService;

    // Scheduler pour les meter values périodiques
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final ConcurrentHashMap<String, ScheduledFuture<?>> meterValuesTasks = new ConcurrentHashMap<>();

    // =========================================================================
    // List Sessions
    // =========================================================================

    @GetMapping
    @Operation(summary = "Liste toutes les sessions (compat /api/simu)")
    public ResponseEntity<?> listSessions(
            @RequestParam(required = false) Boolean paged,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset,
            @RequestParam(required = false) Boolean includeClosed) {

        List<Session> sessions = sessionService.getAllSessions();

        // Si paged=true, retourner format paginé
        if (Boolean.TRUE.equals(paged)) {
            int total = sessions.size();
            int start = Math.min(offset, total);
            int end = Math.min(start + limit, total);
            List<Session> page = sessions.subList(start, end);

            return ResponseEntity.ok(Map.of(
                "sessions", page,
                "total", total,
                "hasMore", end < total,
                "nextOffset", end
            ));
        }

        // Sinon retourner tableau simple
        return ResponseEntity.ok(sessions);
    }

    // =========================================================================
    // Create Session
    // =========================================================================

    @PostMapping("/session")
    @Operation(summary = "Crée une nouvelle session (compat /api/simu/session)")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
        try {
            Session session = new Session();
            session.setUrl((String) body.get("url"));
            session.setCpId((String) body.get("cpId"));
            session.setIdTag((String) body.getOrDefault("idTag", "TEST-TAG"));

            // Configuration EVSE si fournie
            String evseType = (String) body.get("evseType");
            Number maxA = (Number) body.get("maxA");
            if (maxA != null) {
                session.setMaxCurrentA(maxA.doubleValue());
            }

            // Token d'authentification si fourni
            String bearerToken = (String) body.get("bearerToken");
            if (bearerToken != null) {
                session.setBearerToken(bearerToken);
            }

            Session created = sessionService.createSession(session);

            log.info("Session created: {} with cpId={}, url={}", created.getId(), created.getCpId(), created.getUrl());

            boolean auto = Boolean.TRUE.equals(body.get("auto"));

            // Toujours connecter au CSMS et envoyer BootNotification
            // Cela simule le comportement de l'ancien backend Node.js
            final String sessionId = created.getId();
            ocppService.connect(sessionId)
                .thenAccept(connected -> {
                    if (connected) {
                        log.info("Session {} connected to CSMS, sending BootNotification", sessionId);
                        ocppService.sendBootNotification(sessionId)
                            .thenAccept(result -> {
                                log.info("BootNotification response for session {}: {}", sessionId, result);
                            })
                            .exceptionally(ex -> {
                                log.error("BootNotification failed for session {}: {}", sessionId, ex.getMessage());
                                return null;
                            });
                    } else {
                        log.warn("Session {} failed to connect to CSMS", sessionId);
                    }
                })
                .exceptionally(ex -> {
                    log.error("Connection failed for session {}: {}", sessionId, ex.getMessage());
                    return null;
                });

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "ok", true,
                "id", created.getId(),
                "auto", auto
            ));
        } catch (Exception e) {
            log.error("Error creating session", e);
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", e.getMessage()
            ));
        }
    }

    // =========================================================================
    // Delete Session
    // =========================================================================

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprime une session (compat /api/simu/{id})")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String id) {
        try {
            // Arrêter les meter values si actifs
            stopMeterValuesTask(id);

            // Déconnecter si connecté
            ocppService.disconnect(id);

            // Supprimer la session
            sessionService.deleteSession(id);

            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // Connect / Disconnect
    // =========================================================================

    @PostMapping("/{id}/connect")
    @Operation(summary = "Connecte une session au CSMS (compat /api/simu/{id}/connect)")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> connect(@PathVariable String id) {
        return ocppService.connect(id)
            .<ResponseEntity<Map<String, Object>>>thenApply(connected -> {
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("ok", connected);
                result.put("status", connected ? "connected" : "failed");
                result.put("sessionId", id);
                if (!connected) {
                    result.put("error", "Connection failed");
                }
                return ResponseEntity.ok(result);
            })
            .exceptionally(ex -> {
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("ok", false);
                result.put("error", ex.getMessage());
                return ResponseEntity.ok(result);
            });
    }

    @PostMapping("/{id}/disconnect")
    @Operation(summary = "Déconnecte une session du CSMS (compat /api/simu/{id}/disconnect)")
    public ResponseEntity<Map<String, Object>> disconnect(@PathVariable String id) {
        try {
            ocppService.disconnect(id);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "status", "disconnected",
                "sessionId", id
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "error", e.getMessage()
            ));
        }
    }

    // =========================================================================
    // Park, Plug, Unplug, Leave (Physical actions)
    // =========================================================================

    @PostMapping("/{id}/park")
    @Operation(summary = "Véhicule garé (compat)")
    public ResponseEntity<Map<String, Object>> park(@PathVariable String id) {
        log.info("Vehicle parked for session {}", id);
        Session session = sessionService.getSession(id);
        // État: PARKED (véhicule configuré)
        session.setState(com.evse.simulator.model.enums.SessionState.PARKED);
        session.setParked(true);
        sessionService.updateSession(id, session);
        return ResponseEntity.ok(Map.of("ok", true, "status", "parked"));
    }

    @PostMapping("/{id}/status/park")
    @Operation(summary = "Véhicule garé - alias (compat)")
    public ResponseEntity<Map<String, Object>> statusPark(@PathVariable String id) {
        return park(id);
    }

    @PostMapping("/{id}/plug")
    @Operation(summary = "Câble branché (compat)")
    public ResponseEntity<Map<String, Object>> plug(@PathVariable String id) {
        log.info("Cable plugged for session {}", id);
        Session session = sessionService.getSession(id);
        // État: PLUGGED (câble branché, prêt pour authorize)
        session.setState(com.evse.simulator.model.enums.SessionState.PLUGGED);
        session.setPlugged(true);
        sessionService.updateSession(id, session);
        // Envoyer StatusNotification: Preparing
        ocppService.sendStatusNotification(id, ConnectorStatus.PREPARING);
        return ResponseEntity.ok(Map.of("ok", true, "status", "plugged"));
    }

    @PostMapping("/{id}/status/plug")
    @Operation(summary = "Câble branché - alias (compat)")
    public ResponseEntity<Map<String, Object>> statusPlug(@PathVariable String id) {
        return plug(id);
    }

    @PostMapping("/{id}/unplug")
    @Operation(summary = "Câble débranché (compat)")
    public ResponseEntity<Map<String, Object>> unplug(@PathVariable String id) {
        log.info("Cable unplugged for session {}", id);
        Session session = sessionService.getSession(id);
        // Retour à PARKED (véhicule toujours garé mais débranché)
        session.setState(com.evse.simulator.model.enums.SessionState.PARKED);
        session.setPlugged(false);
        session.setAuthorized(false);
        sessionService.updateSession(id, session);
        // Envoyer StatusNotification: Available
        ocppService.sendStatusNotification(id, ConnectorStatus.AVAILABLE);
        return ResponseEntity.ok(Map.of("ok", true, "status", "unplugged"));
    }

    @PostMapping("/{id}/status/unplug")
    @Operation(summary = "Câble débranché - alias (compat)")
    public ResponseEntity<Map<String, Object>> statusUnplug(@PathVariable String id) {
        return unplug(id);
    }

    @PostMapping("/{id}/leave")
    @Operation(summary = "Véhicule parti (compat)")
    public ResponseEntity<Map<String, Object>> leave(@PathVariable String id) {
        log.info("Vehicle left for session {}", id);
        Session session = sessionService.getSession(id);
        // Retour à BOOT_ACCEPTED (prêt pour nouvelle session)
        session.setState(com.evse.simulator.model.enums.SessionState.BOOT_ACCEPTED);
        session.setParked(false);
        session.setPlugged(false);
        session.setAuthorized(false);
        sessionService.updateSession(id, session);
        return ResponseEntity.ok(Map.of("ok", true, "status", "left"));
    }

    @PostMapping("/{id}/status/unpark")
    @Operation(summary = "Véhicule parti - alias (compat)")
    public ResponseEntity<Map<String, Object>> statusUnpark(@PathVariable String id) {
        return leave(id);
    }

    // =========================================================================
    // Authorize
    // =========================================================================

    @PostMapping("/{id}/authorize")
    @Operation(summary = "Envoie Authorize (compat)")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> authorize(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        String idTag = body != null ? (String) body.get("idTag") : null;
        if (idTag != null) {
            Session session = sessionService.getSession(id);
            session.setIdTag(idTag);
            sessionService.updateSession(id, session);
        }

        return ocppService.sendAuthorize(id)
            .thenApply(result -> ResponseEntity.ok(Map.of("ok", true, "result", result)))
            .exceptionally(ex -> ResponseEntity.ok(Map.of("ok", false, "error", ex.getMessage())));
    }

    // =========================================================================
    // Boot, Status, Heartbeat
    // =========================================================================

    @PostMapping("/{id}/boot")
    @Operation(summary = "Envoie BootNotification (compat /api/simu/{id}/boot)")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendBootNotification(
            @PathVariable String id) {
        return ocppService.sendBootNotification(id)
            .thenApply(result -> ResponseEntity.ok(Map.of("ok", true, "result", result)))
            .exceptionally(ex -> ResponseEntity.ok(Map.of("ok", false, "error", ex.getMessage())));
    }

    @PostMapping("/{id}/heartbeat")
    @Operation(summary = "Envoie Heartbeat (compat /api/simu/{id}/heartbeat)")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendHeartbeat(
            @PathVariable String id) {
        return ocppService.sendHeartbeat(id)
            .thenApply(result -> ResponseEntity.ok(Map.of("ok", true, "result", result)))
            .exceptionally(ex -> ResponseEntity.ok(Map.of("ok", false, "error", ex.getMessage())));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Envoie StatusNotification (compat /api/simu/{id}/status)")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendStatusNotification(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        String statusStr = body != null ? (String) body.getOrDefault("status", "Available") : "Available";
        ConnectorStatus status = ConnectorStatus.fromValue(statusStr);
        return ocppService.sendStatusNotification(id, status)
            .thenApply(result -> ResponseEntity.ok(Map.of("ok", true, "result", result)))
            .exceptionally(ex -> ResponseEntity.ok(Map.of("ok", false, "error", ex.getMessage())));
    }

    // =========================================================================
    // Start/Stop Transaction
    // =========================================================================

    @PostMapping("/{id}/startTx")
    @Operation(summary = "Démarre une transaction (compat /api/simu/{id}/startTx)")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> startTransaction(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        return ocppService.sendStartTransaction(id)
            .thenApply(result -> ResponseEntity.ok(Map.of("ok", true, "result", result)))
            .exceptionally(ex -> ResponseEntity.ok(Map.of("ok", false, "error", ex.getMessage())));
    }

    @PostMapping("/{id}/stopTx")
    @Operation(summary = "Arrête une transaction (compat /api/simu/{id}/stopTx)")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> stopTransaction(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        // Arrêter les meter values automatiques
        stopMeterValuesTask(id);

        return ocppService.sendStopTransaction(id)
            .thenApply(result -> ResponseEntity.ok(Map.of("ok", true, "result", result)))
            .exceptionally(ex -> ResponseEntity.ok(Map.of("ok", false, "error", ex.getMessage())));
    }

    // =========================================================================
    // Meter Values
    // =========================================================================

    @PostMapping("/{id}/mv/start")
    @Operation(summary = "Démarre l'envoi périodique de MeterValues")
    public ResponseEntity<Map<String, Object>> startMeterValues(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        int periodSec = ((Number) body.getOrDefault("periodSec", 10)).intValue();

        // Arrêter une tâche existante
        stopMeterValuesTask(id);

        // Créer nouvelle tâche
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                ocppService.sendMeterValues(id).join();
            } catch (Exception e) {
                log.warn("MeterValues error for session {}: {}", id, e.getMessage());
            }
        }, 0, periodSec, TimeUnit.SECONDS);

        meterValuesTasks.put(id, task);

        log.info("Started MeterValues for session {} every {} seconds", id, periodSec);

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "periodSec", periodSec
        ));
    }

    @PostMapping("/{id}/mv/stop")
    @Operation(summary = "Arrête l'envoi périodique de MeterValues")
    public ResponseEntity<Map<String, Object>> stopMeterValues(@PathVariable String id) {
        stopMeterValuesTask(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{id}/mv/restart")
    @Operation(summary = "Redémarre l'envoi périodique de MeterValues avec nouvelle config")
    public ResponseEntity<Map<String, Object>> restartMeterValues(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        int periodSec = body != null ? ((Number) body.getOrDefault("periodSec", 10)).intValue() : 10;

        // Arrêter une tâche existante
        stopMeterValuesTask(id);

        // Créer nouvelle tâche
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                ocppService.sendMeterValues(id).join();
            } catch (Exception e) {
                log.warn("MeterValues error for session {}: {}", id, e.getMessage());
            }
        }, 0, periodSec, TimeUnit.SECONDS);

        meterValuesTasks.put(id, task);

        log.info("Restarted MeterValues for session {} every {} seconds", id, periodSec);

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "periodSec", periodSec
        ));
    }

    // =========================================================================
    // MV Mask (Meter Values Configuration)
    // =========================================================================

    @PostMapping("/{id}/mv-mask")
    @Operation(summary = "Configure le masque MeterValues (compat)")
    public ResponseEntity<Map<String, Object>> setMvMask(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        // Stocker le masque MV dans la session
        log.info("Setting MV mask for session {}: {}", id, body);
        return ResponseEntity.ok(Map.of("ok", true, "mask", body != null ? body : Map.of()));
    }

    @PostMapping("/{id}/status/mv-mask")
    @Operation(summary = "Configure le masque MeterValues via status (compat)")
    public ResponseEntity<Map<String, Object>> setStatusMvMask(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        // Stocker le masque MV dans la session
        log.info("Setting status MV mask for session {}: {}", id, body);
        return ResponseEntity.ok(Map.of("ok", true, "mask", body != null ? body : Map.of()));
    }

    private void stopMeterValuesTask(String id) {
        ScheduledFuture<?> task = meterValuesTasks.remove(id);
        if (task != null) {
            task.cancel(false);
            log.info("Stopped MeterValues for session {}", id);
        }
    }

    // =========================================================================
    // Logs
    // =========================================================================

    @GetMapping("/{id}/logs")
    @Operation(summary = "Récupère les logs d'une session")
    public ResponseEntity<List<Map<String, Object>>> getLogs(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category) {
        try {
            Session session = sessionService.getSession(id);
            List<com.evse.simulator.model.LogEntry> logs = session.getLogs();

            if (logs == null || logs.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            // Filtrer et convertir les logs
            java.util.stream.Stream<com.evse.simulator.model.LogEntry> stream = logs.stream();

            // Filtrer par niveau si spécifié
            if (level != null && !level.isEmpty()) {
                String upperLevel = level.toUpperCase();
                stream = stream.filter(log -> log.getLevel() != null &&
                        log.getLevel().name().equalsIgnoreCase(upperLevel));
            }

            // Filtrer par catégorie si spécifiée
            if (category != null && !category.isEmpty()) {
                stream = stream.filter(log -> log.getCategory() != null &&
                        log.getCategory().equalsIgnoreCase(category));
            }

            // Limiter et convertir en Map
            List<Map<String, Object>> result = stream
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp())) // Plus récent d'abord
                    .limit(limit)
                    .map(log -> Map.<String, Object>of(
                            "timestamp", log.getTimestamp().toString(),
                            "level", log.getLevel() != null ? log.getLevel().name() : "INFO",
                            "category", log.getCategory() != null ? log.getCategory() : "GENERAL",
                            "message", log.getMessage() != null ? log.getMessage() : ""
                    ))
                    .toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting logs for session {}: {}", id, e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/{id}/logs")
    @Operation(summary = "Ajoute un log à une session")
    public ResponseEntity<Map<String, Object>> addLog(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            String level = (String) body.getOrDefault("level", "INFO");
            String category = (String) body.getOrDefault("category", "USER");
            String message = (String) body.get("message");

            if (message == null || message.isEmpty()) {
                return ResponseEntity.ok(Map.of("ok", false, "error", "Message is required"));
            }

            com.evse.simulator.model.LogEntry logEntry = com.evse.simulator.model.LogEntry.builder()
                    .level(com.evse.simulator.model.LogEntry.Level.valueOf(level.toUpperCase()))
                    .category(category)
                    .message(message)
                    .build();

            sessionService.addLog(id, logEntry);

            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("Error adding log for session {}: {}", id, e.getMessage());
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/logs")
    @Operation(summary = "Efface les logs d'une session")
    public ResponseEntity<Map<String, Object>> clearLogs(@PathVariable String id) {
        try {
            Session session = sessionService.getSession(id);
            session.getLogs().clear();
            sessionService.updateSession(id, session);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // Custom OCPP Message
    // =========================================================================

    @PostMapping("/{id}/ocpp")
    @Operation(summary = "Envoie un message OCPP personnalisé")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendOcppMessage(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        String action = (String) body.get("action");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) body.getOrDefault("payload", Map.of());

        return ocppService.sendMessage(id, action, payload)
            .thenApply(result -> ResponseEntity.ok(Map.of("ok", true, "result", result)))
            .exceptionally(ex -> ResponseEntity.ok(Map.of("ok", false, "error", ex.getMessage())));
    }

    // =========================================================================
    // Phasing Control (Three-Phase Power Management)
    // =========================================================================

    @PostMapping("/{id}/phasing")
    @Operation(summary = "Configure le phasage pour la régulation triphasée")
    public ResponseEntity<Map<String, Object>> configurePhasing(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            Session session = sessionService.getSession(id);

            int evsePhases = ((Number) body.getOrDefault("evsePhases", 3)).intValue();
            int vehicleActivePhases = ((Number) body.getOrDefault("vehicleActivePhases", 3)).intValue();
            double powerPerPhase = ((Number) body.getOrDefault("powerPerPhase", 16.0)).doubleValue();

            // Validation
            if (evsePhases < 1 || evsePhases > 3) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "error", "evsePhases must be between 1 and 3"
                ));
            }
            if (vehicleActivePhases < 1 || vehicleActivePhases > evsePhases) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "error", "vehicleActivePhases must be between 1 and evsePhases"
                ));
            }
            if (powerPerPhase < 6 || powerPerPhase > 63) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "error", "powerPerPhase must be between 6A and 63A"
                ));
            }

            // Calcul de la puissance totale (P = U * I * sqrt(3) pour triphasé)
            double voltage = 230.0;
            double totalPowerKw;
            if (vehicleActivePhases == 1) {
                totalPowerKw = voltage * powerPerPhase / 1000.0;
            } else {
                totalPowerKw = Math.sqrt(3) * voltage * powerPerPhase * vehicleActivePhases / 3.0 / 1000.0;
            }

            // Calcul du déséquilibre de phase (pour les tests de régulation)
            double phaseImbalance = 0.0;
            if (evsePhases == 3 && vehicleActivePhases < 3) {
                phaseImbalance = (double)(evsePhases - vehicleActivePhases) / evsePhases * 100.0;
            }

            // Mise à jour de la session
            session.setMaxCurrentA(powerPerPhase);
            session.setMaxPowerKw(totalPowerKw);

            // Log de la configuration
            session.addLog(com.evse.simulator.model.LogEntry.info("PHASING",
                String.format("Phasing configured: EVSE=%dph, Vehicle=%dph, Current=%.1fA, Power=%.2fkW, Imbalance=%.1f%%",
                    evsePhases, vehicleActivePhases, powerPerPhase, totalPowerKw, phaseImbalance)));

            sessionService.updateSession(id, session);

            // Simuler l'impact sur les MeterValues si la session est en charge
            if (session.isCharging()) {
                session.setCurrentPowerKw(Math.min(session.getCurrentPowerKw(), totalPowerKw));
                session.setCurrentA(powerPerPhase);
                sessionService.updateSession(id, session);
            }

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "evsePhases", evsePhases,
                "vehicleActivePhases", vehicleActivePhases,
                "powerPerPhase", powerPerPhase,
                "totalPowerKw", Math.round(totalPowerKw * 100.0) / 100.0,
                "phaseImbalance", Math.round(phaseImbalance * 10.0) / 10.0,
                "currentLimitA", powerPerPhase
            ));
        } catch (Exception e) {
            log.error("Error configuring phasing for session {}: {}", id, e.getMessage());
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/phasing")
    @Operation(summary = "Récupère la configuration de phasage actuelle")
    public ResponseEntity<Map<String, Object>> getPhasing(@PathVariable String id) {
        try {
            Session session = sessionService.getSession(id);

            // Déduire la configuration de phasage depuis les données de la session
            int evsePhases = session.getChargerType() != null ?
                session.getChargerType().getPhases() : 3;
            double currentA = session.getMaxCurrentA() > 0 ? session.getMaxCurrentA() : 32.0;
            double powerKw = session.getMaxPowerKw() > 0 ? session.getMaxPowerKw() : 22.0;

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "evsePhases", evsePhases,
                "vehicleActivePhases", evsePhases, // Par défaut = max phases
                "powerPerPhase", currentA,
                "totalPowerKw", powerKw,
                "phaseImbalance", 0.0
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/phasing/simulate-imbalance")
    @Operation(summary = "Simule un déséquilibre de phase pour tester la régulation")
    public ResponseEntity<Map<String, Object>> simulatePhaseImbalance(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            Session session = sessionService.getSession(id);

            double imbalancePercent = ((Number) body.getOrDefault("imbalancePercent", 10.0)).doubleValue();
            int durationSeconds = ((Number) body.getOrDefault("durationSeconds", 30)).intValue();

            // Limiter le déséquilibre entre 0 et 100%
            imbalancePercent = Math.max(0, Math.min(100, imbalancePercent));

            // Calculer l'impact sur la puissance
            double originalPower = session.getCurrentPowerKw();
            double reducedPower = originalPower * (1 - imbalancePercent / 100.0);

            // Appliquer temporairement le déséquilibre
            session.setCurrentPowerKw(reducedPower);
            session.addLog(com.evse.simulator.model.LogEntry.warn("PHASING",
                String.format("Phase imbalance simulation: %.1f%% for %ds (Power: %.2fkW -> %.2fkW)",
                    imbalancePercent, durationSeconds, originalPower, reducedPower)));

            sessionService.updateSession(id, session);

            // Programmer la restauration de la puissance originale
            if (durationSeconds > 0) {
                scheduler.schedule(() -> {
                    try {
                        Session s = sessionService.getSession(id);
                        s.setCurrentPowerKw(originalPower);
                        s.addLog(com.evse.simulator.model.LogEntry.info("PHASING",
                            String.format("Phase imbalance simulation ended. Power restored to %.2fkW", originalPower)));
                        sessionService.updateSession(id, s);
                    } catch (Exception e) {
                        log.warn("Error restoring power after imbalance simulation: {}", e.getMessage());
                    }
                }, durationSeconds, TimeUnit.SECONDS);
            }

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "imbalancePercent", imbalancePercent,
                "originalPowerKw", originalPower,
                "reducedPowerKw", reducedPower,
                "durationSeconds", durationSeconds
            ));
        } catch (Exception e) {
            log.error("Error simulating phase imbalance: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
