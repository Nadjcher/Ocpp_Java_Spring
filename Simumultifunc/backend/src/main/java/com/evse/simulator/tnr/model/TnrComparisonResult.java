package com.evse.simulator.tnr.model;

import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Résultat de comparaison TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrComparisonResult {
    private String baselineId;
    private String baselineName;
    private String comparedId;
    private String comparedName;
    private boolean match;
    private boolean signatureMatch;
    private boolean criticalSignatureMatch;
    private int totalFields;
    private int matchingFields;
    private int differingFields;
    private int baselineEventCount;
    private int comparedEventCount;
    private double similarityPercent;
    private Instant comparedAt;
    private long durationMs;
    private List<TnrDifference> differences;
    private Verdict verdict;
    private String summary;
    private ComparisonOptions options;

    public enum Verdict {
        PASS,
        FAIL,
        PARTIAL,
        SKIP,
        IDENTICAL,
        REGRESSION,
        COMPATIBLE,
        DIFFERENT
    }

    /**
     * Generates a summary of the comparison.
     */
    public String generateSummary() {
        if (match) {
            return String.format("PASS: All %d fields match", totalFields);
        } else {
            return String.format("FAIL: %d of %d fields differ", differingFields, totalFields);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonOptions {
        private boolean ignoreOrder;
        private boolean ignoreCase;
        private boolean ignoreWhitespace;
        private boolean ignoreExtraFields;
        private boolean ignoreHeartbeats;
        private boolean ignorePayloads;
        private boolean criticalOnly;
        private Set<String> ignoredPaths;
        private Set<String> ignoredEventTypes;
        private Set<String> ignoredActions;
        private double numericTolerance;

        /**
         * Creates default comparison options.
         */
        public static ComparisonOptions defaults() {
            return ComparisonOptions.builder()
                    .ignoreOrder(false)
                    .ignoreCase(false)
                    .ignoreWhitespace(true)
                    .ignoreExtraFields(false)
                    .ignoreHeartbeats(true)
                    .ignorePayloads(false)
                    .criticalOnly(false)
                    .ignoredPaths(new HashSet<>())
                    .ignoredEventTypes(new HashSet<>())
                    .ignoredActions(new HashSet<>())
                    .numericTolerance(0.001)
                    .build();
        }

        /**
         * Checks if payloads should be ignored.
         */
        public boolean isIgnorePayloads() {
            return ignorePayloads;
        }

        /**
         * Checks if heartbeats should be ignored.
         */
        public boolean isIgnoreHeartbeats() {
            return ignoreHeartbeats;
        }

        /**
         * Checks if only critical events matter.
         */
        public boolean isCriticalOnly() {
            return criticalOnly;
        }

        /**
         * Gets ignored event types.
         */
        public Set<String> getIgnoredEventTypes() {
            return ignoredEventTypes != null ? ignoredEventTypes : new HashSet<>();
        }

        /**
         * Gets ignored actions.
         */
        public Set<String> getIgnoredActions() {
            return ignoredActions != null ? ignoredActions : new HashSet<>();
        }
    }
}
