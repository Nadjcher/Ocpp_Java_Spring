package com.evse.simulator.scheduler.model;

import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Tâche planifiée.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTask {
    private String id;
    private String name;
    private String description;
    private String taskType;
    private String actionType;
    private String scheduleType;
    private String cronExpression;
    private String status;
    private boolean enabled;
    private Map<String, Object> parameters;
    private ActionConfig actionConfig;
    private Integer intervalSeconds;
    private Instant runAt;
    private String timezone;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant nextExecutionAt;
    private Instant nextRunAt;
    private Instant lastExecutionAt;
    private Instant lastRunAt;
    private String lastExecutionStatus;
    private String lastRunStatus;
    private String lastRunError;
    private int executionCount;
    private int runCount;
    private int successCount;
    private int failCount;
    private int failureCount;

    /**
     * Gets schedule type, falling back to cronExpression if set.
     */
    public String getScheduleType() {
        if (scheduleType != null) return scheduleType;
        return cronExpression != null ? "cron" : "once";
    }

    /**
     * Gets next run at, falling back to nextExecutionAt.
     */
    public Instant getNextRunAt() {
        return nextRunAt != null ? nextRunAt : nextExecutionAt;
    }

    /**
     * Sets next run at and nextExecutionAt.
     */
    public void setNextRunAt(Instant time) {
        this.nextRunAt = time;
        this.nextExecutionAt = time;
    }

    /**
     * Gets last run at, falling back to lastExecutionAt.
     */
    public Instant getLastRunAt() {
        return lastRunAt != null ? lastRunAt : lastExecutionAt;
    }

    /**
     * Gets last run status, falling back to lastExecutionStatus.
     */
    public String getLastRunStatus() {
        return lastRunStatus != null ? lastRunStatus : lastExecutionStatus;
    }

    /**
     * Gets run count, falling back to executionCount.
     */
    public int getRunCount() {
        return runCount > 0 ? runCount : executionCount;
    }

    /**
     * Sets run count and executionCount.
     */
    public void setRunCount(int count) {
        this.runCount = count;
        this.executionCount = count;
    }

    /**
     * Gets fail count, falling back to failureCount.
     */
    public int getFailCount() {
        return failCount > 0 ? failCount : failureCount;
    }

    /**
     * Sets fail count and failureCount.
     */
    public void setFailCount(int count) {
        this.failCount = count;
        this.failureCount = count;
    }
}
