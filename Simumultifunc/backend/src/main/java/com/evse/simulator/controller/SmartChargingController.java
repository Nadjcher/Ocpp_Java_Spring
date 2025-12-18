package com.evse.simulator.controller;

import com.evse.simulator.domain.service.SmartChargingService;
import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.ChargingProfile.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour le Smart Charging.
 */
@RestController
@RequestMapping("/api/smart-charging")
@Tag(name = "Smart Charging", description = "Gestion des profils de charge intelligente")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class SmartChargingController {

    private final SmartChargingService smartChargingService;

    @PostMapping("/set-profile")
    @Operation(summary = "Définit un profil de charge")
    public ResponseEntity<Map<String, Object>> setChargingProfile(
            @RequestParam String sessionId,
            @RequestBody ChargingProfile profile) {

        String status = smartChargingService.setChargingProfile(sessionId, profile);

        return ResponseEntity.ok(Map.of(
                "status", status,
                "sessionId", sessionId,
                "chargingProfileId", profile.getChargingProfileId()
        ));
    }

    @PostMapping("/clear-profile")
    @Operation(summary = "Supprime un ou plusieurs profils")
    public ResponseEntity<Map<String, Object>> clearChargingProfile(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Integer chargingProfileId,
            @RequestParam(required = false) Integer stackLevel,
            @RequestParam(required = false) String chargingProfilePurpose) {

        ChargingProfilePurpose purpose = chargingProfilePurpose != null ?
                ChargingProfilePurpose.fromValue(chargingProfilePurpose) : null;

        String status = smartChargingService.clearChargingProfile(
                sessionId, chargingProfileId, stackLevel, purpose);

        return ResponseEntity.ok(Map.of(
                "status", status
        ));
    }

    @GetMapping("/composite-schedule")
    @Operation(summary = "Récupère le planning de charge composite")
    public ResponseEntity<ChargingSchedule> getCompositeSchedule(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "86400") int duration,
            @RequestParam(defaultValue = "A") String chargingRateUnit) {

        ChargingRateUnit unit = ChargingRateUnit.fromValue(chargingRateUnit);
        ChargingSchedule schedule = smartChargingService.getCompositeSchedule(
                sessionId, duration, unit);

        return ResponseEntity.ok(schedule);
    }

    @GetMapping("/profiles/{sessionId}")
    @Operation(summary = "Récupère les profils actifs d'une session")
    public ResponseEntity<List<ChargingProfile>> getActiveProfiles(@PathVariable String sessionId) {
        return ResponseEntity.ok(smartChargingService.getActiveProfiles(sessionId));
    }

    @GetMapping("/charge-point-max-profile")
    @Operation(summary = "Récupère le profil ChargePointMaxProfile global")
    public ResponseEntity<ChargingProfile> getChargePointMaxProfile() {
        ChargingProfile profile = smartChargingService.getChargePointMaxProfile();
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/current-limit/{sessionId}")
    @Operation(summary = "Récupère la limite de puissance actuelle")
    public ResponseEntity<Map<String, Object>> getCurrentLimit(@PathVariable String sessionId) {
        double limitKw = smartChargingService.getCurrentLimit(sessionId);

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "currentLimitKw", limitKw
        ));
    }
}