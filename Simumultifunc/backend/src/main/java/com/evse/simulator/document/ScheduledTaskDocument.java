package com.evse.simulator.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Document MongoDB pour les taches planifiees.
 */
@Document(collection = "scheduledTasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledTaskDocument {

    @Id
    private String id;

    private String name;
    private String description;

    // Schedule configuration
    private String scheduleType;
    private String cronExpression;
    private Integer intervalSeconds;
    private LocalDateTime runAt;

    @Builder.Default
    private String timezone = "Europe/Paris";

    // Action
    private String actionType;
    private Map<String, Object> actionConfig;

    // State
    @Indexed
    @Builder.Default
    private boolean enabled = true;

    private LocalDateTime lastRunAt;

    @Indexed
    private LocalDateTime nextRunAt;

    private String lastRunStatus;
    private String lastRunError;

    // Statistics
    @Builder.Default
    private int runCount = 0;

    @Builder.Default
    private int failCount = 0;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
