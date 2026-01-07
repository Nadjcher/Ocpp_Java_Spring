package com.evse.simulator.controller;

import com.evse.simulator.domain.service.SmartChargingService;
import com.evse.simulator.dto.request.smartcharging.SetChargingProfileRequest;
import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.ChargingProfile.*;
import com.evse.simulator.tte.service.CognitoTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controleur REST pour le Smart Charging OCPP 1.6.
 */
@RestController
@RequestMapping("/api/smart-charging")
@Tag(name = "Smart Charging", description = "Profils de charge intelligente")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class SmartChargingController {

    private final SmartChargingService smartChargingService;
    private final CognitoTokenService cognitoTokenService;
    private final RestTemplate restTemplate;

    // URLs Central Task API par environnement
    private static final String CENTRAL_TASK_URL_PP = "https://evplatform.evcharge-pp.totalenergies.com/evportal/api/task";
    private static final String CENTRAL_TASK_URL_TEST = "https://evplatform.evcharge-test.totalenergies.com/evportal/api/task";

    @PostMapping("/set-profile")
    @Operation(summary = "Appliquer limite de puissance")
    public ResponseEntity<Map<String, Object>> setChargingProfile(
            @RequestParam String sessionId,
            @RequestBody @Valid SetChargingProfileRequest request) {

        // Convertir DTO en ChargingProfile
        ChargingProfile profile = ChargingProfile.builder()
                .chargingProfileId((int) System.currentTimeMillis() % 10000)
                .stackLevel(0)
                .chargingProfilePurpose(ChargingProfilePurpose.fromValue(request.purpose()))
                .chargingProfileKind(ChargingProfileKind.RELATIVE)
                .chargingSchedule(ChargingSchedule.builder()
                        .duration(request.durationSec())
                        .chargingRateUnit(ChargingRateUnit.A)
                        .chargingSchedulePeriod(List.of(
                                ChargingSchedulePeriod.builder()
                                        .startPeriod(0)
                                        // kW to A: pour 230V (phase-neutre), factor = phases
                                        .limit(request.limitKw() * 1000 / (230 * request.phases()))
                                        .numberPhases(request.phases())
                                        .build()
                        ))
                        .build())
                .build();

        String status = smartChargingService.setChargingProfile(sessionId, profile);
        return ResponseEntity.ok(Map.of(
                "status", status,
                "sessionId", sessionId,
                "limitKw", request.limitKw()
        ));
    }

    @PostMapping("/clear-profile")
    @Operation(summary = "Supprimer profil(s)")
    public ResponseEntity<Map<String, Object>> clearChargingProfile(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Integer profileId,
            @Parameter(schema = @Schema(allowableValues = {"ChargePointMaxProfile", "TxDefaultProfile", "TxProfile"}))
            @RequestParam(required = false) String purpose) {

        ChargingProfilePurpose profilePurpose = purpose != null ?
                ChargingProfilePurpose.fromValue(purpose) : null;
        String status = smartChargingService.clearChargingProfile(sessionId, profileId, null, profilePurpose);
        return ResponseEntity.ok(Map.of("status", status));
    }

    @GetMapping("/composite-schedule")
    @Operation(summary = "Planning composite")
    public ResponseEntity<Map<String, Object>> getCompositeSchedule(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "3600") int duration) {

        ChargingSchedule schedule = smartChargingService.getCompositeSchedule(sessionId, duration, ChargingRateUnit.A);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "duration", duration,
                "periods", schedule != null && schedule.getChargingSchedulePeriod() != null ?
                        schedule.getChargingSchedulePeriod().size() : 0
        ));
    }

    @GetMapping("/profiles/{sessionId}")
    @Operation(summary = "Profils actifs")
    public ResponseEntity<List<Map<String, Object>>> getActiveProfiles(@PathVariable String sessionId) {
        List<ChargingProfile> profiles = smartChargingService.getActiveProfiles(sessionId);
        List<Map<String, Object>> summaries = profiles.stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getChargingProfileId(),
                        "purpose", p.getChargingProfilePurpose().getValue(),
                        "stackLevel", p.getStackLevel()
                ))
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/current-limit/{sessionId}")
    @Operation(summary = "Limite actuelle")
    public ResponseEntity<Map<String, Object>> getCurrentLimit(@PathVariable String sessionId) {
        double limitKw = smartChargingService.getCurrentLimit(sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "limitKw", limitKw
        ));
    }

    // =========================================================================
    // Central Task API - Envoyer profil via CSMS
    // =========================================================================

    /**
     * Envoie un SetChargingProfile via l'API Central Task TTE.
     * Cette API permet d'envoyer des commandes OCPP via le CSMS au lieu de directement à la borne.
     *
     * @param request Contient evpId, connectorId, et le profil de charge
     * @param env Environnement cible (pp ou test)
     * @return Résultat de l'appel Central Task
     */
    @PostMapping("/central-task/set-profile")
    @Operation(summary = "Envoyer profil via Central Task API (CSMS → Borne)")
    public ResponseEntity<Map<String, Object>> sendCentralTaskSetProfile(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "pp") String env) {

        try {
            // Extraire les paramètres
            String evpId = (String) request.get("evpId");
            Integer connectorId = request.get("connectorId") != null ?
                    ((Number) request.get("connectorId")).intValue() : 1;
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) request.get("csChargingProfiles");
            String customToken = (String) request.get("customToken");

            if (evpId == null || evpId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "error", "evpId requis"
                ));
            }

            if (profile == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "error", "csChargingProfiles requis"
                ));
            }

            // Déterminer le token à utiliser
            String token;
            if (customToken != null && !customToken.isBlank()) {
                // Utiliser le token personnalisé (pour env Test)
                token = customToken;
                log.info("Using custom bearer token for Central Task");
            } else {
                // Utiliser le token Cognito (pour env PP)
                if (!cognitoTokenService.isConfigured()) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                            "status", "error",
                            "error", "Token Cognito non configuré et aucun token personnalisé fourni."
                    ));
                }
                token = cognitoTokenService.getValidToken();
                log.info("Using Cognito token for Central Task");
            }

            // Construire le payload Central Task
            Map<String, Object> centralTaskPayload = new LinkedHashMap<>();
            centralTaskPayload.put("targets", List.of("evse/" + evpId));
            centralTaskPayload.put("operation", "SET_CHARGING_PROFILE");

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("connectorId", connectorId);
            params.put("csChargingProfiles", profile);
            centralTaskPayload.put("params", params);

            // Sélectionner l'URL selon l'environnement
            String centralTaskUrl = "test".equalsIgnoreCase(env) ? CENTRAL_TASK_URL_TEST : CENTRAL_TASK_URL_PP;

            log.info("Sending Central Task SET_CHARGING_PROFILE to {} for evse/{}", centralTaskUrl, evpId);
            log.debug("Payload: {}", centralTaskPayload);

            // Appeler l'API Central Task
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(centralTaskPayload, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    centralTaskUrl,
                    HttpMethod.POST,
                    httpRequest,
                    Map.class
            );

            log.info("Central Task response: {} - {}", response.getStatusCode(), response.getBody());

            return ResponseEntity.ok(Map.of(
                    "status", "sent",
                    "httpStatus", response.getStatusCode().value(),
                    "response", response.getBody() != null ? response.getBody() : Map.of(),
                    "evpId", evpId,
                    "centralTaskUrl", centralTaskUrl
            ));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Erreur HTTP 4xx du Central Task API
            log.error("Central Task HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "status", "error",
                    "httpStatus", e.getStatusCode().value(),
                    "error", e.getResponseBodyAsString(),
                    "message", "Erreur Central Task API: " + e.getStatusText()
            ));
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            // Erreur HTTP 5xx du Central Task API
            log.error("Central Task server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "status", "error",
                    "httpStatus", e.getStatusCode().value(),
                    "error", e.getResponseBodyAsString(),
                    "message", "Erreur serveur Central Task API"
            ));
        } catch (Exception e) {
            log.error("Central Task error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Envoie un ClearChargingProfile via l'API Central Task TTE.
     */
    @PostMapping("/central-task/clear-profile")
    @Operation(summary = "Effacer profil via Central Task API")
    public ResponseEntity<Map<String, Object>> sendCentralTaskClearProfile(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "pp") String env) {

        try {
            String evpId = (String) request.get("evpId");
            Integer connectorId = request.get("connectorId") != null ?
                    ((Number) request.get("connectorId")).intValue() : 1;
            Integer profileId = request.get("chargingProfileId") != null ?
                    ((Number) request.get("chargingProfileId")).intValue() : null;
            String purpose = (String) request.get("chargingProfilePurpose");
            Integer stackLevel = request.get("stackLevel") != null ?
                    ((Number) request.get("stackLevel")).intValue() : null;
            String customToken = (String) request.get("customToken");

            if (evpId == null || evpId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "error", "evpId requis"
                ));
            }

            // Déterminer le token à utiliser
            String token;
            if (customToken != null && !customToken.isBlank()) {
                // Utiliser le token personnalisé (pour env Test)
                token = customToken;
                log.info("Using custom bearer token for Central Task CLEAR");
            } else {
                // Utiliser le token Cognito (pour env PP)
                if (!cognitoTokenService.isConfigured()) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                            "status", "error",
                            "error", "Token Cognito non configuré et aucun token personnalisé fourni."
                    ));
                }
                token = cognitoTokenService.getValidToken();
                log.info("Using Cognito token for Central Task CLEAR");
            }

            // Construire le payload
            Map<String, Object> centralTaskPayload = new LinkedHashMap<>();
            centralTaskPayload.put("targets", List.of("evse/" + evpId));
            centralTaskPayload.put("operation", "CLEAR_CHARGING_PROFILE");

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("connectorId", connectorId);
            if (profileId != null) params.put("id", profileId);
            if (purpose != null) params.put("chargingProfilePurpose", purpose);
            if (stackLevel != null) params.put("stackLevel", stackLevel);
            centralTaskPayload.put("params", params);

            String centralTaskUrl = "test".equalsIgnoreCase(env) ? CENTRAL_TASK_URL_TEST : CENTRAL_TASK_URL_PP;

            log.info("Sending Central Task CLEAR_CHARGING_PROFILE to {} for evse/{}", centralTaskUrl, evpId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(centralTaskPayload, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    centralTaskUrl,
                    HttpMethod.POST,
                    httpRequest,
                    Map.class
            );

            return ResponseEntity.ok(Map.of(
                    "status", "sent",
                    "httpStatus", response.getStatusCode().value(),
                    "response", response.getBody() != null ? response.getBody() : Map.of(),
                    "evpId", evpId
            ));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Erreur HTTP 4xx du Central Task API
            log.error("Central Task CLEAR HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "status", "error",
                    "httpStatus", e.getStatusCode().value(),
                    "error", e.getResponseBodyAsString(),
                    "message", "Erreur Central Task API: " + e.getStatusText()
            ));
        } catch (org.springframework.web.client.HttpServerErrorException e) {
            // Erreur HTTP 5xx du Central Task API
            log.error("Central Task CLEAR server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "status", "error",
                    "httpStatus", e.getStatusCode().value(),
                    "error", e.getResponseBodyAsString(),
                    "message", "Erreur serveur Central Task API"
            ));
        } catch (Exception e) {
            log.error("Central Task CLEAR error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Vérifie le statut du token Cognito pour Central Task.
     */
    @GetMapping("/central-task/token-status")
    @Operation(summary = "Statut du token Cognito")
    public ResponseEntity<Map<String, Object>> getCentralTaskTokenStatus() {
        var tokenInfo = cognitoTokenService.getTokenInfo();
        return ResponseEntity.ok(Map.of(
                "configured", tokenInfo.isConfigured(),
                "enabled", tokenInfo.isEnabled(),
                "hasValidToken", tokenInfo.isHasValidToken(),
                "secondsRemaining", tokenInfo.getSecondsRemaining(),
                "refreshCount", tokenInfo.getRefreshCount(),
                "errorCount", tokenInfo.getErrorCount(),
                "lastError", tokenInfo.getLastError() != null ? tokenInfo.getLastError() : ""
        ));
    }
}
