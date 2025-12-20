package com.evse.simulator.mapper;

import com.evse.simulator.dto.response.tnr.ExecutionResult;
import com.evse.simulator.dto.response.tnr.ScenarioSummary;
import com.evse.simulator.dto.response.tnr.StepResultSummary;
import com.evse.simulator.model.TNRExecutionResult;
import com.evse.simulator.model.TNRScenario;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mapper pour convertir les entites TNR en DTOs.
 */
@Component
public class TnrMapper {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_DATE_TIME;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Convertit un scenario en resume.
     */
    public ScenarioSummary toSummary(TNRScenario s) {
        String lastStatus = null;
        if (s.getStatus() != null) {
            lastStatus = s.getStatus().name();
        } else if (s.getLastResult() != null && s.getLastResult().getStatus() != null) {
            lastStatus = s.getLastResult().getStatus().name();
        }

        return new ScenarioSummary(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getCategory(),
                s.getSteps() != null ? s.getSteps().size() : 0,
                lastStatus,
                s.getLastRunAt() != null ? s.getLastRunAt().format(ISO_FORMAT) : null
        );
    }

    /**
     * Convertit un resultat d'execution.
     */
    public ExecutionResult toExecutionResult(TNRExecutionResult r) {
        int differencesCount = r.getDifferences() != null ? r.getDifferences().size() : 0;
        int eventsCount = r.getEvents() != null ? r.getEvents().size() : 0;

        // Extraire metrics si disponibles
        long avgLatency = 0;
        if (r.getMetrics() != null && r.getMetrics().getAvgLatency() != null) {
            avgLatency = r.getMetrics().getAvgLatency().longValue();
        }

        List<StepResultSummary> steps = List.of();
        if (r.getEvents() != null) {
            steps = r.getEvents().stream()
                    .map(e -> new StepResultSummary(
                            0,
                            e.getAction() != null ? e.getAction() : "Event",
                            r.isPassed() ? "PASSED" : "FAILED",
                            e.getLatency() != null ? e.getLatency() : 0,
                            null
                    ))
                    .toList();
        }

        return new ExecutionResult(
                r.getExecutionId(),
                r.getScenarioId(),
                r.getScenarioId(), // scenarioName non disponible, utilise ID
                r.isPassed() ? "PASSED" : "FAILED",
                eventsCount,
                r.isPassed() ? eventsCount : 0,
                r.isPassed() ? 0 : differencesCount,
                avgLatency,
                r.getTimestamp() != null ? DATE_FORMAT.format(r.getTimestamp()) : null,
                steps
        );
    }
}
