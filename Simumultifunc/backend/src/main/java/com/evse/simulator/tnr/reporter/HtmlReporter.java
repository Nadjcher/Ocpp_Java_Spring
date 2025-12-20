package com.evse.simulator.tnr.reporter;

import com.evse.simulator.tnr.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Reporter HTML pour les résultats TNR.
 * <p>
 * Génère un rapport HTML interactif avec:
 * - Couleurs: vert=passed, rouge=failed, gris=skipped
 * - Détails des steps avec logs
 * - Temps d'exécution par scénario
 * - Graphiques de synthèse
 * </p>
 *
 * @example
 * <pre>
 * HtmlReporter reporter = new HtmlReporter();
 * reporter.generateReport(suiteResult, Path.of("reports/tnr-report.html"));
 * </pre>
 */
@Slf4j
@Component
public class HtmlReporter implements TnrReporter {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public Format getFormat() {
        return Format.HTML;
    }

    @Override
    public void generateReport(TnrSuiteResult suiteResult, Path outputPath) throws IOException {
        String html = generateReportAsString(suiteResult);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, html);
        log.info("[HTML-REPORTER] Report generated: {}", outputPath);
    }

    @Override
    public void generateReport(TnrResult result, Path outputPath) throws IOException {
        String html = generateReportAsString(result);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, html);
        log.info("[HTML-REPORTER] Scenario report generated: {}", outputPath);
    }

    @Override
    public String generateReportAsString(TnrSuiteResult suiteResult) {
        StringBuilder html = new StringBuilder();

        html.append(generateHtmlHeader(suiteResult.getSuiteName()));
        html.append(generateSummarySection(suiteResult));
        html.append(generateScenariosSection(suiteResult));
        html.append(generateHtmlFooter());

        return html.toString();
    }

    @Override
    public String generateReportAsString(TnrResult result) {
        StringBuilder html = new StringBuilder();
        String title = result.getScenario() != null ? result.getScenario().getName() : "TNR Scenario";

        html.append(generateHtmlHeader(title));
        html.append(generateScenarioDetailSection(result));
        html.append(generateHtmlFooter());

        return html.toString();
    }

    // =========================================================================
    // HTML Generation
    // =========================================================================

    private String generateHtmlHeader(String title) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - TNR Report</title>
                <style>
                    :root {
                        --color-passed: #28a745;
                        --color-failed: #dc3545;
                        --color-skipped: #6c757d;
                        --color-error: #fd7e14;
                        --color-pending: #17a2b8;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        background: #f5f5f5;
                        padding: 20px;
                    }
                    .container { max-width: 1200px; margin: 0 auto; }
                    h1 { color: #2c3e50; margin-bottom: 20px; }
                    h2 { color: #34495e; margin: 20px 0 10px; border-bottom: 2px solid #3498db; padding-bottom: 5px; }
                    h3 { color: #7f8c8d; margin: 15px 0 10px; }

                    .summary-cards {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                        gap: 15px;
                        margin-bottom: 30px;
                    }
                    .card {
                        background: white;
                        border-radius: 8px;
                        padding: 20px;
                        text-align: center;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
                    .card-value { font-size: 2em; font-weight: bold; }
                    .card-label { color: #666; font-size: 0.9em; }
                    .card.passed .card-value { color: var(--color-passed); }
                    .card.failed .card-value { color: var(--color-failed); }
                    .card.skipped .card-value { color: var(--color-skipped); }

                    .progress-bar {
                        width: 100%%;
                        height: 30px;
                        background: #e9ecef;
                        border-radius: 15px;
                        overflow: hidden;
                        margin: 20px 0;
                    }
                    .progress-bar .segment {
                        height: 100%%;
                        float: left;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: white;
                        font-weight: bold;
                        font-size: 0.85em;
                    }
                    .progress-bar .passed { background: var(--color-passed); }
                    .progress-bar .failed { background: var(--color-failed); }
                    .progress-bar .skipped { background: var(--color-skipped); }

                    .scenario {
                        background: white;
                        border-radius: 8px;
                        margin-bottom: 15px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        overflow: hidden;
                    }
                    .scenario-header {
                        padding: 15px 20px;
                        cursor: pointer;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        border-left: 5px solid;
                    }
                    .scenario-header.passed { border-left-color: var(--color-passed); background: #f8fff8; }
                    .scenario-header.failed { border-left-color: var(--color-failed); background: #fff8f8; }
                    .scenario-header.skipped { border-left-color: var(--color-skipped); background: #f8f8f8; }
                    .scenario-header.error { border-left-color: var(--color-error); background: #fff8f0; }

                    .scenario-name { font-weight: 600; }
                    .scenario-meta { display: flex; gap: 20px; color: #666; font-size: 0.9em; }
                    .scenario-content { display: none; padding: 20px; border-top: 1px solid #eee; }
                    .scenario.open .scenario-content { display: block; }

                    .status-badge {
                        padding: 4px 12px;
                        border-radius: 12px;
                        font-size: 0.85em;
                        font-weight: 600;
                        text-transform: uppercase;
                    }
                    .status-badge.passed { background: var(--color-passed); color: white; }
                    .status-badge.failed { background: var(--color-failed); color: white; }
                    .status-badge.skipped { background: var(--color-skipped); color: white; }
                    .status-badge.error { background: var(--color-error); color: white; }

                    .step {
                        padding: 10px 15px;
                        margin: 5px 0;
                        border-radius: 4px;
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                    .step.passed { background: #e8f5e9; }
                    .step.failed { background: #ffebee; }
                    .step.skipped { background: #f5f5f5; }
                    .step.error { background: #fff3e0; }

                    .step-icon { font-size: 1.2em; }
                    .step.passed .step-icon::before { content: "[OK]"; color: var(--color-passed); }
                    .step.failed .step-icon::before { content: "[ERR]"; color: var(--color-failed); }
                    .step.skipped .step-icon::before { content: "[SKIP]"; color: var(--color-skipped); }
                    .step.error .step-icon::before { content: "[WARN]"; color: var(--color-error); }

                    .step-keyword { font-weight: 600; color: #7f8c8d; min-width: 60px; }
                    .step-text { flex: 1; }
                    .step-duration { color: #999; font-size: 0.85em; }

                    .error-message {
                        background: #ffebee;
                        border: 1px solid #ffcdd2;
                        border-radius: 4px;
                        padding: 10px;
                        margin-top: 10px;
                        font-family: monospace;
                        font-size: 0.9em;
                        color: #c62828;
                        white-space: pre-wrap;
                        word-break: break-word;
                    }

                    .logs {
                        background: #263238;
                        color: #aed581;
                        border-radius: 4px;
                        padding: 10px;
                        margin-top: 10px;
                        font-family: monospace;
                        font-size: 0.85em;
                        max-height: 200px;
                        overflow-y: auto;
                    }
                    .logs .log-line { margin: 2px 0; }

                    .meta-info {
                        background: #f8f9fa;
                        padding: 15px;
                        border-radius: 8px;
                        margin-bottom: 20px;
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 10px;
                    }
                    .meta-item { }
                    .meta-label { color: #666; font-size: 0.85em; }
                    .meta-value { font-weight: 600; }

                    .toggle-icon { transition: transform 0.2s; }
                    .scenario.open .toggle-icon { transform: rotate(90deg); }

                    @media (max-width: 768px) {
                        .scenario-meta { flex-direction: column; gap: 5px; }
                    }
                </style>
            </head>
            <body>
                <div class="container">
            """.formatted(escapeHtml(title));
    }

    private String generateSummarySection(TnrSuiteResult suiteResult) {
        StringBuilder html = new StringBuilder();

        html.append("<h1>").append(escapeHtml(suiteResult.getSuiteName())).append("</h1>");

        // Meta info
        html.append("<div class=\"meta-info\">");
        html.append(metaItem("Execution ID", suiteResult.getExecutionId()));
        html.append(metaItem("Environment", suiteResult.getEnvironment()));
        html.append(metaItem("Started", formatTime(suiteResult.getStartTime())));
        html.append(metaItem("Duration", formatDuration(suiteResult.getDurationMs())));
        html.append("</div>");

        // Summary cards
        html.append("<div class=\"summary-cards\">");
        html.append(summaryCard("Total", suiteResult.getTotalScenarios(), ""));
        html.append(summaryCard("Passed", (int) suiteResult.getPassedCount(), "passed"));
        html.append(summaryCard("Failed", (int) suiteResult.getFailedCount(), "failed"));
        html.append(summaryCard("Skipped", (int) suiteResult.getSkippedCount(), "skipped"));
        html.append(summaryCard("Success Rate", String.format("%.1f%%", suiteResult.getSuccessRate()), ""));
        html.append("</div>");

        // Progress bar
        int total = suiteResult.getTotalScenarios();
        if (total > 0) {
            double passedPct = (double) suiteResult.getPassedCount() / total * 100;
            double failedPct = (double) suiteResult.getFailedCount() / total * 100;
            double skippedPct = (double) suiteResult.getSkippedCount() / total * 100;

            html.append("<div class=\"progress-bar\">");
            if (passedPct > 0) {
                html.append(String.format("<div class=\"segment passed\" style=\"width:%.1f%%\">%d</div>",
                        passedPct, suiteResult.getPassedCount()));
            }
            if (failedPct > 0) {
                html.append(String.format("<div class=\"segment failed\" style=\"width:%.1f%%\">%d</div>",
                        failedPct, suiteResult.getFailedCount()));
            }
            if (skippedPct > 0) {
                html.append(String.format("<div class=\"segment skipped\" style=\"width:%.1f%%\">%d</div>",
                        skippedPct, suiteResult.getSkippedCount()));
            }
            html.append("</div>");
        }

        return html.toString();
    }

    private String generateScenariosSection(TnrSuiteResult suiteResult) {
        StringBuilder html = new StringBuilder();

        html.append("<h2>Scénarios</h2>");

        if (suiteResult.getScenarioResults() != null) {
            for (TnrResult result : suiteResult.getScenarioResults()) {
                html.append(generateScenarioCard(result));
            }
        }

        return html.toString();
    }

    private String generateScenarioCard(TnrResult result) {
        StringBuilder html = new StringBuilder();
        String status = result.getStatus().name().toLowerCase();
        String name = result.getScenario() != null ? result.getScenario().getName() : "Unknown";
        String id = result.getScenario() != null ? result.getScenario().getId() : "";

        html.append(String.format("<div class=\"scenario\" onclick=\"this.classList.toggle('open')\">"));
        html.append(String.format("<div class=\"scenario-header %s\">", status));
        html.append("<div>");
        html.append(String.format("<span class=\"scenario-name\">%s</span>", escapeHtml(name)));
        if (!id.isEmpty()) {
            html.append(String.format(" <small style=\"color:#999\">[%s]</small>", escapeHtml(id)));
        }
        html.append("</div>");
        html.append("<div class=\"scenario-meta\">");
        html.append(String.format("<span>%s</span>", formatDuration(result.getDurationMs())));
        html.append(String.format("<span class=\"status-badge %s\">%s</span>", status, status));
        html.append("<span class=\"toggle-icon\">▶</span>");
        html.append("</div>");
        html.append("</div>");

        // Content (steps)
        html.append("<div class=\"scenario-content\">");

        if (result.getStepResults() != null) {
            for (TnrStepResult stepResult : result.getStepResults()) {
                html.append(generateStepHtml(stepResult));
            }
        }

        if (result.getErrorMessage() != null) {
            html.append("<div class=\"error-message\">");
            html.append(escapeHtml(result.getErrorMessage()));
            html.append("</div>");
        }

        html.append("</div>");
        html.append("</div>");

        return html.toString();
    }

    private String generateScenarioDetailSection(TnrResult result) {
        StringBuilder html = new StringBuilder();

        String name = result.getScenario() != null ? result.getScenario().getName() : "Scenario";
        String status = result.getStatus().name().toLowerCase();

        html.append(String.format("<h1>%s <span class=\"status-badge %s\">%s</span></h1>",
                escapeHtml(name), status, status));

        // Meta info
        html.append("<div class=\"meta-info\">");
        html.append(metaItem("Execution ID", result.getExecutionId()));
        html.append(metaItem("Started", formatTime(result.getStartTime())));
        html.append(metaItem("Duration", formatDuration(result.getDurationMs())));
        html.append(metaItem("Retries", String.valueOf(result.getRetryCount())));
        html.append("</div>");

        // Steps
        html.append("<h2>Steps</h2>");
        if (result.getStepResults() != null) {
            for (TnrStepResult stepResult : result.getStepResults()) {
                html.append(generateStepHtml(stepResult));
            }
        }

        // Error
        if (result.getErrorMessage() != null) {
            html.append("<h2>Error</h2>");
            html.append("<div class=\"error-message\">");
            html.append(escapeHtml(result.getErrorMessage()));
            html.append("</div>");
        }

        return html.toString();
    }

    private String generateStepHtml(TnrStepResult stepResult) {
        StringBuilder html = new StringBuilder();
        String status = stepResult.getStatus().name().toLowerCase();
        String keyword = stepResult.getStep() != null && stepResult.getStep().getType() != null ?
                stepResult.getStep().getType().name() : "";
        String text = stepResult.getStep() != null ? stepResult.getStep().getText() : "";

        html.append(String.format("<div class=\"step %s\">", status));
        html.append("<span class=\"step-icon\"></span>");
        html.append(String.format("<span class=\"step-keyword\">%s</span>", keyword));
        html.append(String.format("<span class=\"step-text\">%s</span>", escapeHtml(text)));
        html.append(String.format("<span class=\"step-duration\">%dms</span>", stepResult.getDurationMs()));
        html.append("</div>");

        // Error message for failed steps
        if (stepResult.getErrorMessage() != null && !stepResult.getErrorMessage().isEmpty()) {
            html.append("<div class=\"error-message\" style=\"margin-left:30px\">");
            html.append(escapeHtml(stepResult.getErrorMessage()));
            html.append("</div>");
        }

        // Logs
        List<String> logs = stepResult.getLogs();
        if (logs != null && !logs.isEmpty()) {
            html.append("<div class=\"logs\" style=\"margin-left:30px\">");
            for (String log : logs) {
                html.append("<div class=\"log-line\">").append(escapeHtml(log)).append("</div>");
            }
            html.append("</div>");
        }

        return html.toString();
    }

    private String generateHtmlFooter() {
        return """
                </div>
                <script>
                    // Auto-expand failed scenarios
                    document.querySelectorAll('.scenario-header.failed, .scenario-header.error')
                        .forEach(h => h.parentElement.classList.add('open'));
                </script>
            </body>
            </html>
            """;
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private String summaryCard(String label, int value, String cssClass) {
        return String.format("""
            <div class="card %s">
                <div class="card-value">%d</div>
                <div class="card-label">%s</div>
            </div>
            """, cssClass, value, label);
    }

    private String summaryCard(String label, String value, String cssClass) {
        return String.format("""
            <div class="card %s">
                <div class="card-value">%s</div>
                <div class="card-label">%s</div>
            </div>
            """, cssClass, value, label);
    }

    private String metaItem(String label, String value) {
        return String.format("""
            <div class="meta-item">
                <div class="meta-label">%s</div>
                <div class="meta-value">%s</div>
            </div>
            """, label, value != null ? escapeHtml(value) : "-");
    }

    private String formatTime(Instant instant) {
        if (instant == null) return "-";
        return TIME_FORMATTER.format(instant);
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return String.format("%dm %ds", minutes, seconds);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
