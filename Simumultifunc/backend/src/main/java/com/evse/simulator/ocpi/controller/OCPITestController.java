package com.evse.simulator.ocpi.controller;

import com.evse.simulator.ocpi.model.Partner;
import com.evse.simulator.ocpi.service.OCPIClientService;
import com.evse.simulator.ocpi.service.PartnerService;
import com.evse.simulator.ocpi.test.OCPITestRepository;
import com.evse.simulator.ocpi.test.OCPITestResult;
import com.evse.simulator.ocpi.test.OCPITestRunner;
import com.evse.simulator.ocpi.test.OCPITestScenario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for OCPI testing functionality.
 */
@RestController
@RequestMapping("/api/ocpi")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(originPatterns = "*", allowCredentials = "false")
public class OCPITestController {

    private final PartnerService partnerService;
    private final OCPIClientService ocpiClient;
    private final OCPITestRunner testRunner;
    private final OCPITestRepository testRepository;

    // =========================================================================
    // Partners
    // =========================================================================

    @GetMapping("/partners")
    public ResponseEntity<List<Partner>> getPartners() {
        return ResponseEntity.ok(partnerService.getAllPartners());
    }

    @GetMapping("/partners/active")
    public ResponseEntity<List<Partner>> getActivePartners() {
        return ResponseEntity.ok(partnerService.getActivePartners());
    }

