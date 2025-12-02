package com.evse.simulator.controller;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.ChargingProfile.ChargingProfilePurpose;
import com.evse.simulator.domain.service.SmartChargingService;
import com.evse.simulator.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Contrôleur pour les opérations OCPP 1.6.
 */
@RestController
@RequestMapping("/api/ocpp")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name = "OCPP", description = "Opérations OCPP 1.6 (Authorize, StartTransaction, etc.)")
public class OCPPController {

    private final OCPPService ocppService;
    private final SmartChargingService smartChargingService;
    private final SessionService sessionService;

    @Operation(summary = "Envoie une requête Authorize")
    @PostMapping("/{sessionId}/authorize")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> authorize(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        // Met à jour l'idTag si fourni
        String idTag = request.get("idTag");
        if (idTag != null && !idTag.isBlank()) {
            sessionService.findSession(sessionId).ifPresent(s -> s.setIdTag(idTag));
        }
        return ocppService.sendAuthorize(sessionId)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().body(
                        Map.of("error", ex.getMessage())
                ));
    }

    @Operation(summary = "Démarre une transaction de charge")
    @PostMapping("/{sessionId}/start-transaction")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> startTransaction(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        // Met à jour l'idTag si fourni
        String idTag = request.get("idTag");
        if (idTag != null && !idTag.isBlank()) {
            sessionService.findSession(sessionId).ifPresent(s -> s.setIdTag(idTag));
        }
        return ocppService.sendStartTransaction(sessionId)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().body(
                        Map.of("error", ex.getMessage())
                ));
    }

    @Operation(summary = "Arrête une transaction de charge")
    @PostMapping("/{sessionId}/stop-transaction")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> stopTransaction(@PathVariable String sessionId) {
        return ocppService.sendStopTransaction(sessionId)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.badRequest().body(
                        Map.of("error", ex.getMessage())
                ));
    }

    @Operation(summary = "Applique un profil de charge (Smart Charging)")
    @PostMapping("/{sessionId}/set-charging-profile")
    public ResponseEntity<Map<String, Object>> setChargingProfile(
            @PathVariable String sessionId,
            @RequestBody ChargingProfile profile) {
        String result = smartChargingService.setChargingProfile(sessionId, profile);
        return ResponseEntity.ok(Map.of("status", result));
    }

    @Operation(summary = "Supprime un profil de charge")
    @PostMapping("/{sessionId}/clear-charging-profile")
    public ResponseEntity<Map<String, Object>> clearChargingProfile(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {
        Integer profileId = request.get("profileId") != null ?
                ((Number) request.get("profileId")).intValue() : null;
        Integer stackLevel = request.get("stackLevel") != null ?
                ((Number) request.get("stackLevel")).intValue() : null;
        String purposeStr = (String) request.get("purpose");
        ChargingProfilePurpose purpose = purposeStr != null ?
                ChargingProfilePurpose.valueOf(purposeStr) : null;

        String result = smartChargingService.clearChargingProfile(sessionId, profileId, stackLevel, purpose);
        return ResponseEntity.ok(Map.of("status", result));
    }

    @Operation(summary = "Envoie un message OCPP personnalisé")
    @PostMapping("/{sessionId}/send")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendMessage(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {
        String action = (String) request.get("action");

        // Route vers la méthode appropriée selon l'action
        return switch (action != null ? action.toLowerCase() : "") {
            case "bootnotification" -> ocppService.sendBootNotification(sessionId)
                    .thenApply(ResponseEntity::ok);
            case "authorize" -> ocppService.sendAuthorize(sessionId)
                    .thenApply(ResponseEntity::ok);
            case "starttransaction" -> ocppService.sendStartTransaction(sessionId)
                    .thenApply(ResponseEntity::ok);
            case "stoptransaction" -> ocppService.sendStopTransaction(sessionId)
                    .thenApply(ResponseEntity::ok);
            case "heartbeat" -> ocppService.sendHeartbeat(sessionId)
                    .thenApply(ResponseEntity::ok);
            case "metervalues" -> ocppService.sendMeterValues(sessionId)
                    .thenApply(ResponseEntity::ok);
            default -> CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.of("error", "Unknown action: " + action))
            );
        };
    }
}