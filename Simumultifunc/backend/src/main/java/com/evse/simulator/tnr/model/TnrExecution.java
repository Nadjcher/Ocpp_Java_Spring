package com.evse.simulator.tnr.model;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Exécution d'un scénario TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrExecution {
    private String id;
    private String scenarioId;
    private String scenarioName;
    private TnrConfig config;
    private String status;
    private Instant startTime;
    private Instant startedAt;
    private Instant endTime;
    private long durationMs;
    private int totalSteps;
    private int passedSteps;
    private int failedSteps;
    private List<TnrEvent> events;
    private List<TnrStepResult> stepResults;
    private TnrComparisonResult comparison;
    private Map<String, Object> metadata;
    private String signature;
    private String criticalSignature;

    /**
     * Gets start time (alias for startTime).
     */
    public Instant getStartedAt() {
        return startedAt != null ? startedAt : startTime;
    }

    /**
     * Gets event count.
     */
    public int getEventCount() {
        return events != null ? events.size() : 0;
    }

    /**
     * Completes the execution.
     */
    public void complete() {
        this.endTime = Instant.now();
        if (startTime != null) {
            this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
        this.status = "COMPLETED";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TnrStepResult {
        private int stepIndex;
        private String stepType;
        private String action;
        private String status;
        private long durationMs;
        private Object expected;
        private Object actual;
        private TnrComparisonResult comparison;
        private String errorMessage;
    }
}
