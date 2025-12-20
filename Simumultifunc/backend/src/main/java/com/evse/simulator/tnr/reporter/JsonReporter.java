package com.evse.simulator.tnr.reporter;

import com.evse.simulator.tnr.model.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reporter JSON pour les résultats TNR.
 * <p>
 * Génère un fichier JSON structuré avec toutes les informations
 * d'exécution, incluant les scénarios, steps, métriques et erreurs.
 * </p>
 *
 * @example
 * <pre>
 * JsonReporter reporter = new JsonReporter();
 * reporter.generateReport(suiteResult, Path.of("reports/tnr-result.json"));
 * </pre>
 */
@Slf4j
@Component
public class JsonReporter implements TnrReporter {

    private final ObjectMapper objectMapper;

    public JsonReporter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public JsonReporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Format getFormat() {
        return Format.JSON;
    }

    @Override
    public void generateReport(TnrSuiteResult suiteResult, Path outputPath) throws IOException {
        String json = generateReportAsString(suiteResult);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, json);
        log.info("[JSON-REPORTER] Report generated: {}", outputPath);
    }

    @Override
    public void generateReport(TnrResult result, Path outputPath) throws IOException {
        String json = generateReportAsString(result);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, json);
        log.info("[JSON-REPORTER] Scenario report generated: {}", outputPath);
    }

    @Override
    public String generateReportAsString(TnrSuiteResult suiteResult) {
        try {
            JsonReport report = buildReport(suiteResult);
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.error("[JSON-REPORTER] Error generating report: {}", e.getMessage());
            return "{}";
        }
    }

    @Override
    public String generateReportAsString(TnrResult result) {
        try {
            JsonScenarioReport report = buildScenarioReport(result);
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.error("[JSON-REPORTER] Error generating scenario report: {}", e.getMessage());
            return "{}";
        }
    }

    // =========================================================================
    // Report Building
    // =========================================================================

    private JsonReport buildReport(TnrSuiteResult suiteResult) {
        JsonReport report = new JsonReport();
        report.setExecutionId(suiteResult.getExecutionId());
        report.setSuiteName(suiteResult.getSuiteName());
        report.setEnvironment(suiteResult.getEnvironment());
        report.setVersion(suiteResult.getVersion());
        report.setStartedAt(suiteResult.getStartTime());
        report.setCompletedAt(suiteResult.getEndTime());
        report.setDurationMs(suiteResult.getDurationMs());

        // Summary
        JsonSummary summary = new JsonSummary();
        summary.setTotal(suiteResult.getTotalScenarios());
        summary.setPassed((int) suiteResult.getPassedCount());
        summary.setFailed((int) suiteResult.getFailedCount());
        summary.setSkipped((int) suiteResult.getSkippedCount());
        summary.setErrors((int) suiteResult.getErrorCount());
        summary.setSuccessRate(suiteResult.getSuccessRate());
        summary.setTotalSteps((int) suiteResult.getTotalSteps());
        summary.setPassedSteps((int) suiteResult.getPassedSteps());
        summary.setAverageDurationMs(suiteResult.getAverageDurationMs());
        report.setSummary(summary);

        // Scenarios
        List<JsonScenarioReport> scenarios = new ArrayList<>();
        if (suiteResult.getScenarioResults() != null) {
            for (TnrResult result : suiteResult.getScenarioResults()) {
                scenarios.add(buildScenarioReport(result));
            }
        }
        report.setScenarios(scenarios);

        // Metrics
        report.setMetrics(suiteResult.getMetrics());

        // Tags
        report.setTags(suiteResult.getTags());

        return report;
    }

    private JsonScenarioReport buildScenarioReport(TnrResult result) {
        JsonScenarioReport scenarioReport = new JsonScenarioReport();

        if (result.getScenario() != null) {
            scenarioReport.setId(result.getScenario().getId());
            scenarioReport.setName(result.getScenario().getName());
            scenarioReport.setDescription(result.getScenario().getDescription());
            scenarioReport.setTags(result.getScenario().getTags());
        }

        scenarioReport.setExecutionId(result.getExecutionId());
        scenarioReport.setStatus(result.getStatus().name());
        scenarioReport.setStartedAt(result.getStartTime());
        scenarioReport.setCompletedAt(result.getEndTime());
        scenarioReport.setDurationMs(result.getDurationMs());
        scenarioReport.setRetryCount(result.getRetryCount());
        scenarioReport.setErrorMessage(result.getErrorMessage());

        // Steps
        List<JsonStepReport> steps = new ArrayList<>();
        if (result.getStepResults() != null) {
            for (TnrStepResult stepResult : result.getStepResults()) {
                steps.add(buildStepReport(stepResult));
            }
        }
        scenarioReport.setSteps(steps);

        // Metrics
        scenarioReport.setMetrics(result.getMetrics());

        // Anomalies
        if (result.getAnomalies() != null && !result.getAnomalies().isEmpty()) {
            scenarioReport.setAnomalies(result.getAnomalies().stream()
                    .map(a -> new JsonAnomaly(
                            a.getType() != null ? a.getType().name() : null,
                            a.getMessage(),
                            a.getSeverity() != null ? a.getSeverity().name() : null))
                    .collect(Collectors.toList()));
        }

        return scenarioReport;
    }

    private JsonStepReport buildStepReport(TnrStepResult stepResult) {
        JsonStepReport stepReport = new JsonStepReport();

        if (stepResult.getStep() != null) {
            stepReport.setKeyword(stepResult.getStep().getType() != null ?
                    stepResult.getStep().getType().name() : "");
            stepReport.setText(stepResult.getStep().getText());
        }

        stepReport.setStatus(stepResult.getStatus().name());
        stepReport.setDurationMs(stepResult.getDurationMs());
        stepReport.setErrorMessage(stepResult.getErrorMessage());
        stepReport.setLogs(stepResult.getLogs());

        // Assertions
        if (stepResult.getAssertions() != null && !stepResult.getAssertions().isEmpty()) {
            stepReport.setAssertions(stepResult.getAssertions().stream()
                    .map(a -> new JsonAssertion(a.getMessage(), a.isPassed(), a.getActual(), a.getExpected()))
                    .collect(Collectors.toList()));
        }

        // OCPP Messages
        if (stepResult.getOcppMessages() != null && !stepResult.getOcppMessages().isEmpty()) {
            stepReport.setOcppMessages(stepResult.getOcppMessages().stream()
                    .map(m -> new JsonOcppMessage(m.getAction(), m.getDirection().name(), m.getTimestamp(), m.getLatencyMs()))
                    .collect(Collectors.toList()));
        }

        return stepReport;
    }

    // =========================================================================
    // JSON Model Classes
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JsonReport {
        private String executionId;
        private String suiteName;
        private String environment;
        private String version;
        private Instant startedAt;
        private Instant completedAt;
        private long durationMs;
        private JsonSummary summary;
        private List<JsonScenarioReport> scenarios;
        private java.util.Map<String, Object> metrics;
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JsonSummary {
        private int total;
        private int passed;
        private int failed;
        private int skipped;
        private int errors;
        private double successRate;
        private int totalSteps;
        private int passedSteps;
        private double averageDurationMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JsonScenarioReport {
        private String id;
        private String executionId;
        private String name;
        private String description;
        private List<String> tags;
        private String status;
        private Instant startedAt;
        private Instant completedAt;
        private long durationMs;
        private int retryCount;
        private String errorMessage;
        private List<JsonStepReport> steps;
        private java.util.Map<String, Object> metrics;
        private List<JsonAnomaly> anomalies;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JsonStepReport {
        private String keyword;
        private String text;
        private String status;
        private long durationMs;
        private String errorMessage;
        private List<String> logs;
        private List<JsonAssertion> assertions;
        private List<JsonOcppMessage> ocppMessages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsonAssertion {
        private String description;
        private boolean passed;
        private Object actualValue;
        private Object expectedValue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsonOcppMessage {
        private String action;
        private String direction;
        private Instant timestamp;
        private Long latencyMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsonAnomaly {
        private String type;
        private String message;
        private String severity;
    }
}
