package com.evse.simulator.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Document MongoDB pour les scenarios de test TNR.
 */
@Document(collection = "tnrScenarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TnrScenarioDocument {

    @Id
    private String id;

    @Indexed
    private String name;

    private String description;

    @Indexed
    private String category;

    private List<String> tags;
    private String author;
    private String version;

    // Configuration
    private Map<String, Object> config;

    // Steps (BSON array)
    private List<TnrStepEmbedded> steps;

    // State
    @Indexed
    @Builder.Default
    private String status = "PENDING";

    @Indexed
    @Builder.Default
    private boolean active = true;

    // Last result
    private TnrResultEmbedded lastResult;
    private LocalDateTime lastRunAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // =========================================================================
    // Embedded documents
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TnrStepEmbedded {
        private int order;
        private String name;
        private String type;
        private String action;
        private Map<String, Object> payload;
        private Long delayMs;
        private Long timeoutMs;
        private List<TnrAssertionEmbedded> assertions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TnrAssertionEmbedded {
        private String type;
        private String path;
        private String operator;
        private Object expected;
        private Object actual;
        private boolean passed;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TnrResultEmbedded {
        private String status;
        private int passedSteps;
        private int failedSteps;
        private int totalSteps;
        private long durationMs;
        private String errorMessage;
        private LocalDateTime executedAt;
    }
}
