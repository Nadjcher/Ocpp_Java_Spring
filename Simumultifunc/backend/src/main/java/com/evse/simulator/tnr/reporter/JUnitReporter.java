package com.evse.simulator.tnr.reporter;

import com.evse.simulator.tnr.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Reporter JUnit XML pour les résultats TNR.
 * <p>
 * Génère un fichier XML au format JUnit compatible avec Jenkins, GitLab CI,
 * et autres outils CI/CD.
 * </p>
 *
 * @example
 * <pre>
 * JUnitReporter reporter = new JUnitReporter();
 * reporter.generateReport(suiteResult, Path.of("reports/TEST-tnr.xml"));
 * </pre>
 *
 * Format de sortie:
 * <pre>
 * {@code
 * <testsuite name="TNR Suite" tests="10" failures="1" errors="0" skipped="1" time="150.0">
 *   <testcase name="SC001 - BootNotification" classname="tnr.ocpp" time="1.234">
 *     <failure message="Expected Accepted but got Rejected">...</failure>
 *   </testcase>
 * </testsuite>
 * }
 * </pre>
 */
@Slf4j
@Component
public class JUnitReporter implements TnrReporter {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault());

    @Override
    public Format getFormat() {
        return Format.JUNIT_XML;
    }

    @Override
    public void generateReport(TnrSuiteResult suiteResult, Path outputPath) throws IOException {
        String xml = generateReportAsString(suiteResult);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, xml);
        log.info("[JUNIT-REPORTER] Report generated: {}", outputPath);
    }

    @Override
    public void generateReport(TnrResult result, Path outputPath) throws IOException {
        String xml = generateReportAsString(result);
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, xml);
        log.info("[JUNIT-REPORTER] Scenario report generated: {}", outputPath);
    }

    @Override
    public String generateReportAsString(TnrSuiteResult suiteResult) {
        try {
            StringWriter stringWriter = new StringWriter();
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = factory.createXMLStreamWriter(stringWriter);

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            // <testsuites>
            writer.writeStartElement("testsuites");
            writer.writeCharacters("\n");

            // <testsuite>
            writeSuiteElement(writer, suiteResult);

            writer.writeEndElement(); // </testsuites>
            writer.writeEndDocument();
            writer.close();

            return stringWriter.toString();
        } catch (XMLStreamException e) {
            log.error("[JUNIT-REPORTER] Error generating XML: {}", e.getMessage());
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuites/>";
        }
    }

    @Override
    public String generateReportAsString(TnrResult result) {
        try {
            StringWriter stringWriter = new StringWriter();
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = factory.createXMLStreamWriter(stringWriter);

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            // <testsuite> for single scenario
            writer.writeStartElement("testsuite");
            String name = result.getScenario() != null ? result.getScenario().getName() : "TNR Scenario";
            writer.writeAttribute("name", name);
            writer.writeAttribute("tests", "1");
            writer.writeAttribute("failures", result.getStatus() == TnrResult.Status.FAILED ? "1" : "0");
            writer.writeAttribute("errors", result.getStatus() == TnrResult.Status.ERROR ? "1" : "0");
            writer.writeAttribute("skipped", result.getStatus() == TnrResult.Status.SKIPPED ? "1" : "0");
            writer.writeAttribute("time", formatTime(result.getDurationMs()));
            if (result.getStartTime() != null) {
                writer.writeAttribute("timestamp", TIMESTAMP_FORMATTER.format(result.getStartTime()));
            }
            writer.writeCharacters("\n");

            writeTestCase(writer, result);

            writer.writeEndElement(); // </testsuite>
            writer.writeEndDocument();
            writer.close();

            return stringWriter.toString();
        } catch (XMLStreamException e) {
            log.error("[JUNIT-REPORTER] Error generating scenario XML: {}", e.getMessage());
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite/>";
        }
    }

    // =========================================================================
    // XML Writing
    // =========================================================================

    private void writeSuiteElement(XMLStreamWriter writer, TnrSuiteResult suiteResult) throws XMLStreamException {
        writer.writeStartElement("testsuite");
        writer.writeAttribute("name", suiteResult.getSuiteName() != null ? suiteResult.getSuiteName() : "TNR Suite");
        writer.writeAttribute("tests", String.valueOf(suiteResult.getTotalScenarios()));
        writer.writeAttribute("failures", String.valueOf(suiteResult.getFailedCount()));
        writer.writeAttribute("errors", String.valueOf(suiteResult.getErrorCount()));
        writer.writeAttribute("skipped", String.valueOf(suiteResult.getSkippedCount()));
        writer.writeAttribute("time", formatTime(suiteResult.getDurationMs()));

        if (suiteResult.getStartTime() != null) {
            writer.writeAttribute("timestamp", TIMESTAMP_FORMATTER.format(suiteResult.getStartTime()));
        }
        if (suiteResult.getEnvironment() != null) {
            writer.writeAttribute("hostname", suiteResult.getEnvironment());
        }
        writer.writeCharacters("\n");

        // Properties
        writeProperties(writer, suiteResult);

        // Test cases
        if (suiteResult.getScenarioResults() != null) {
            for (TnrResult result : suiteResult.getScenarioResults()) {
                writeTestCase(writer, result);
            }
        }

        // System output
        writeSystemOut(writer, suiteResult);

        writer.writeEndElement(); // </testsuite>
        writer.writeCharacters("\n");
    }

    private void writeProperties(XMLStreamWriter writer, TnrSuiteResult suiteResult) throws XMLStreamException {
        writer.writeStartElement("properties");
        writer.writeCharacters("\n");

        writeProperty(writer, "execution.id", suiteResult.getExecutionId());
        writeProperty(writer, "environment", suiteResult.getEnvironment());
        writeProperty(writer, "version", suiteResult.getVersion());

        if (suiteResult.getTags() != null && !suiteResult.getTags().isEmpty()) {
            writeProperty(writer, "tags", String.join(",", suiteResult.getTags()));
        }

        writer.writeEndElement(); // </properties>
        writer.writeCharacters("\n");
    }

    private void writeProperty(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
        if (value == null) return;
        writer.writeEmptyElement("property");
        writer.writeAttribute("name", name);
        writer.writeAttribute("value", value);
        writer.writeCharacters("\n");
    }

    private void writeTestCase(XMLStreamWriter writer, TnrResult result) throws XMLStreamException {
        writer.writeStartElement("testcase");

        String name = result.getScenario() != null ? result.getScenario().getName() : "Unknown";
        String id = result.getScenario() != null ? result.getScenario().getId() : "";
        String classname = buildClassName(result);

        writer.writeAttribute("name", name);
        writer.writeAttribute("classname", classname);
        writer.writeAttribute("time", formatTime(result.getDurationMs()));

        if (!id.isEmpty()) {
            writer.writeAttribute("id", id);
        }

        writer.writeCharacters("\n");

        // Status-specific elements
        switch (result.getStatus()) {
            case FAILED -> writeFailure(writer, result);
            case ERROR -> writeError(writer, result);
            case SKIPPED -> writeSkipped(writer, result);
            default -> { /* PASSED - no additional elements */ }
        }

        // System output with step details
        writeTestCaseOutput(writer, result);

        writer.writeEndElement(); // </testcase>
        writer.writeCharacters("\n");
    }

    private void writeFailure(XMLStreamWriter writer, TnrResult result) throws XMLStreamException {
        writer.writeStartElement("failure");

        TnrStepResult failedStep = result.getFirstFailedStep();
        String message = result.getErrorMessage();
        if (message == null && failedStep != null) {
            message = failedStep.getErrorMessage();
        }

        writer.writeAttribute("message", message != null ? truncate(message, 500) : "Test failed");
        writer.writeAttribute("type", "AssertionError");

        // Content: stack trace or detailed error
        StringBuilder content = new StringBuilder();
        if (failedStep != null) {
            if (failedStep.getStep() != null) {
                String stepType = failedStep.getStep().getType() != null ?
                        failedStep.getStep().getType().name() : "";
                content.append("Failed at step: ").append(stepType)
                       .append(" ").append(failedStep.getStep().getText()).append("\n\n");
            }
            if (failedStep.getStackTrace() != null) {
                content.append(failedStep.getStackTrace());
            }
        }
        if (result.getStackTrace() != null) {
            content.append(result.getStackTrace());
        }

        writer.writeCharacters(content.toString());
        writer.writeEndElement(); // </failure>
        writer.writeCharacters("\n");
    }

    private void writeError(XMLStreamWriter writer, TnrResult result) throws XMLStreamException {
        writer.writeStartElement("error");

        String message = result.getErrorMessage();
        writer.writeAttribute("message", message != null ? truncate(message, 500) : "Test error");
        writer.writeAttribute("type", "Exception");

        if (result.getStackTrace() != null) {
            writer.writeCharacters(result.getStackTrace());
        }

        writer.writeEndElement(); // </error>
        writer.writeCharacters("\n");
    }

    private void writeSkipped(XMLStreamWriter writer, TnrResult result) throws XMLStreamException {
        writer.writeEmptyElement("skipped");
        if (result.getErrorMessage() != null) {
            writer.writeAttribute("message", result.getErrorMessage());
        }
        writer.writeCharacters("\n");
    }

    private void writeTestCaseOutput(XMLStreamWriter writer, TnrResult result) throws XMLStreamException {
        StringBuilder output = new StringBuilder();

        // Step summary
        if (result.getStepResults() != null) {
            output.append("=== Steps ===\n");
            for (TnrStepResult stepResult : result.getStepResults()) {
                String status = stepResult.getStatus().name();
                String keyword = stepResult.getStep() != null && stepResult.getStep().getType() != null ?
                        stepResult.getStep().getType().name() : "";
                String text = stepResult.getStep() != null ? stepResult.getStep().getText() : "";
                long duration = stepResult.getDurationMs();

                output.append(String.format("[%s] %s %s (%dms)\n", status, keyword, text, duration));

                // Step logs
                if (stepResult.getLogs() != null) {
                    for (String log : stepResult.getLogs()) {
                        output.append("  > ").append(log).append("\n");
                    }
                }
            }
        }

        // Metrics
        if (result.getMetrics() != null && !result.getMetrics().isEmpty()) {
            output.append("\n=== Metrics ===\n");
            result.getMetrics().forEach((k, v) ->
                output.append(String.format("%s: %s\n", k, v)));
        }

        if (output.length() > 0) {
            writer.writeStartElement("system-out");
            writer.writeCData(output.toString());
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
    }

    private void writeSystemOut(XMLStreamWriter writer, TnrSuiteResult suiteResult) throws XMLStreamException {
        StringBuilder output = new StringBuilder();
        output.append("=== TNR Suite Summary ===\n");
        output.append(String.format("Total: %d\n", suiteResult.getTotalScenarios()));
        output.append(String.format("Passed: %d\n", suiteResult.getPassedCount()));
        output.append(String.format("Failed: %d\n", suiteResult.getFailedCount()));
        output.append(String.format("Skipped: %d\n", suiteResult.getSkippedCount()));
        output.append(String.format("Success Rate: %.1f%%\n", suiteResult.getSuccessRate()));
        output.append(String.format("Duration: %s\n", formatDuration(suiteResult.getDurationMs())));

        writer.writeStartElement("system-out");
        writer.writeCData(output.toString());
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private String buildClassName(TnrResult result) {
        StringBuilder classname = new StringBuilder("tnr");

        if (result.getScenario() != null) {
            if (result.getScenario().getTags() != null && !result.getScenario().getTags().isEmpty()) {
                // Use first tag as package
                String tag = result.getScenario().getTags().get(0).replace("@", "");
                classname.append(".").append(sanitizeClassName(tag));
            }
            if (result.getScenario().getId() != null) {
                classname.append(".").append(sanitizeClassName(result.getScenario().getId()));
            }
        }

        return classname.toString();
    }

    private String sanitizeClassName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String formatTime(long ms) {
        return String.format("%.3f", ms / 1000.0);
    }

    private String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return String.format("%dm %ds", minutes, seconds);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
