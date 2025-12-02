package com.evse.simulator.dto.response;

import com.evse.simulator.model.TNRScenario.ScenarioStatus;
import com.evse.simulator.model.TNRScenario.TNRResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Réponse du résultat d'exécution d'un scénario TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TnrResultResponse {

    /**
     * ID du scénario.
     */
    private String scenarioId;

    /**
     * Nom du scénario.
     */
    private String scenarioName;

    /**
     * Statut global.
     */
    private ScenarioStatus status;

    /**
     * Nombre d'étapes réussies.
     */
    private int passedSteps;

    /**
     * Nombre d'étapes échouées.
     */
    private int failedSteps;

    /**
     * Nombre total d'étapes.
     */
    private int totalSteps;

    /**
     * Progression (%).
     */
    private double progress;

    /**
     * Durée totale en ms.
     */
    private long durationMs;

    /**
     * Date d'exécution.
     */
    private LocalDateTime executedAt;

    /**
     * Résultats par étape.
     */
    @Builder.Default
    private List<StepResultResponse> stepResults = new ArrayList<>();

    /**
     * Message d'erreur global.
     */
    private String errorMessage;

    /**
     * Logs d'exécution.
     */
    @Builder.Default
    private List<String> logs = new ArrayList<>();

    /**
     * Résultat d'une étape.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResultResponse {
        private int stepOrder;
        private String stepName;
        private boolean passed;
        private String message;
        private long durationMs;
        private Object response;
        private String error;
    }

    /**
     * Convertit un TNRResult en DTO.
     */
    public static TnrResultResponse fromResult(TNRResult result) {
        if (result == null) {
            return null;
        }

        double progress = result.getTotalSteps() > 0 ?
                (result.getPassedSteps() + result.getFailedSteps()) * 100.0 / result.getTotalSteps() : 0;

        return TnrResultResponse.builder()
                .scenarioId(result.getScenarioId())
                .status(result.getStatus())
                .passedSteps(result.getPassedSteps())
                .failedSteps(result.getFailedSteps())
                .totalSteps(result.getTotalSteps())
                .progress(progress)
                .durationMs(result.getDurationMs())
                .executedAt(result.getExecutedAt())
                .errorMessage(result.getErrorMessage())
                .logs(result.getLogs())
                .build();
    }
}