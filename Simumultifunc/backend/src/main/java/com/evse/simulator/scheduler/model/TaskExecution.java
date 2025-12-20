package com.evse.simulator.scheduler.model;

import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Exécution d'une tâche planifiée.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecution {
    private String id;
    private String taskId;
    private String taskName;
    private String status;
    private Instant startTime;
    private Instant startedAt;
    private Instant endTime;
    private Instant completedAt;
    private long durationMs;
    private String result;
    private String output;
    private String error;
    private String errorMessage;
    private String triggeredBy;
    private Map<String, Object> outputData;
    private int retryCount;

    /**
     * Gets started at time, falling back to startTime.
     */
    public Instant getStartedAt() {
        return startedAt != null ? startedAt : startTime;
    }

    /**
     * Gets completed at time, falling back to endTime.
     */
    public Instant getCompletedAt() {
        return completedAt != null ? completedAt : endTime;
    }

    /**
     * Gets error, falling back to errorMessage.
     */
    public String getError() {
        return error != null ? error : errorMessage;
    }
}
