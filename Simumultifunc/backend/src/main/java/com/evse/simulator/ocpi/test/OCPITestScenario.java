package com.evse.simulator.ocpi.test;

import com.evse.simulator.ocpi.OCPIModule;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * OCPI Test Scenario definition.
 * Describes a sequence of test steps to execute against a partner.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OCPITestScenario {

    private String id;
    private String name;
    private String description;
    private String category;          // credentials, locations, sessions, commands, full-flow
    private List<String> tags;
    private List<OCPIModule> requiredModules;
    private List<TestStep> steps;
    private Map<String, Object> variables;    // Shared variables between steps
    private boolean stopOnFailure;

    /**
     * Single test step.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestStep {
        private String id;
        private String name;
        private String description;
        private StepType type;
        private OCPIModule module;
        private String method;            // GET, POST, PUT, PATCH, DELETE
        private String endpoint;          // URL pattern with {variables}
        private Object requestBody;       // Request body template
        private Map<String, String> headers;
        private List<Assertion> assertions;
        private List<Extraction> extractions;   // Extract values for next steps
        private int timeoutMs;
        private int retries;
        private String condition;         // SpEL condition for conditional execution
        private String dependsOn;         // Step ID this depends on
    }

    /**
     * Step type.
     */
    public enum StepType {
        HTTP_REQUEST,      // Standard HTTP request
        VERSION_DISCOVERY, // Get versions + details
        CREDENTIALS_HANDSHAKE, // Full credentials exchange
        WAIT,              // Wait for condition or timeout
        WEBHOOK_WAIT,      // Wait for async callback
        EVSE_SIMULATE      // Simulate EVSE action (via OCPP)
    }

    /**
     * Response assertion.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assertion {
        private String name;
        private AssertionType type;
        private String path;          // JSONPath expression
        private Object expected;
        private String operator;      // eq, ne, gt, lt, contains, matches, exists
        private boolean critical;     // If false, failure is warning only
    }

    public enum AssertionType {
        HTTP_STATUS,       // Check HTTP status code
        OCPI_STATUS,       // Check OCPI status_code
        JSON_PATH,         // Check value at JSONPath
        HEADER,            // Check response header
        LATENCY,           // Check response time
        SCHEMA             // Validate against JSON schema
    }

    /**
     * Value extraction for use in subsequent steps.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Extraction {
        private String variableName;
        private String jsonPath;
        private String header;
        private String defaultValue;
    }
}
