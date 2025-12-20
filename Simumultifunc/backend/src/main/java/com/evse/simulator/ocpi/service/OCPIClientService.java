package com.evse.simulator.ocpi.service;

import com.evse.simulator.ocpi.OCPIModule;
import com.evse.simulator.ocpi.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * HTTP Client service for OCPI API calls.
 * Handles authentication, request building, and response parsing.
 */
@Service
@Slf4j
public class OCPIClientService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PartnerService partnerService;

    public OCPIClientService(PartnerService partnerService) {
        this.partnerService = partnerService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // =========================================================================
    // Generic HTTP Methods
    // =========================================================================

    /**
     * Execute GET request to partner.
     */
    public <T> OCPIResponse<T> get(String partnerId, String url, Class<T> responseType) {
        return execute(partnerId, HttpMethod.GET, url, null, responseType);
    }

    /**
     * Execute GET request with query parameters.
     */
    public <T> OCPIResponse<T> get(String partnerId, String url, Map<String, String> params, Class<T> responseType) {
        String fullUrl = buildUrlWithParams(url, params);
        return execute(partnerId, HttpMethod.GET, fullUrl, null, responseType);
    }

    /**
     * Execute POST request.
     */
    public <T> OCPIResponse<T> post(String partnerId, String url, Object body, Class<T> responseType) {
        return execute(partnerId, HttpMethod.POST, url, body, responseType);
    }

    /**
     * Execute PUT request.
     */
    public <T> OCPIResponse<T> put(String partnerId, String url, Object body, Class<T> responseType) {
        return execute(partnerId, HttpMethod.PUT, url, body, responseType);
    }

    /**
     * Execute PATCH request.
     */
    public <T> OCPIResponse<T> patch(String partnerId, String url, Object body, Class<T> responseType) {
        return execute(partnerId, HttpMethod.PATCH, url, body, responseType);
    }

    /**
     * Execute DELETE request.
     */
    public <T> OCPIResponse<T> delete(String partnerId, String url, Class<T> responseType) {
        return execute(partnerId, HttpMethod.DELETE, url, null, responseType);
    }

    // =========================================================================
    // Version Discovery
    // =========================================================================

    /**
     * Discover OCPI versions from partner.
     */
    public OCPIResponse<List<VersionInfo>> getVersions(String partnerId) {
        Partner partner = partnerService.getPartner(partnerId)
                .orElseThrow(() -> new IllegalArgumentException("Partner not found: " + partnerId));

        Partner.EnvironmentConfig config = partner.getActiveConfig();
        if (config == null) {
            throw new IllegalStateException("No active environment for partner: " + partnerId);
        }

        String versionsUrl = config.getVersionsUrl() != null ? config.getVersionsUrl()
                : config.getBaseUrl() + "/ocpi/versions";

        return execute(partnerId, HttpMethod.GET, versionsUrl, null, new TypeReference<List<VersionInfo>>() {});
    }

    /**
     * Get version details (endpoints).
     */
    public OCPIResponse<VersionDetails> getVersionDetails(String partnerId, String versionUrl) {
        return execute(partnerId, HttpMethod.GET, versionUrl, null, VersionDetails.class);
    }

    // =========================================================================
    // Credentials Module
    // =========================================================================

    /**
     * Get credentials from partner (handshake step 1).
     */
    public OCPIResponse<Credentials> getCredentials(String partnerId) {
        String url = getModuleUrl(partnerId, OCPIModule.CREDENTIALS);
        return get(partnerId, url, Credentials.class);
    }

    /**
     * Post credentials to partner (handshake step 2).
     */
    public OCPIResponse<Credentials> postCredentials(String partnerId, Credentials credentials) {
        String url = getModuleUrl(partnerId, OCPIModule.CREDENTIALS);
        return post(partnerId, url, credentials, Credentials.class);
    }

    /**
     * Update credentials (re-registration).
     */
    public OCPIResponse<Credentials> putCredentials(String partnerId, Credentials credentials) {
        String url = getModuleUrl(partnerId, OCPIModule.CREDENTIALS);
        return put(partnerId, url, credentials, Credentials.class);
    }

    // =========================================================================
    // Locations Module
    // =========================================================================

    /**
     * Get all locations from CPO.
     */
    public OCPIResponse<List<Location>> getLocations(String partnerId, Map<String, String> params) {
        String url = getModuleUrl(partnerId, OCPIModule.LOCATIONS);
        return execute(partnerId, HttpMethod.GET, buildUrlWithParams(url, params), null,
                new TypeReference<List<Location>>() {});
    }

    /**
     * Get specific location.
     */
    public OCPIResponse<Location> getLocation(String partnerId, String countryCode, String partyId, String locationId) {
        String url = getModuleUrl(partnerId, OCPIModule.LOCATIONS) + "/" + countryCode + "/" + partyId + "/" + locationId;
        return get(partnerId, url, Location.class);
    }

    /**
     * Get specific EVSE.
     */
    public OCPIResponse<EVSE> getEvse(String partnerId, String countryCode, String partyId, String locationId, String evseUid) {
        String url = getModuleUrl(partnerId, OCPIModule.LOCATIONS) + "/" + countryCode + "/" + partyId + "/" + locationId + "/" + evseUid;
        return get(partnerId, url, EVSE.class);
    }

    // =========================================================================
    // Sessions Module
    // =========================================================================

    /**
     * Get sessions.
     */
    public OCPIResponse<List<OCPISession>> getSessions(String partnerId, Map<String, String> params) {
        String url = getModuleUrl(partnerId, OCPIModule.SESSIONS);
        return execute(partnerId, HttpMethod.GET, buildUrlWithParams(url, params), null,
                new TypeReference<List<OCPISession>>() {});
    }

    /**
     * Get specific session.
     */
    public OCPIResponse<OCPISession> getSession(String partnerId, String countryCode, String partyId, String sessionId) {
        String url = getModuleUrl(partnerId, OCPIModule.SESSIONS) + "/" + countryCode + "/" + partyId + "/" + sessionId;
        return get(partnerId, url, OCPISession.class);
    }

    /**
     * PUT session (receiver interface).
     */
    public OCPIResponse<Void> putSession(String partnerId, String countryCode, String partyId, String sessionId, OCPISession session) {
        String url = getModuleUrl(partnerId, OCPIModule.SESSIONS) + "/" + countryCode + "/" + partyId + "/" + sessionId;
        return put(partnerId, url, session, Void.class);
    }

    // =========================================================================
    // CDRs Module
    // =========================================================================

    /**
     * Get CDRs.
     */
    public OCPIResponse<List<CDR>> getCdrs(String partnerId, Map<String, String> params) {
        String url = getModuleUrl(partnerId, OCPIModule.CDRS);
        return execute(partnerId, HttpMethod.GET, buildUrlWithParams(url, params), null,
                new TypeReference<List<CDR>>() {});
    }

    /**
     * Post CDR (sender interface).
     */
    public OCPIResponse<Void> postCdr(String partnerId, CDR cdr) {
        String url = getModuleUrl(partnerId, OCPIModule.CDRS);
        return post(partnerId, url, cdr, Void.class);
    }

    // =========================================================================
    // Tokens Module
    // =========================================================================

    /**
     * Get tokens.
     */
    public OCPIResponse<List<Token>> getTokens(String partnerId, Map<String, String> params) {
        String url = getModuleUrl(partnerId, OCPIModule.TOKENS);
        return execute(partnerId, HttpMethod.GET, buildUrlWithParams(url, params), null,
                new TypeReference<List<Token>>() {});
    }

    /**
     * Authorize token (real-time authorization).
     */
    public OCPIResponse<AuthorizationInfo> postAuthorize(String partnerId, String tokenUid, LocationReferences locationReferences) {
        String url = getModuleUrl(partnerId, OCPIModule.TOKENS) + "/" + tokenUid + "/authorize";
        return post(partnerId, url, locationReferences, AuthorizationInfo.class);
    }

    // =========================================================================
    // Commands Module
    // =========================================================================

    /**
     * Send StartSession command.
     */
    public OCPIResponse<Command.CommandResponse> startSession(String partnerId, Command.StartSession command) {
        String url = getModuleUrl(partnerId, OCPIModule.COMMANDS) + "/START_SESSION";
        return post(partnerId, url, command, Command.CommandResponse.class);
    }

    /**
     * Send StopSession command.
     */
    public OCPIResponse<Command.CommandResponse> stopSession(String partnerId, Command.StopSession command) {
        String url = getModuleUrl(partnerId, OCPIModule.COMMANDS) + "/STOP_SESSION";
        return post(partnerId, url, command, Command.CommandResponse.class);
    }

    /**
     * Send ReserveNow command.
     */
    public OCPIResponse<Command.CommandResponse> reserveNow(String partnerId, Command.ReserveNow command) {
        String url = getModuleUrl(partnerId, OCPIModule.COMMANDS) + "/RESERVE_NOW";
        return post(partnerId, url, command, Command.CommandResponse.class);
    }

    /**
     * Send CancelReservation command.
     */
    public OCPIResponse<Command.CommandResponse> cancelReservation(String partnerId, Command.CancelReservation command) {
        String url = getModuleUrl(partnerId, OCPIModule.COMMANDS) + "/CANCEL_RESERVATION";
        return post(partnerId, url, command, Command.CommandResponse.class);
    }

    /**
     * Send UnlockConnector command.
     */
    public OCPIResponse<Command.CommandResponse> unlockConnector(String partnerId, Command.UnlockConnector command) {
        String url = getModuleUrl(partnerId, OCPIModule.COMMANDS) + "/UNLOCK_CONNECTOR";
        return post(partnerId, url, command, Command.CommandResponse.class);
    }

    // =========================================================================
    // Tariffs Module
    // =========================================================================

    /**
     * Get tariffs.
     */
    public OCPIResponse<List<Tariff>> getTariffs(String partnerId, Map<String, String> params) {
        String url = getModuleUrl(partnerId, OCPIModule.TARIFFS);
        return execute(partnerId, HttpMethod.GET, buildUrlWithParams(url, params), null,
                new TypeReference<List<Tariff>>() {});
    }

    // =========================================================================
    // ChargingProfiles Module
    // =========================================================================

    /**
     * Set charging profile.
     */
    public OCPIResponse<ChargingProfile.ChargingProfileResult> setChargingProfile(
            String partnerId, String sessionId, Command.SetChargingProfile command) {
        String url = getModuleUrl(partnerId, OCPIModule.CHARGING_PROFILES) + "/" + sessionId;
        return put(partnerId, url, command, ChargingProfile.ChargingProfileResult.class);
    }

    /**
     * Get active charging profile.
     */
    public OCPIResponse<ChargingProfile.ActiveChargingProfile> getActiveChargingProfile(
            String partnerId, String sessionId, Integer duration, String responseUrl) {
        String url = getModuleUrl(partnerId, OCPIModule.CHARGING_PROFILES) + "/" + sessionId
                + "?duration=" + duration + "&response_url=" + responseUrl;
        return get(partnerId, url, ChargingProfile.ActiveChargingProfile.class);
    }

    // =========================================================================
    // Raw Request Builder (Postman-like)
    // =========================================================================

    /**
     * Execute a raw OCPI request with full control over method, headers, body.
     * Returns extended response with raw body and response headers.
     */
    public RawOCPIResponse executeRawRequest(
            String partnerId,
            String method,
            String url,
            Map<String, String> queryParams,
            Map<String, String> customHeaders,
            Object body) {

        try {
            // Build URL with query params
            String finalUrl = buildUrlWithParams(url, queryParams);

            // Build headers
            HttpHeaders headers = buildHeaders(partnerId);
            if (customHeaders != null) {
                customHeaders.forEach(headers::set);
            }

            // Build request entity
            HttpEntity<?> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);

            // Resolve HTTP method
            HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

            log.info("Executing raw OCPI request: {} {}", method, finalUrl);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(finalUrl, httpMethod, entity, String.class);
            long latency = System.currentTimeMillis() - startTime;

            // Parse OCPI envelope
            JsonNode root = null;
            int ocpiStatusCode = 0;
            String ocpiStatusMessage = "";
            Object data = null;

            if (response.getBody() != null && !response.getBody().isEmpty()) {
                try {
                    root = objectMapper.readTree(response.getBody());
                    if (root.has("status_code")) {
                        ocpiStatusCode = root.get("status_code").asInt();
                    }
                    if (root.has("status_message")) {
                        ocpiStatusMessage = root.get("status_message").asText();
                    }
                    if (root.has("data")) {
                        data = objectMapper.treeToValue(root.get("data"), Object.class);
                    }
                } catch (Exception e) {
                    log.warn("Response is not valid OCPI JSON: {}", e.getMessage());
                }
            }

            boolean success = response.getStatusCode().is2xxSuccessful()
                    && ocpiStatusCode >= 1000 && ocpiStatusCode < 2000;

            // Extract response headers
            Map<String, String> responseHeaders = new HashMap<>();
            response.getHeaders().forEach((key, values) ->
                    responseHeaders.put(key, String.join(", ", values)));

            return RawOCPIResponse.builder()
                    .success(success)
                    .httpStatus(response.getStatusCode().value())
                    .statusCode(ocpiStatusCode)
                    .statusMessage(ocpiStatusMessage)
                    .data(data)
                    .rawBody(response.getBody())
                    .responseHeaders(responseHeaders)
                    .latencyMs(latency)
                    .timestamp(Instant.now())
                    .build();

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // HTTP error (4xx, 5xx)
            log.error("HTTP error on raw request: {} {} - {}", method, url, e.getStatusCode());

            Map<String, String> responseHeaders = new HashMap<>();
            e.getResponseHeaders().forEach((key, values) ->
                    responseHeaders.put(key, String.join(", ", values)));

            return RawOCPIResponse.builder()
                    .success(false)
                    .httpStatus(e.getStatusCode().value())
                    .statusCode(0)
                    .statusMessage(e.getStatusText())
                    .rawBody(e.getResponseBodyAsString())
                    .responseHeaders(responseHeaders)
                    .latencyMs(0)
                    .timestamp(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Raw OCPI request failed: {} {} - {}", method, url, e.getMessage());
            return RawOCPIResponse.builder()
                    .success(false)
                    .httpStatus(0)
                    .statusCode(0)
                    .statusMessage(e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }

    /**
     * Extended response for raw requests with full details.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RawOCPIResponse {
        private boolean success;
        private int httpStatus;
        private int statusCode;           // OCPI status code
        private String statusMessage;
        private Object data;              // Parsed data object
        private String rawBody;           // Raw response body
        private Map<String, String> responseHeaders;
        private long latencyMs;
        private Instant timestamp;
    }

    // =========================================================================
    // Internal Methods
    // =========================================================================

    private <T> OCPIResponse<T> execute(String partnerId, HttpMethod method, String url, Object body, Class<T> responseType) {
        try {
            HttpHeaders headers = buildHeaders(partnerId);
            HttpEntity<?> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            long latency = System.currentTimeMillis() - startTime;

            return parseResponse(response, responseType, latency);
        } catch (Exception e) {
            log.error("OCPI request failed: {} {} - {}", method, url, e.getMessage());
            return OCPIResponse.<T>builder()
                    .success(false)
                    .statusCode(0)
                    .statusMessage(e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }

    private <T> OCPIResponse<T> execute(String partnerId, HttpMethod method, String url, Object body, TypeReference<T> typeRef) {
        try {
            HttpHeaders headers = buildHeaders(partnerId);
            HttpEntity<?> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            long latency = System.currentTimeMillis() - startTime;

            return parseResponse(response, typeRef, latency);
        } catch (Exception e) {
            log.error("OCPI request failed: {} {} - {}", method, url, e.getMessage());
            return OCPIResponse.<T>builder()
                    .success(false)
                    .statusCode(0)
                    .statusMessage(e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }

    private HttpHeaders buildHeaders(String partnerId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        Partner partner = partnerService.getPartner(partnerId).orElse(null);
        if (partner != null) {
            String token = partner.getTokenB();
            if (token != null) {
                headers.set("Authorization", "Token " + Base64.getEncoder().encodeToString(token.getBytes()));
            }

            // Add OCPI headers
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Correlation-ID", UUID.randomUUID().toString());

            // Add from/to party headers
            headers.set("OCPI-from-country-code", "FR");
            headers.set("OCPI-from-party-id", "TTE");
            if (partner.getCountryCode() != null) {
                headers.set("OCPI-to-country-code", partner.getCountryCode());
            }
            if (partner.getPartyId() != null) {
                headers.set("OCPI-to-party-id", partner.getPartyId());
            }
        }

        return headers;
    }

    private <T> OCPIResponse<T> parseResponse(ResponseEntity<String> response, Class<T> type, long latency) {
        try {
            JsonNode root = objectMapper.readTree(response.getBody());

            OCPIResponse.OCPIResponseBuilder<T> builder = OCPIResponse.<T>builder()
                    .httpStatusCode(response.getStatusCode().value())
                    .latencyMs(latency)
                    .timestamp(Instant.now());

            if (root.has("status_code")) {
                builder.statusCode(root.get("status_code").asInt());
            }
            if (root.has("status_message")) {
                builder.statusMessage(root.get("status_message").asText());
            }
            if (root.has("timestamp")) {
                builder.ocpiTimestamp(root.get("timestamp").asText());
            }

            boolean success = response.getStatusCode().is2xxSuccessful()
                    && (root.has("status_code") && root.get("status_code").asInt() >= 1000 && root.get("status_code").asInt() < 2000);
            builder.success(success);

            if (root.has("data") && type != Void.class) {
                T data = objectMapper.treeToValue(root.get("data"), type);
                builder.data(data);
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse OCPI response: {}", e.getMessage());
            return OCPIResponse.<T>builder()
                    .success(false)
                    .httpStatusCode(response.getStatusCode().value())
                    .statusMessage("Failed to parse response: " + e.getMessage())
                    .latencyMs(latency)
                    .timestamp(Instant.now())
                    .build();
        }
    }

    private <T> OCPIResponse<T> parseResponse(ResponseEntity<String> response, TypeReference<T> typeRef, long latency) {
        try {
            JsonNode root = objectMapper.readTree(response.getBody());

            OCPIResponse.OCPIResponseBuilder<T> builder = OCPIResponse.<T>builder()
                    .httpStatusCode(response.getStatusCode().value())
                    .latencyMs(latency)
                    .timestamp(Instant.now());

            if (root.has("status_code")) {
                builder.statusCode(root.get("status_code").asInt());
            }
            if (root.has("status_message")) {
                builder.statusMessage(root.get("status_message").asText());
            }

            boolean success = response.getStatusCode().is2xxSuccessful()
                    && (root.has("status_code") && root.get("status_code").asInt() >= 1000 && root.get("status_code").asInt() < 2000);
            builder.success(success);

            if (root.has("data")) {
                T data = objectMapper.readValue(objectMapper.treeAsTokens(root.get("data")), typeRef);
                builder.data(data);
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Failed to parse OCPI response: {}", e.getMessage());
            return OCPIResponse.<T>builder()
                    .success(false)
                    .httpStatusCode(response.getStatusCode().value())
                    .statusMessage("Failed to parse response: " + e.getMessage())
                    .latencyMs(latency)
                    .timestamp(Instant.now())
                    .build();
        }
    }

    private String getModuleUrl(String partnerId, OCPIModule module) {
        String url = partnerService.getEndpointUrl(partnerId, module);
        if (url == null) {
            throw new IllegalStateException("No endpoint URL for module " + module + " on partner " + partnerId);
        }
        return url;
    }

    private String buildUrlWithParams(String baseUrl, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }
        StringBuilder sb = new StringBuilder(baseUrl);
        sb.append("?");
        params.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
        return sb.substring(0, sb.length() - 1);
    }

    // =========================================================================
    // Response and Helper Classes
    // =========================================================================

    /**
     * Generic OCPI response wrapper.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OCPIResponse<T> {
        private boolean success;
        private int httpStatusCode;
        private int statusCode;           // OCPI status code (1000 = success)
        private String statusMessage;
        private String ocpiTimestamp;
        private T data;
        private long latencyMs;
        private Instant timestamp;
    }

    /**
     * Version info from versions endpoint.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VersionInfo {
        private String version;
        private String url;
    }

    /**
     * Version details with endpoints.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VersionDetails {
        private String version;
        private List<Endpoint> endpoints;
    }

    /**
     * Module endpoint info.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Endpoint {
        private String identifier;
        private String role;
        private String url;
    }

    /**
     * Authorization info response.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AuthorizationInfo {
        private AllowedType allowed;
        private Token token;
        private Location.DisplayText info;
    }

    public enum AllowedType {
        ALLOWED, BLOCKED, EXPIRED, NO_CREDIT, NOT_ALLOWED
    }

    /**
     * Location references for authorization.
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LocationReferences {
        private String locationId;
        private List<String> evseUids;
    }
}