    @GetMapping("/partners/{id}")
    public ResponseEntity<Partner> getPartner(@PathVariable String id) {
        return partnerService.getPartner(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/partners")
    public ResponseEntity<Partner> createPartner(@RequestBody Partner partner) {
        return ResponseEntity.ok(partnerService.savePartner(partner));
    }

    @PutMapping("/partners/{id}")
    public ResponseEntity<Partner> updatePartner(@PathVariable String id, @RequestBody Partner partner) {
        partner.setId(id);
        return ResponseEntity.ok(partnerService.savePartner(partner));
    }

    @DeleteMapping("/partners/{id}")
    public ResponseEntity<Void> deletePartner(@PathVariable String id) {
        partnerService.deletePartner(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/partners/{id}/switch-environment")
    public ResponseEntity<Partner> switchEnvironment(@PathVariable String id, @RequestParam String environment) {
        return ResponseEntity.ok(partnerService.switchEnvironment(id, environment));
    }

    @GetMapping("/partners/{id}/validate")
    public ResponseEntity<Map<String, Object>> validatePartner(@PathVariable String id) {
        List<String> errors = partnerService.validatePartner(id);
        Map<String, Object> result = new HashMap<>();
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/partners/reload")
    public ResponseEntity<Map<String, Object>> reloadPartners() {
        partnerService.reloadConfigurations();
        return ResponseEntity.ok(Map.of("ok", true, "count", partnerService.getAllPartners().size()));
    }

    // =========================================================================
    // Version Discovery & Credentials
    // =========================================================================

    @PostMapping("/partners/{id}/discover")
    public ResponseEntity<Map<String, Object>> discoverVersions(@PathVariable String id) {
        try {
            OCPIClientService.OCPIResponse<List<OCPIClientService.VersionInfo>> response =
                    ocpiClient.getVersions(id);

            if (response.isSuccess()) {
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "versions", response.getData(),
                        "latencyMs", response.getLatencyMs()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "ok", false,
                        "error", response.getStatusMessage()
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/partners/{id}/discover-full")
    public ResponseEntity<Map<String, Object>> discoverFull(@PathVariable String id) {
        try {
            // Get versions
            OCPIClientService.OCPIResponse<List<OCPIClientService.VersionInfo>> versionsResponse =
                    ocpiClient.getVersions(id);

            if (!versionsResponse.isSuccess()) {
                return ResponseEntity.ok(Map.of("ok", false, "error", "Failed to get versions"));
            }

            Partner partner = partnerService.getPartner(id).orElseThrow();
            String targetVersion = partner.getVersion() != null ? partner.getVersion().getValue() : "2.2.1";

            OCPIClientService.VersionInfo versionInfo = versionsResponse.getData().stream()
                    .filter(v -> v.getVersion().equals(targetVersion))
                    .findFirst()
                    .orElse(null);

            if (versionInfo == null) {
                return ResponseEntity.ok(Map.of("ok", false, "error", "Version not found: " + targetVersion));
            }

            // Get version details
            OCPIClientService.OCPIResponse<OCPIClientService.VersionDetails> detailsResponse =
                    ocpiClient.getVersionDetails(id, versionInfo.getUrl());

            if (!detailsResponse.isSuccess()) {
                return ResponseEntity.ok(Map.of("ok", false, "error", "Failed to get version details"));
            }

            // Update endpoints
            Map<String, String> endpoints = new HashMap<>();
            for (OCPIClientService.Endpoint ep : detailsResponse.getData().getEndpoints()) {
                endpoints.put(ep.getIdentifier(), ep.getUrl());
            }
            partnerService.updateEndpoints(id, endpoints);
            partnerService.markSynced(id);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "version", targetVersion,
                    "endpoints", endpoints
            ));

        } catch (Exception e) {
            log.error("Full discovery failed for partner {}: {}", id, e.getMessage());
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // Test Scenarios
    // =========================================================================

    @GetMapping("/scenarios")
    public ResponseEntity<List<OCPITestScenario>> getScenarios() {
        return ResponseEntity.ok(testRepository.getAllScenarios());
    }

    @GetMapping("/scenarios/category/{category}")
    public ResponseEntity<List<OCPITestScenario>> getScenariosByCategory(@PathVariable String category) {
        return ResponseEntity.ok(testRepository.getScenariosByCategory(category));
    }

    @GetMapping("/scenarios/{id}")
    public ResponseEntity<OCPITestScenario> getScenario(@PathVariable String id) {
        return testRepository.getScenario(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/scenarios")
    public ResponseEntity<OCPITestScenario> createScenario(@RequestBody OCPITestScenario scenario) {
        return ResponseEntity.ok(testRepository.saveScenario(scenario));
    }

    @PutMapping("/scenarios/{id}")
    public ResponseEntity<OCPITestScenario> updateScenario(@PathVariable String id, @RequestBody OCPITestScenario scenario) {
        scenario.setId(id);
        return ResponseEntity.ok(testRepository.saveScenario(scenario));
    }

    @DeleteMapping("/scenarios/{id}")
    public ResponseEntity<Void> deleteScenario(@PathVariable String id) {
        testRepository.deleteScenario(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/scenarios/reload")
    public ResponseEntity<Map<String, Object>> reloadScenarios() {
        testRepository.reload();
        return ResponseEntity.ok(Map.of("ok", true, "count", testRepository.getAllScenarios().size()));
    }

    // =========================================================================
    // Test Execution
    // =========================================================================

    @PostMapping("/test/run")
    public ResponseEntity<OCPITestResult> runTest(
            @RequestParam String scenarioId,
            @RequestParam String partnerId) {
        OCPITestResult result = testRunner.executeScenario(scenarioId, partnerId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/test/run-async")
    public ResponseEntity<Map<String, Object>> runTestAsync(
            @RequestParam String scenarioId,
            @RequestParam String partnerId) {
        CompletableFuture.runAsync(() -> {
            try {
                testRunner.executeScenario(scenarioId, partnerId);
            } catch (Exception e) {
                log.error("Async test execution failed: {}", e.getMessage());
            }
        });
        return ResponseEntity.ok(Map.of("ok", true, "message", "Test started asynchronously"));
    }

    @PostMapping("/test/run-all")
    public ResponseEntity<Map<String, Object>> runAllTests(@RequestParam String partnerId) {
        List<OCPITestScenario> scenarios = testRepository.getAllScenarios();
        List<OCPITestResult> results = scenarios.stream()
                .map(s -> testRunner.executeScenario(s.getId(), partnerId))
                .toList();

        long passed = results.stream().filter(r -> r.getStatus() == OCPITestResult.ResultStatus.PASSED).count();
        long failed = results.stream().filter(r -> r.getStatus() == OCPITestResult.ResultStatus.FAILED).count();

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "total", results.size(),
                "passed", passed,
                "failed", failed,
                "results", results
        ));
    }

    @GetMapping("/test/active")
    public ResponseEntity<List<OCPITestResult>> getActiveTests() {
        return ResponseEntity.ok(List.copyOf(testRunner.getActiveTests()));
    }

    // =========================================================================
    // Test Results
    // =========================================================================

    @GetMapping("/results")
    public ResponseEntity<List<OCPITestResult>> getResults() {
        return ResponseEntity.ok(testRepository.getAllResults());
    }

    @GetMapping("/results/recent")
    public ResponseEntity<List<OCPITestResult>> getRecentResults(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(testRepository.getRecentResults(limit));
    }

    @GetMapping("/results/{id}")
    public ResponseEntity<OCPITestResult> getResult(@PathVariable String id) {
        return testRunner.getResult(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/results/partner/{partnerId}")
    public ResponseEntity<List<OCPITestResult>> getResultsForPartner(@PathVariable String partnerId) {
        return ResponseEntity.ok(testRunner.getResultsForPartner(partnerId));
    }

    @GetMapping("/results/scenario/{scenarioId}")
    public ResponseEntity<List<OCPITestResult>> getResultsForScenario(@PathVariable String scenarioId) {
        return ResponseEntity.ok(testRepository.getResultsForScenario(scenarioId));
    }

    @DeleteMapping("/results/{id}")
    public ResponseEntity<Void> deleteResult(@PathVariable String id) {
        testRepository.deleteResult(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/results/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupResults(@RequestParam(defaultValue = "10") int keepCount) {
        testRepository.cleanupOldResults(keepCount);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // =========================================================================
    // Quick Tests (Direct API calls)
    // =========================================================================

    @PostMapping("/quick-test/locations")
    public ResponseEntity<Map<String, Object>> quickTestLocations(@RequestParam String partnerId) {
        try {
            var response = ocpiClient.getLocations(partnerId, Map.of("limit", "10"));
            return ResponseEntity.ok(Map.of(
                    "ok", response.isSuccess(),
                    "statusCode", response.getStatusCode(),
                    "data", response.getData() != null ? response.getData() : List.of(),
                    "latencyMs", response.getLatencyMs()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/quick-test/sessions")
    public ResponseEntity<Map<String, Object>> quickTestSessions(@RequestParam String partnerId) {
        try {
            var response = ocpiClient.getSessions(partnerId, Map.of("limit", "10"));
            return ResponseEntity.ok(Map.of(
                    "ok", response.isSuccess(),
                    "statusCode", response.getStatusCode(),
                    "data", response.getData() != null ? response.getData() : List.of(),
                    "latencyMs", response.getLatencyMs()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/quick-test/cdrs")
    public ResponseEntity<Map<String, Object>> quickTestCdrs(@RequestParam String partnerId) {
        try {
            var response = ocpiClient.getCdrs(partnerId, Map.of("limit", "10"));
            return ResponseEntity.ok(Map.of(
                    "ok", response.isSuccess(),
                    "statusCode", response.getStatusCode(),
                    "data", response.getData() != null ? response.getData() : List.of(),
                    "latencyMs", response.getLatencyMs()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/quick-test/tokens")
    public ResponseEntity<Map<String, Object>> quickTestTokens(@RequestParam String partnerId) {
        try {
            var response = ocpiClient.getTokens(partnerId, Map.of("limit", "10"));
            return ResponseEntity.ok(Map.of(
                    "ok", response.isSuccess(),
                    "statusCode", response.getStatusCode(),
                    "data", response.getData() != null ? response.getData() : List.of(),
                    "latencyMs", response.getLatencyMs()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/quick-test/tariffs")
    public ResponseEntity<Map<String, Object>> quickTestTariffs(@RequestParam String partnerId) {
        try {
            var response = ocpiClient.getTariffs(partnerId, Map.of("limit", "10"));
            return ResponseEntity.ok(Map.of(
                    "ok", response.isSuccess(),
                    "statusCode", response.getStatusCode(),
                    "data", response.getData() != null ? response.getData() : List.of(),
                    "latencyMs", response.getLatencyMs()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    // =========================================================================
    // Request Builder (Postman-like)
    // =========================================================================

    /**
     * Generic request builder endpoint - like Postman for OCPI.
     * Supports GET, POST, PUT, PATCH, DELETE with custom headers and body.
     */
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> executeRequest(@RequestBody Map<String, Object> requestConfig) {
        try {
            String partnerId = (String) requestConfig.get("partnerId");
            String method = (String) requestConfig.getOrDefault("method", "GET");
            String url = (String) requestConfig.get("url");
            String endpoint = (String) requestConfig.get("endpoint"); // or use OCPI module endpoint
            String module = (String) requestConfig.get("module"); // e.g., "locations", "sessions"
            @SuppressWarnings("unchecked")
            Map<String, String> customHeaders = (Map<String, String>) requestConfig.getOrDefault("headers", Map.of());
            Object body = requestConfig.get("body");
            @SuppressWarnings("unchecked")
            Map<String, String> queryParams = (Map<String, String>) requestConfig.getOrDefault("queryParams", Map.of());

            // Build final URL
            String finalUrl = url;
            if (finalUrl == null && partnerId != null && module != null) {
                Partner partner = partnerService.getPartner(partnerId).orElseThrow(() ->
                        new IllegalArgumentException("Partner not found: " + partnerId));
                finalUrl = partner.getEndpoints().get(module);
                if (finalUrl == null) {
                    return ResponseEntity.ok(Map.of(
                            "ok", false,
                            "error", "No endpoint found for module: " + module + ". Run discovery first."
                    ));
                }
            }
            if (finalUrl == null && endpoint != null && partnerId != null) {
                Partner partner = partnerService.getPartner(partnerId).orElseThrow();
                finalUrl = partner.getBaseUrl() + endpoint;
            }
            if (finalUrl == null) {
                return ResponseEntity.ok(Map.of("ok", false, "error", "No URL specified"));
            }

            // Execute request
            var response = ocpiClient.executeRawRequest(
                    partnerId,
                    method,
                    finalUrl,
                    queryParams,
                    customHeaders,
                    body
            );

            return ResponseEntity.ok(Map.of(
                    "ok", response.isSuccess(),
                    "httpStatus", response.getHttpStatus(),
                    "ocpiStatus", response.getStatusCode(),
                    "ocpiMessage", response.getStatusMessage() != null ? response.getStatusMessage() : "",
                    "headers", response.getResponseHeaders() != null ? response.getResponseHeaders() : Map.of(),
                    "data", response.getData() != null ? response.getData() : Map.of(),
                    "rawBody", response.getRawBody() != null ? response.getRawBody() : "",
                    "latencyMs", response.getLatencyMs(),
                    "requestUrl", finalUrl,
                    "requestMethod", method
            ));

        } catch (Exception e) {
            log.error("Request execution failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "error", e.getMessage(),
                    "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Get OCPI request templates (predefined requests for each module)
     */
    @GetMapping("/request/templates")
    public ResponseEntity<List<Map<String, Object>>> getRequestTemplates() {
        List<Map<String, Object>> templates = List.of(
                // Versions
                Map.of(
                        "id", "versions",
                        "name", "Get OCPI Versions",
                        "description", "Retrieve list of supported OCPI versions",
                        "method", "GET",
                        "module", "versions",
                        "endpoint", "/ocpi/versions",
                        "category", "credentials"
                ),
                // Credentials
                Map.of(
                        "id", "credentials-get",
                        "name", "Get Credentials",
                        "description", "Retrieve credentials information",
                        "method", "GET",
                        "module", "credentials",
                        "category", "credentials"
                ),
                // Locations
                Map.of(
                        "id", "locations-list",
                        "name", "Get Locations List",
                        "description", "Retrieve list of charging locations",
                        "method", "GET",
                        "module", "locations",
                        "queryParams", Map.of("limit", "10", "offset", "0"),
                        "category", "locations"
                ),
                Map.of(
                        "id", "location-single",
                        "name", "Get Single Location",
                        "description", "Retrieve a specific location by ID",
                        "method", "GET",
                        "module", "locations",
                        "pathTemplate", "/{country_code}/{party_id}/{location_id}",
                        "category", "locations"
                ),
                Map.of(
                        "id", "evse-single",
                        "name", "Get EVSE",
                        "description", "Retrieve a specific EVSE",
                        "method", "GET",
                        "module", "locations",
                        "pathTemplate", "/{country_code}/{party_id}/{location_id}/{evse_uid}",
                        "category", "locations"
                ),
                // Sessions
                Map.of(
                        "id", "sessions-list",
                        "name", "Get Sessions List",
                        "description", "Retrieve list of charging sessions",
                        "method", "GET",
                        "module", "sessions",
                        "queryParams", Map.of("limit", "10", "date_from", "2024-01-01T00:00:00Z"),
                        "category", "sessions"
                ),
                Map.of(
                        "id", "session-single",
                        "name", "Get Single Session",
                        "description", "Retrieve a specific session",
                        "method", "GET",
                        "module", "sessions",
                        "pathTemplate", "/{country_code}/{party_id}/{session_id}",
                        "category", "sessions"
                ),
                // CDRs
                Map.of(
                        "id", "cdrs-list",
                        "name", "Get CDRs List",
                        "description", "Retrieve list of Charge Detail Records",
                        "method", "GET",
                        "module", "cdrs",
                        "queryParams", Map.of("limit", "10", "date_from", "2024-01-01T00:00:00Z"),
                        "category", "cdrs"
                ),
                // Tokens
                Map.of(
                        "id", "tokens-list",
                        "name", "Get Tokens List",
                        "description", "Retrieve list of authorization tokens",
                        "method", "GET",
                        "module", "tokens",
                        "queryParams", Map.of("limit", "10"),
                        "category", "tokens"
                ),
                Map.of(
                        "id", "token-authorize",
                        "name", "Real-time Authorization",
                        "description", "Authorize a token in real-time",
                        "method", "POST",
                        "module", "tokens",
                        "pathTemplate", "/{token_uid}/authorize",
                        "body", Map.of(
                                "location_id", "LOC001",
                                "type", "RFID"
                        ),
                        "category", "tokens"
                ),
                // Tariffs
                Map.of(
                        "id", "tariffs-list",
                        "name", "Get Tariffs List",
                        "description", "Retrieve list of tariffs",
                        "method", "GET",
                        "module", "tariffs",
                        "queryParams", Map.of("limit", "10"),
                        "category", "tariffs"
                ),
                // Commands
                Map.of(
                        "id", "command-start",
                        "name", "Start Session Command",
                        "description", "Send a remote start command",
                        "method", "POST",
                        "module", "commands",
                        "pathTemplate", "/START_SESSION",
                        "body", Map.of(
                                "response_url", "https://your-callback-url/ocpi/commands/START_SESSION/result",
                                "token", Map.of(
                                        "uid", "TOKEN001",
                                        "type", "RFID",
                                        "auth_id", "AUTH001",
                                        "valid", true,
                                        "whitelist", "ALLOWED"
                                ),
                                "location_id", "LOC001",
                                "evse_uid", "EVSE001"
                        ),
                        "category", "commands"
                ),
                Map.of(
                        "id", "command-stop",
                        "name", "Stop Session Command",
                        "description", "Send a remote stop command",
                        "method", "POST",
                        "module", "commands",
                        "pathTemplate", "/STOP_SESSION",
                        "body", Map.of(
                                "response_url", "https://your-callback-url/ocpi/commands/STOP_SESSION/result",
                                "session_id", "SESSION001"
                        ),
                        "category", "commands"
                ),
                Map.of(
                        "id", "command-reserve",
                        "name", "Reserve Now Command",
                        "description", "Make a reservation at a location",
                        "method", "POST",
                        "module", "commands",
                        "pathTemplate", "/RESERVE_NOW",
                        "body", Map.of(
                                "response_url", "https://your-callback-url/ocpi/commands/RESERVE_NOW/result",
                                "token", Map.of("uid", "TOKEN001", "type", "RFID"),
                                "expiry_date", "2024-12-31T23:59:59Z",
                                "reservation_id", "RES001",
                                "location_id", "LOC001"
                        ),
                        "category", "commands"
                ),
                Map.of(
                        "id", "command-unlock",
                        "name", "Unlock Connector Command",
                        "description", "Unlock a connector remotely",
                        "method", "POST",
                        "module", "commands",
                        "pathTemplate", "/UNLOCK_CONNECTOR",
                        "body", Map.of(
                                "response_url", "https://your-callback-url/ocpi/commands/UNLOCK_CONNECTOR/result",
                                "location_id", "LOC001",
                                "evse_uid", "EVSE001",
                                "connector_id", "1"
                        ),
                        "category", "commands"
                ),
                // Charging Profiles
                Map.of(
                        "id", "charging-profiles-set",
                        "name", "Set Charging Profile",
                        "description", "Set a charging profile for smart charging",
                        "method", "PUT",
                        "module", "chargingprofiles",
                        "pathTemplate", "/{session_id}",
                        "body", Map.of(
                                "response_url", "https://your-callback-url/ocpi/chargingprofiles/result",
                                "charging_profile", Map.of(
                                        "start_date_time", "2024-01-01T00:00:00Z",
                                        "charging_rate_unit", "W",
                                        "charging_profile_period", List.of(
                                                Map.of("start_period", 0, "limit", 11000.0)
                                        )
                                )
                        ),
                        "category", "chargingprofiles"
                )
        );
        return ResponseEntity.ok(templates);
    }

    /**
     * Get request history for a partner
     */
    @GetMapping("/request/history")
    public ResponseEntity<List<Map<String, Object>>> getRequestHistory(
            @RequestParam(required = false) String partnerId,
            @RequestParam(defaultValue = "20") int limit) {
        // For now, return empty list - could be implemented with persistence later
        return ResponseEntity.ok(List.of());
    }

    // =========================================================================
    // Global Environment Connection Tests
    // =========================================================================

    /**
     * Test OCPI connection to a specific URL with credentials.
     * Used by ConnectionPanel to verify OCPI connectivity.
     */
    @PostMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testOcpiConnection(@RequestBody Map<String, Object> request) {
        try {
            String baseUrl = (String) request.get("baseUrl");
            String tokenType = (String) request.getOrDefault("tokenType", "token");
            String token = (String) request.get("token");
            @SuppressWarnings("unchecked")
            Map<String, String> cognitoConfig = (Map<String, String>) request.get("cognitoConfig");

            if (baseUrl == null || baseUrl.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "status", "error",
                        "error", "URL de base requise"
                ));
            }

            // Get token if using Cognito
            String authToken = token;
            if ("cognito".equals(tokenType) && cognitoConfig != null) {
                try {
                    authToken = getCognitoToken(cognitoConfig);
                } catch (Exception e) {
                    return ResponseEntity.ok(Map.of(
                            "status", "error",
                            "error", "Echec authentification Cognito: " + e.getMessage()
                    ));
                }
            }

            // Try to connect to versions endpoint
            String versionsUrl = baseUrl.endsWith("/") ? baseUrl + "versions" : baseUrl + "/versions";
            long startTime = System.currentTimeMillis();

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(versionsUrl))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Accept", "application/json");

            if (authToken != null && !authToken.isEmpty()) {
                reqBuilder.header("Authorization", "Token " + authToken);
            }

            java.net.http.HttpResponse<String> response = client.send(
                    reqBuilder.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );

            long latencyMs = System.currentTimeMillis() - startTime;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ResponseEntity.ok(Map.of(
                        "status", "connected",
                        "httpStatus", response.statusCode(),
                        "latencyMs", latencyMs,
                        "url", versionsUrl
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "status", "error",
                        "httpStatus", response.statusCode(),
                        "error", "HTTP " + response.statusCode(),
                        "latencyMs", latencyMs
                ));
            }

        } catch (java.net.http.HttpTimeoutException e) {
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "error", "Timeout - serveur ne repond pas"
            ));
        } catch (Exception e) {
            log.error("OCPI connection test failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Test WebSocket OCPP connection.
     * Used by ConnectionPanel to verify WebSocket connectivity.
     */
    @PostMapping("/test-websocket")
    public ResponseEntity<Map<String, Object>> testWebSocketConnection(@RequestBody Map<String, Object> request) {
        try {
            String wsUrl = (String) request.get("wsUrl");
            String protocol = (String) request.getOrDefault("protocol", "ocpp1.6");

            if (wsUrl == null || wsUrl.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "status", "error",
                        "error", "URL WebSocket requise"
                ));
            }

            // Validate URL format
            if (!wsUrl.startsWith("ws://") && !wsUrl.startsWith("wss://")) {
                return ResponseEntity.ok(Map.of(
                        "status", "error",
                        "error", "URL doit commencer par ws:// ou wss://"
                ));
            }

            // Count active WebSocket connections (from SessionService if available)
            int activeConnections = 0;
            try {
                // Try to get active connections count from application context
                // For now, we'll just check if the URL is reachable
                java.net.URI uri = new java.net.URI(wsUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                if (port == -1) {
                    port = wsUrl.startsWith("wss://") ? 443 : 80;
                }

                // Simple TCP connectivity check
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress(host, port), 5000);
                    return ResponseEntity.ok(Map.of(
                            "status", "connected",
                            "activeConnections", activeConnections,
                            "protocol", protocol,
                            "url", wsUrl
                    ));
                }

            } catch (java.net.SocketTimeoutException e) {
                return ResponseEntity.ok(Map.of(
                        "status", "error",
                        "error", "Timeout - serveur WebSocket ne repond pas"
                ));
            } catch (java.net.ConnectException e) {
                return ResponseEntity.ok(Map.of(
                        "status", "error",
                        "error", "Connexion refusee"
                ));
            }

        } catch (Exception e) {
            log.error("WebSocket connection test failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Helper method to get Cognito OAuth2 token.
     */
    private String getCognitoToken(Map<String, String> cognitoConfig) throws Exception {
        String tokenUrl = cognitoConfig.get("tokenUrl");
        String clientId = cognitoConfig.get("clientId");
        String clientSecret = cognitoConfig.get("clientSecret");

        if (tokenUrl == null || clientId == null || clientSecret == null) {
            throw new IllegalArgumentException("Configuration Cognito incomplete");
        }

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        String credentials = java.util.Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(tokenUrl))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(java.time.Duration.ofSeconds(15))
                .build();

        java.net.http.HttpResponse<String> response = client.send(
                request,
                java.net.http.HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("Cognito auth failed: HTTP " + response.statusCode());
        }

        // Parse JSON response to extract access_token
        String body = response.body();
        // Simple JSON parsing (assumes access_token is present)
        int tokenStart = body.indexOf("\"access_token\":\"") + 16;
        int tokenEnd = body.indexOf("\"", tokenStart);
        if (tokenStart < 16 || tokenEnd < 0) {
            throw new RuntimeException("Token non trouve dans la reponse Cognito");
        }

        return body.substring(tokenStart, tokenEnd);
    }

    // =========================================================================
    // Dashboard Data
    // =========================================================================

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        List<Partner> partners = partnerService.getAllPartners();
        List<OCPITestResult> recentResults = testRepository.getRecentResults(50);

        long totalTests = recentResults.size();
        long passedTests = recentResults.stream()
                .filter(r -> r.getStatus() == OCPITestResult.ResultStatus.PASSED).count();
        long failedTests = recentResults.stream()
                .filter(r -> r.getStatus() == OCPITestResult.ResultStatus.FAILED).count();

        // Per-partner stats
        Map<String, Map<String, Object>> partnerStats = new HashMap<>();
        for (Partner partner : partners) {
            List<OCPITestResult> partnerResults = recentResults.stream()
                    .filter(r -> partner.getId().equals(r.getPartnerId()))
                    .toList();

            partnerStats.put(partner.getId(), Map.of(
                    "name", partner.getName(),
                    "active", partner.isActive(),
                    "totalTests", partnerResults.size(),
                    "passed", partnerResults.stream()
                            .filter(r -> r.getStatus() == OCPITestResult.ResultStatus.PASSED).count(),
                    "failed", partnerResults.stream()
                            .filter(r -> r.getStatus() == OCPITestResult.ResultStatus.FAILED).count(),
                    "lastSync", partner.getLastSync() != null ? partner.getLastSync().toString() : "never"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "partners", Map.of(
                        "total", partners.size(),
                        "active", partners.stream().filter(Partner::isActive).count()
                ),
                "scenarios", Map.of(
                        "total", testRepository.getAllScenarios().size()
                ),
                "tests", Map.of(
                        "total", totalTests,
                        "passed", passedTests,
                        "failed", failedTests,
                        "successRate", totalTests > 0 ? (double) passedTests / totalTests * 100 : 0
                ),
                "partnerStats", partnerStats,
                "activeTests", testRunner.getActiveTests().size()
        ));
    }
}
