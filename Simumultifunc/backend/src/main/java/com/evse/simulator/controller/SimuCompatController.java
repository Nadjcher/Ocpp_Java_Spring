package com.evse.simulator.controller;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.service.SessionService;
import com.evse.simulator.service.SessionStateManager;
import com.evse.simulator.tte.model.PricingData;
import com.evse.simulator.tte.service.CognitoTokenService;
import com.evse.simulator.tte.service.TTEApiService;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    private final TTEApiService tteApiService;
    private final CognitoTokenService cognitoTokenService;
    private final SessionStateManager stateManager;

    // Note: Le scheduler pour MeterValues est centralisé dans OCPPService pour éviter les doublons
    // Ce scheduler est uniquement pour les simulations temporaires (phase imbalance)
    private final ScheduledExecutorService delayedTaskScheduler = Executors.newScheduledThreadPool(2);

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
            Number maxPowerKw = (Number) body.get("maxPowerKw");

            // Appliquer le type de chargeur (dc, ac-mono, ac-bi, ac-tri)
            if (evseType != null) {
                ChargerType chargerType = ChargerType.fromValue(evseType);
                session.setChargerType(chargerType);
                // Initialiser activePhases selon le type de chargeur
                session.setActivePhases(chargerType.getPhases());
                log.info("Session charger type set to: {} (from evseType: {}), phases: {}",
                    chargerType, evseType, chargerType.getPhases());

                // Pour DC, utiliser la puissance max configurée ou celle du type de chargeur
                if (chargerType.isDC()) {
                    double dcPower = maxPowerKw != null ? maxPowerKw.doubleValue() : chargerType.getMaxPowerKw();
                    session.setMaxPowerKw(dcPower);
                    log.info("Session DC maxPowerKw set to: {} kW", dcPower);
                } else {
                    // Pour AC, calculer la puissance depuis le type de chargeur
                    session.setMaxPowerKw(chargerType.getMaxPowerKw());
                    log.info("Session AC maxPowerKw set to: {} kW", chargerType.getMaxPowerKw());
                }
            }

            if (maxA != null) {
                session.setMaxCurrentA(maxA.doubleValue());
            }

            // Phases véhicule pour MeterValues (si le véhicule limite les phases)
            Number vehicleAcPhases = (Number) body.get("vehicleAcPhases");
            if (vehicleAcPhases != null) {
                int evsePhases = session.getChargerType() != null ? session.getChargerType().getPhases() : 3;
                int effectivePhases = Math.min(evsePhases, vehicleAcPhases.intValue());
                session.setActivePhases(effectivePhases);
                log.info("Session activePhases set to: {} (vehicle: {}, EVSE: {})",
                    effectivePhases, vehicleAcPhases.intValue(), evsePhases);
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
            // Arrêter les meter values si actifs (via OCPPService centralisé)
            ocppService.stopMeterValuesPublic(id);

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

        // Transition validée vers PARKED
        if (!stateManager.transition(session, SessionState.PARKED)) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", "Invalid state transition to PARKED from " + session.getState(),
                "currentState", session.getState().getValue()
            ));
        }

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

        // Transition validée vers PLUGGED
        if (!stateManager.transition(session, SessionState.PLUGGED)) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", "Invalid state transition to PLUGGED from " + session.getState(),
                "currentState", session.getState().getValue()
            ));
        }

        session.setPlugged(true);
        sessionService.updateSession(id, session);

        // Envoyer StatusNotification: Preparing (géré par la transition si shouldSendStatusNotification)
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

        // Déterminer l'état cible: PARKED si véhicule garé, sinon AVAILABLE
        SessionState targetState = session.isParked() ? SessionState.PARKED : SessionState.AVAILABLE;

        // Transition validée
        if (!stateManager.transition(session, targetState)) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", "Invalid state transition to " + targetState + " from " + session.getState(),
                "currentState", session.getState().getValue()
            ));
        }

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

        // Transition validée vers AVAILABLE (prêt pour nouvelle session)
        if (!stateManager.transition(session, SessionState.AVAILABLE)) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", "Invalid state transition to AVAILABLE from " + session.getState(),
                "currentState", session.getState().getValue()
            ));
        }

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

        // Arrêter les meter values automatiques (via OCPPService centralisé)
        ocppService.stopMeterValuesPublic(id);

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

        // Déléguer à OCPPService (qui gère le scheduler unique)
        ocppService.startMeterValuesWithInterval(id, periodSec);

        log.info("Started MeterValues for session {} every {} seconds (via OCPPService)", id, periodSec);

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "periodSec", periodSec
        ));
    }

    @PostMapping("/{id}/mv/stop")
    @Operation(summary = "Arrête l'envoi périodique de MeterValues")
    public ResponseEntity<Map<String, Object>> stopMeterValues(@PathVariable String id) {
        // Déléguer à OCPPService
        ocppService.stopMeterValuesPublic(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/{id}/mv/restart")
    @Operation(summary = "Redémarre l'envoi périodique de MeterValues avec nouvelle config")
    public ResponseEntity<Map<String, Object>> restartMeterValues(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        int periodSec = body != null ? ((Number) body.getOrDefault("periodSec", 10)).intValue() : 10;

        // Déléguer à OCPPService (startMeterValuesWithInterval arrête automatiquement l'ancienne tâche)
        ocppService.startMeterValuesWithInterval(id, periodSec);

        log.info("Restarted MeterValues for session {} every {} seconds (via OCPPService)", id, periodSec);

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

    // Note: stopMeterValuesTask supprimé - utiliser ocppService.stopMeterValuesPublic() à la place

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
            // Définir les phases actives pour la génération des MeterValues
            session.setActivePhases(vehicleActivePhases);

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
                delayedTaskScheduler.schedule(() -> {
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

    // =========================================================================
    // Pricing
    // =========================================================================

    @GetMapping("/{id}/price")
    @Operation(summary = "Récupère le prix de la session depuis le CSMS (TTE API uniquement)")
    public ResponseEntity<Map<String, Object>> getSessionPrice(@PathVariable String id) {
        // Récupérer la session (utiliser findSession pour éviter exception)
        Session session;
        try {
            session = sessionService.getSession(id);
        } catch (Exception e) {
            log.warn("Session not found for price request: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "ok", false,
                "error", "Session non trouvée: " + id
            ));
        }

        // Vérifier que TTE est configuré
        if (!cognitoTokenService.isConfigured()) {
            log.warn("TTE not configured - cannot fetch price for session {}", id);
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "source", "not_configured",
                "error", "TTE non configuré - définir TTE_CLIENT_ID et TTE_CLIENT_SECRET",
                "message", "Configuration TTE manquante"
            ));
        }

        if (!tteApiService.isAvailable()) {
            log.warn("TTE service unavailable for price request - session {}", id);
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "source", "not_configured",
                "error", "Service TTE indisponible",
                "message", "Service TTE indisponible"
            ));
        }

        // Récupérer les infos de la session
        Integer transactionId = session.getTxId();
        String cpId = session.getCpId();

        if (cpId == null || cpId.isEmpty()) {
            log.warn("No ChargePoint ID for session {} - cannot fetch price", id);
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "source", "error",
                "error", "Pas de ChargePoint ID valide pour cette session",
                "message", "ChargePoint ID manquant"
            ));
        }

        // Récupérer le prix depuis TTE API (CSMS) - même si transactionId est 0 ou null
        int txId = transactionId != null ? transactionId : 0;
        String csmsUrl = session.getWsUrl();
        log.info("Fetching pricing from TTE API for CP: {}, transactionId: {}, env: {}",
                 cpId, txId, csmsUrl != null ? csmsUrl : "default");

        try {
            PricingData pricing = tteApiService.getTransactionPricing(cpId, txId, csmsUrl);

            if (pricing == null) {
                log.info("Price not found in TTE for CP: {}, txId: {}", cpId, txId);
                return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "source", "not_found",
                    "error", "Prix non trouvé dans le CSMS pour cette transaction",
                    "message", "Transaction non trouvée dans TTE",
                    "transactionId", txId,
                    "chargePointId", cpId
                ));
            }

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "source", "tte",
                "totalPrice", pricing.getTotalPrice() != null ? pricing.getTotalPrice() : 0.0,
                "currency", pricing.getCurrency() != null ? pricing.getCurrency() : "EUR",
                "energyKWh", pricing.getEnergyDelivered() != null ? pricing.getEnergyDelivered() : 0.0,
                "pricePerKWh", pricing.getPricePerKwh() != null ? pricing.getPricePerKwh() : 0.0,
                "transactionId", txId,
                "chargePointId", cpId,
                "status", session.getStatus()
            ));

        } catch (Exception e) {
            log.error("Error getting price from TTE API for session {}, CP: {}, txId: {}: {}",
                      id, cpId, txId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "source", "error",
                "error", "Erreur lors de la récupération du prix: " + e.getMessage(),
                "message", "Erreur API TTE: " + e.getMessage(),
                "transactionId", txId,
                "chargePointId", cpId
            ));
        }
    }
}
