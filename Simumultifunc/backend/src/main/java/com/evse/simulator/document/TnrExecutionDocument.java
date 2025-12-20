package com.evse.simulator.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Document MongoDB pour les executions de scenarios TNR.
 */
@Document(collection = "tnrExecutions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TnrExecutionDocument {

    @Id
    private String id;

    @Indexed
    private String scenarioId;

    private String scenarioName;

    @Indexed
    private String status;

    @Indexed
    private LocalDateTime executedAt;

    private LocalDateTime completedAt;

    private long durationMs;

    // =========================================================================
    // Execution context
    // =========================================================================

    private String triggeredBy;
    private Map<String, Object> parameters;

    // =========================================================================
    // Results
    // =========================================================================

    private int totalSteps;
    private int passedSteps;
    private int failedSteps;
    private int skippedSteps;

    private List<StepResultEmbedded> stepResults;

    private String errorMessage;
    private String errorStackTrace;

    // =========================================================================
    // Embedded documents
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StepResultEmbedded {
        private int order;
        private String name;
        private String type;
        private String action;
        private String status;
        private long durationMs;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Object request;
        private Object response;
        private List<AssertionResultEmbedded> assertionResults;
        private String errorMessage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssertionResultEmbedded {
        private String type;
        private String path;
        private String operator;
        private Object expected;
        private Object actual;
        private boolean passed;
        private String message;
    }

    @CreatedDate
    private LocalDateTime createdAt;
}
