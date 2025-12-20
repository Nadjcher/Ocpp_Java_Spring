package com.evse.simulator.ocpi.test;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * OCPI Test execution result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OCPITestResult {

    private String id;
    private String scenarioId;
    private String scenarioName;
    private String partnerId;
    private String partnerName;
    private String environment;
    private Instant startTime;
    private Instant endTime;
    private long durationMs;
    private ResultStatus status;
    private List<StepResult> stepResults;
    private Summary summary;
    private Map<String, Object> variables;    // Final variable values

    /**
     * Overall test status.
     */
    public enum ResultStatus {
        PENDING,
        RUNNING,
        PASSED,
        FAILED,
        SKIPPED,
        ERROR
    }

    /**
     * Result of a single step.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResult {
        private String stepId;
        private String stepName;
        private ResultStatus status;
        private Instant startTime;
        private Instant endTime;
        private long durationMs;
        private RequestInfo request;
        private ResponseInfo response;
        private List<AssertionResult> assertionResults;
        private Map<String, Object> extractedValues;
        private String errorMessage;
        private String errorDetails;
    }

    /**
     * HTTP request information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestInfo {
        private String method;
        private String url;
        private Map<String, String> headers;
        private String body;
    }

    /**
     * HTTP response information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseInfo {
        private int httpStatus;
        private int ocpiStatus;
        private String ocpiMessage;
        private Map<String, String> headers;
        private String body;
        private long latencyMs;
    }

    /**
     * Single assertion result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssertionResult {
        private String name;
        private boolean passed;
        private String expected;
        private String actual;
        private String message;
        private boolean critical;
    }

    /**
     * Test summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int totalSteps;
        private int passedSteps;
        private int failedSteps;
        private int skippedSteps;
        private int totalAssertions;
        private int passedAssertions;
        private int failedAssertions;
        private int warningAssertions;
        private double successRate;
        private long avgLatencyMs;
        private long maxLatencyMs;
        private long minLatencyMs;
    }
}
