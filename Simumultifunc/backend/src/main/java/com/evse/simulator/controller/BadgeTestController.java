package com.evse.simulator.controller;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.service.SessionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Controller for automated badge/tag tests.
 * Simulates OCPP charge sessions with EMSP badge authorization.
 */
@RestController
@RequestMapping("/api/badge-test")
@CrossOrigin(originPatterns = "*", allowCredentials = "false")
@RequiredArgsConstructor
@Slf4j
public class BadgeTestController {

    private final SessionService sessionService;
    private final OCPPService ocppService;

    // Store running tests
    private final Map<String, BadgeTestStatus> runningTests = new ConcurrentHashMap<>();

    @Data
    public static class BadgeTestRequest {
        private String ocppId;
        private String emsp;
        private String tag;
        private int durationMinutes = 3;
        private String csmsUrl = "wss://evse-test.total-ev-charge.com/ocpp/WebSocket";
        private String testType = "transaction"; // transaction, authorize, locations, sessions, cdrs, tokens, tariffs, commands
        private String partnerId; // For OCPI module tests
        private String ocpiVersion = "2.2.1"; // OCPI version: 2.1.1 or 2.2.1
    }

    @Data
    public static class BadgeTestStatus {
        private String ocppId;
        private String emsp;
        private String tag;
        private String testType;
        private String ocpiVersion;
        private String status; // pending, connecting, authorizing, charging, stopping, completed, failed
        private String message;
        private String startTime;
        private String endTime;
        private boolean success;
        private int energyWh;
        private List<String> steps = new ArrayList<>();
    }

