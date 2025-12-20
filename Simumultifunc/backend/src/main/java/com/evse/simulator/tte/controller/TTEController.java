package com.evse.simulator.tte.controller;

import com.evse.simulator.tte.model.ChargingProfileRequest;
import com.evse.simulator.tte.model.ChargingProfileResponse;
import com.evse.simulator.tte.model.PricingData;
import com.evse.simulator.tte.model.TokenInfo;
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

/**
 * Controller REST pour l'API TTE.
 * <p>
 * Expose les endpoints pour:
 * - Statut du token Cognito
 * - Tarification des sessions
 * - Smart Charging (profils de charge)
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/tte")
@Tag(name = "TTE API", description = "Integration TTE (Cognito OAuth2)")
@RequiredArgsConstructor
@CrossOrigin
public class TTEController {

    private final CognitoTokenService tokenService;
    private final TTEApiService tteApiService;

    // =========================================================================
    // Token Management
    // =========================================================================

    @GetMapping("/token/status")
    @Operation(summary = "Statut du token Cognito (sans exposer le token)")
    public ResponseEntity<TokenInfo> getTokenStatus() {
        TokenInfo info = tokenService.getTokenInfo();
        return ResponseEntity.ok(info);
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "Force le renouvellement du token")
    public ResponseEntity<TokenInfo> refreshToken() {
        try {
            tokenService.refreshToken();
            return ResponseEntity.ok(tokenService.getTokenInfo());
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            TokenInfo info = tokenService.getTokenInfo();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(info);
        }
    }

    // =========================================================================
    // Health / Status
    // =========================================================================

    @GetMapping("/health")
    @Operation(summary = "Statut de santé du service TTE")
    public ResponseEntity<Map<String, Object>> getHealth() {
        return ResponseEntity.ok(tteApiService.getHealthInfo());
    }

    // =========================================================================
    // Pricing API
    // =========================================================================

    @GetMapping("/pricing/transaction/{ocppId}/{transactionId}")
    @Operation(summary = "Récupère la tarification par OCPP ID et transaction ID")
    public ResponseEntity<PricingData> getTransactionPricing(
            @PathVariable String ocppId,
            @PathVariable Integer transactionId,
            @RequestParam(required = false) String csmsUrl) {
        if (!tteApiService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            // csmsUrl permet de déterminer l'environnement (test ou pp)
            // Si non fourni, utilise test par défaut
            PricingData pricing = tteApiService.getTransactionPricing(ocppId, transactionId, csmsUrl);
            return ResponseEntity.ok(pricing);
        } catch (Exception e) {
            log.error("Failed to get pricing for OCPP ID {}, transactionId {}: {}",
                    ocppId, transactionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/pricing/session/{sessionId}")
    @Operation(summary = "Récupère la tarification d'une session (par ID interne)")
    public ResponseEntity<PricingData> getSessionPricing(@PathVariable String sessionId) {
        if (!tteApiService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            PricingData pricing = tteApiService.getSessionPricing(sessionId);
            return ResponseEntity.ok(pricing);
        } catch (Exception e) {
            log.error("Failed to get pricing for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // =========================================================================
    // Smart Charging API
    // =========================================================================

    @PostMapping("/smart-charging/profile")
    @Operation(summary = "Envoie un profil de charge")
    public ResponseEntity<ChargingProfileResponse> sendChargingProfile(
            @RequestBody ChargingProfileRequest request) {

        if (!tteApiService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            ChargingProfileResponse response = tteApiService.sendChargingProfile(
                    request.getChargePointId(),
                    request
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to send charging profile: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChargingProfileResponse.builder()
                            .status("Failed")
                            .statusMessage(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/smart-charging/profiles/{cpId}")
    @Operation(summary = "Récupère les profils actifs d'un Charge Point")
    public ResponseEntity<List<ChargingProfileResponse>> getActiveProfiles(@PathVariable String cpId) {
        if (!tteApiService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            List<ChargingProfileResponse> profiles = tteApiService.getActiveProfiles(cpId);
            return ResponseEntity.ok(profiles);
        } catch (Exception e) {
            log.error("Failed to get profiles for CP {}: {}", cpId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/smart-charging/profile/{cpId}/{profileId}")
    @Operation(summary = "Supprime un profil de charge")
    public ResponseEntity<Void> clearChargingProfile(
            @PathVariable String cpId,
            @PathVariable String profileId) {

        if (!tteApiService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            tteApiService.clearChargingProfile(cpId, profileId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to clear profile {} for CP {}: {}", profileId, cpId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // =========================================================================
    // Quick Actions
    // =========================================================================

    @PostMapping("/smart-charging/set-limit/{cpId}")
    @Operation(summary = "Définit rapidement une limite de puissance en Watts")
    public ResponseEntity<ChargingProfileResponse> setQuickLimit(
            @PathVariable String cpId,
            @RequestParam double limitWatts,
            @RequestParam(defaultValue = "1") int connectorId) {

        if (!tteApiService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            ChargingProfileRequest request = ChargingProfileRequest.builder()
                    .chargePointId(cpId)
                    .connectorId(connectorId)
                    .stackLevel(0)
                    .chargingProfilePurpose("TxDefaultProfile")
                    .chargingProfileKind("Absolute")
                    .chargingSchedule(ChargingProfileRequest.ChargingSchedule.builder()
                            .chargingRateUnit("W")
                            .chargingSchedulePeriod(List.of(
                                    ChargingProfileRequest.ChargingSchedulePeriod.builder()
                                            .startPeriod(0)
                                            .limit(limitWatts)
                                            .build()
                            ))
                            .build())
                    .build();

            ChargingProfileResponse response = tteApiService.sendChargingProfile(cpId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to set quick limit for CP {}: {}", cpId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChargingProfileResponse.builder()
                            .status("Failed")
                            .statusMessage(e.getMessage())
                            .build());
        }
    }
}