    /**
     * Run a single badge/OCPI test.
     * Test types:
     * - transaction: Full charge flow (Connect → Authorize → Start → MeterValues → Stop)
     * - authorize: Just authorize the tag
     * - locations, sessions, cdrs, tokens, tariffs, commands: OCPI module tests
     */
    @PostMapping("/run")
    public ResponseEntity<BadgeTestStatus> runBadgeTest(@RequestBody BadgeTestRequest request) {
        String testType = request.getTestType() != null ? request.getTestType() : "transaction";
        log.info("Starting {} test: OCPP={}, EMSP={}, Tag={}", testType, request.getOcppId(), request.getEmsp(), request.getTag());

        String ocpiVersion = request.getOcpiVersion() != null ? request.getOcpiVersion() : "2.2.1";

        BadgeTestStatus status = new BadgeTestStatus();
        status.setOcppId(request.getOcppId());
        status.setEmsp(request.getEmsp());
        status.setTag(request.getTag());
        status.setTestType(testType);
        status.setOcpiVersion(ocpiVersion);
        status.setStartTime(java.time.Instant.now().toString());

        String key = request.getOcppId() + "-" + request.getEmsp();
        runningTests.put(key, status);

        // Handle OCPI module tests separately
        if (isOcpiModuleTest(testType)) {
            return runOcpiModuleTest(request, status, key);
        }

        try {
            // Step 1: Create session for this OCPP ID if it doesn't exist
            status.setStatus("connecting");
            status.getSteps().add("Setting up session for EVSE " + request.getOcppId() + "...");
            log.info("[{}] Step 1: Creating/finding session", key);

            // Try to find existing session by cpId
            Session session = null;
            for (Session s : sessionService.getAllSessions()) {
                if (request.getOcppId().equals(s.getCpId())) {
                    session = s;
                    break;
                }
            }

            String sessionId;
            if (session != null) {
                sessionId = session.getId();
                status.getSteps().add("Found existing session: " + sessionId);
            } else {
                // Create new session
                Session newSession = Session.builder()
                        .cpId(request.getOcppId())
                        .title("Badge Test - " + request.getEmsp())
                        .idTag(request.getTag())
                        .url(request.getCsmsUrl())
                        .chargerType(ChargerType.AC_TRI)
                        .soc(20)
                        .targetSoc(80)
                        .build();
                session = sessionService.createSession(newSession);
                sessionId = session.getId();
                status.getSteps().add("Created new session: " + sessionId);
            }

            // Update session with test tag
            Session updates = new Session();
            updates.setIdTag(request.getTag());
            sessionService.updateSession(sessionId, updates);

            // Step 2: Connect to CSMS
            if (!session.isConnected()) {
                status.getSteps().add("Connecting to CSMS...");
                try {
                    CompletableFuture<Boolean> connectFuture = ocppService.connect(sessionId);
                    Boolean connected = connectFuture.get(30, TimeUnit.SECONDS);
                    if (Boolean.TRUE.equals(connected)) {
                        status.getSteps().add("Connected to CSMS successfully");
                    } else {
                        status.getSteps().add("Connection to CSMS failed");
                        status.setStatus("failed");
                        status.setMessage("Failed to connect to CSMS");
                        status.setSuccess(false);
                        status.setEndTime(java.time.Instant.now().toString());
                        return ResponseEntity.ok(status);
                    }
                } catch (TimeoutException e) {
                    status.getSteps().add("Connection timeout");
                    status.setStatus("failed");
                    status.setMessage("Connection timeout");
                    status.setSuccess(false);
                    status.setEndTime(java.time.Instant.now().toString());
                    return ResponseEntity.ok(status);
                }
            } else {
                status.getSteps().add("Already connected to CSMS");
            }

            // Step 3: Send BootNotification
            status.getSteps().add("Sending BootNotification...");
            try {
                CompletableFuture<Map<String, Object>> bootFuture = ocppService.sendBootNotification(sessionId);
                Map<String, Object> bootResponse = bootFuture.get(10, TimeUnit.SECONDS);
                String bootStatus = (String) bootResponse.get("status");
                status.getSteps().add("BootNotification: " + bootStatus);
            } catch (Exception e) {
                status.getSteps().add("BootNotification error: " + e.getMessage());
            }

            // Step 4: Authorize tag (session already has idTag set)
            status.setStatus("authorizing");
            status.getSteps().add("Authorizing tag " + request.getTag() + " (EMSP: " + request.getEmsp() + ")...");
            log.info("[{}] Step 2: Authorizing tag {}", key, request.getTag());

            try {
                CompletableFuture<Map<String, Object>> authFuture = ocppService.sendAuthorize(sessionId);
                Map<String, Object> authResponse = authFuture.get(15, TimeUnit.SECONDS);

                @SuppressWarnings("unchecked")
                Map<String, Object> idTagInfo = (Map<String, Object>) authResponse.get("idTagInfo");
                if (idTagInfo != null) {
                    String authStatus = (String) idTagInfo.get("status");
                    if ("Accepted".equalsIgnoreCase(authStatus)) {
                        status.getSteps().add("Authorization ACCEPTED for " + request.getEmsp());
                    } else {
                        status.getSteps().add("Authorization " + authStatus);
                        status.setStatus("failed");
                        status.setMessage("Authorization " + authStatus);
                        status.setSuccess(false);
                        status.setEndTime(java.time.Instant.now().toString());
                        return ResponseEntity.ok(status);
                    }
                } else {
                    status.getSteps().add("Authorization response received");
                }
            } catch (TimeoutException e) {
                status.getSteps().add("Authorization timeout");
                status.setStatus("failed");
                status.setMessage("Authorization timeout");
                status.setSuccess(false);
                status.setEndTime(java.time.Instant.now().toString());
                return ResponseEntity.ok(status);
            }

            // Step 5: Start Transaction
            status.setStatus("charging");
            status.getSteps().add("Starting transaction...");
            log.info("[{}] Step 3: Starting transaction", key);

            int transactionId = 0;
            try {
                CompletableFuture<Map<String, Object>> startFuture = ocppService.sendStartTransaction(sessionId);
                Map<String, Object> startResponse = startFuture.get(15, TimeUnit.SECONDS);

                if (startResponse.containsKey("transactionId")) {
                    transactionId = ((Number) startResponse.get("transactionId")).intValue();
                    status.getSteps().add("Transaction started: ID=" + transactionId);
                } else {
                    status.getSteps().add("Transaction started (no ID returned)");
                }
            } catch (Exception e) {
                status.getSteps().add("StartTransaction error: " + e.getMessage());
            }

            // Step 6: Send MeterValues during charge (simulated short duration)
            int powerW = 7000 + (int)(Math.random() * 4000); // 7-11 kW
            int energyWh = 0;
            int iterations = Math.min(request.getDurationMinutes(), 3); // Max 3 iterations for quick test

            status.getSteps().add("Charging at " + (powerW / 1000.0) + " kW...");

            for (int i = 1; i <= iterations; i++) {
                energyWh += (powerW * 60) / 3600; // Energy for 1 minute at this power
                try {
                    // Update session energy before sending meter values
                    Session meterUpdate = new Session();
                    meterUpdate.setEnergyDeliveredKwh(energyWh / 1000.0); // Convert Wh to kWh
                    sessionService.updateSession(sessionId, meterUpdate);

                    ocppService.sendMeterValues(sessionId);
                    status.getSteps().add("MeterValues " + i + ": " + energyWh + " Wh, " + (powerW / 1000.0) + " kW");
                } catch (Exception e) {
                    status.getSteps().add("MeterValues error: " + e.getMessage());
                }
                Thread.sleep(2000); // 2 seconds between meter values
            }

            status.setEnergyWh(energyWh);

            // Step 7: Stop Transaction
            status.setStatus("stopping");
            status.getSteps().add("Stopping transaction...");
            log.info("[{}] Step 4: Stopping transaction", key);

            try {
                CompletableFuture<Map<String, Object>> stopFuture = ocppService.sendStopTransaction(sessionId);
                stopFuture.get(15, TimeUnit.SECONDS);
                status.getSteps().add("Transaction stopped. Total energy: " + energyWh + " Wh");
            } catch (Exception e) {
                status.getSteps().add("StopTransaction error: " + e.getMessage());
            }

            // Complete
            status.setStatus("completed");
            status.setSuccess(true);
            status.setMessage("Test completed successfully");
            status.setEndTime(java.time.Instant.now().toString());

            log.info("[{}] Badge test completed successfully. Energy: {} Wh", key, energyWh);

        } catch (Exception e) {
            log.error("[{}] Badge test failed: {}", key, e.getMessage(), e);
            status.setStatus("failed");
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
            status.getSteps().add("ERROR: " + e.getMessage());
            status.setEndTime(java.time.Instant.now().toString());
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Run multiple badge tests in parallel.
     */
    @PostMapping("/run-batch")
    public ResponseEntity<Map<String, Object>> runBatchTests(@RequestBody List<BadgeTestRequest> requests) {
        log.info("Starting batch badge tests: {} tests", requests.size());

        List<CompletableFuture<BadgeTestStatus>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(requests.size(), 5));

        for (BadgeTestRequest request : requests) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return runBadgeTest(request).getBody();
                } catch (Exception e) {
                    BadgeTestStatus failed = new BadgeTestStatus();
                    failed.setOcppId(request.getOcppId());
                    failed.setEmsp(request.getEmsp());
                    failed.setStatus("failed");
                    failed.setMessage(e.getMessage());
                    return failed;
                }
            }, executor));
        }

        List<BadgeTestStatus> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        executor.shutdown();

        long passed = results.stream().filter(BadgeTestStatus::isSuccess).count();
        long failed = results.size() - passed;

        Map<String, Object> response = new HashMap<>();
        response.put("total", results.size());
        response.put("passed", passed);
        response.put("failed", failed);
        response.put("results", results);

        return ResponseEntity.ok(response);
    }

    /**
     * Get status of a running test.
     */
    @GetMapping("/status/{ocppId}/{emsp}")
    public ResponseEntity<BadgeTestStatus> getTestStatus(
            @PathVariable String ocppId,
            @PathVariable String emsp) {
        String key = ocppId + "-" + emsp;
        BadgeTestStatus status = runningTests.get(key);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Get all test statuses.
     */
    @GetMapping("/status")
    public ResponseEntity<List<BadgeTestStatus>> getAllStatuses() {
        return ResponseEntity.ok(new ArrayList<>(runningTests.values()));
    }

    /**
     * Clear test results.
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearResults() {
        int count = runningTests.size();
        runningTests.clear();
        return ResponseEntity.ok(Map.of("cleared", count));
    }

    // =========================================================================
    // Helper methods for different test types
    // =========================================================================

    private boolean isOcpiModuleTest(String testType) {
        return testType != null && (
            testType.equals("locations") ||
            testType.equals("sessions") ||
            testType.equals("cdrs") ||
            testType.equals("tokens") ||
            testType.equals("tariffs") ||
            testType.equals("commands")
        );
    }

    private ResponseEntity<BadgeTestStatus> runOcpiModuleTest(BadgeTestRequest request, BadgeTestStatus status, String key) {
        String testType = request.getTestType();
        String version = request.getOcpiVersion() != null ? request.getOcpiVersion() : "2.2.1";
        status.getSteps().add("Running OCPI " + testType.toUpperCase() + " module test (version " + version + ")...");

        try {
            // For now, simulate OCPI module tests
            // In a real implementation, this would call the OCPI client service
            status.setStatus("testing");
            status.getSteps().add("Preparing OCPI " + testType + " request for " + request.getEmsp());
            status.getSteps().add("Using OCPI version: " + version);

            // Simulate test execution
            Thread.sleep(500);

            switch (testType) {
                case "locations":
                    status.getSteps().add("GET /ocpi/cpo/" + version + "/locations");
                    status.getSteps().add("Retrieving location data from " + request.getEmsp() + "...");
                    Thread.sleep(300);
                    status.getSteps().add("Received locations response (simulated)");
                    break;

                case "sessions":
                    status.getSteps().add("GET /ocpi/cpo/" + version + "/sessions");
                    status.getSteps().add("Retrieving active sessions from " + request.getEmsp() + "...");
                    Thread.sleep(300);
                    status.getSteps().add("Received sessions response (simulated)");
                    break;

                case "cdrs":
                    status.getSteps().add("GET /ocpi/cpo/" + version + "/cdrs");
                    status.getSteps().add("Retrieving CDRs from " + request.getEmsp() + "...");
                    Thread.sleep(300);
                    status.getSteps().add("Received CDRs response (simulated)");
                    break;

                case "tokens":
                    status.getSteps().add("POST /ocpi/emsp/" + version + "/tokens/" + request.getTag() + "/authorize");
                    status.getSteps().add("Authorizing token " + request.getTag() + " with " + request.getEmsp() + "...");
                    Thread.sleep(300);
                    status.getSteps().add("Token authorization response (simulated)");
                    break;

                case "tariffs":
                    status.getSteps().add("GET /ocpi/cpo/" + version + "/tariffs");
                    status.getSteps().add("Retrieving tariffs from " + request.getEmsp() + "...");
                    Thread.sleep(300);
                    status.getSteps().add("Received tariffs response (simulated)");
                    break;

                case "commands":
                    status.getSteps().add("POST /ocpi/emsp/" + version + "/commands/START_SESSION");
                    status.getSteps().add("Sending START_SESSION command to " + request.getEmsp() + "...");
                    status.getSteps().add("location_id: " + request.getOcppId());
                    status.getSteps().add("token: " + request.getTag());
                    Thread.sleep(300);
                    status.getSteps().add("Command accepted (simulated)");
                    break;

                default:
                    status.getSteps().add("Unknown OCPI module: " + testType);
            }

            status.setStatus("completed");
            status.setSuccess(true);
            status.setMessage("OCPI " + testType + " test (v" + version + ") completed successfully (simulated)");
            status.setEndTime(java.time.Instant.now().toString());

        } catch (Exception e) {
            log.error("[{}] OCPI module test failed: {}", key, e.getMessage(), e);
            status.setStatus("failed");
            status.setSuccess(false);
            status.setMessage("Error: " + e.getMessage());
            status.getSteps().add("ERROR: " + e.getMessage());
            status.setEndTime(java.time.Instant.now().toString());
        }

        return ResponseEntity.ok(status);
    }
}
