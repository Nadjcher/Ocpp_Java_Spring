package com.evse.simulator.tnr.service;

import com.evse.simulator.tnr.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service de validation des résultats TNR avec support des tolérances.
 * <p>
 * Valide les exécutions contre les résultats attendus en utilisant
 * des tolérances configurables pour les valeurs numériques.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TnrValidationService {

    /**
     * Valide une exécution complète contre un scénario.
     */
    public TnrValidationSummary validateExecution(TnrExecution execution, TnrScenario scenario) {
        TnrValidationSummary summary = new TnrValidationSummary();
        summary.setExecutionId(execution.getId());
        summary.setScenarioId(scenario.getId());

        TnrExpectedResults expected = scenario.getExpectedResults();
        TnrTolerances tolerances = scenario.getConfig() != null && scenario.getConfig().getTolerances() != null
                ? scenario.getConfig().getTolerances()
                : TnrTolerances.defaults();

        if (expected == null) {
            log.info("No expected results defined for scenario {}, skipping validation", scenario.getId());
            summary.setPassed(true);
            summary.addRecommendation("Consider defining expected results for better validation.");
            return summary;
        }

        // Valider les résultats globaux
        validateGlobalResults(execution, expected, tolerances, summary);

        // Valider les assertions personnalisées
        if (expected.getCustomAssertions() != null) {
            validateCustomAssertions(execution, expected.getCustomAssertions(), summary);
        }

        // Déterminer le statut final
        summary.setPassed(summary.getFailedAssertions() == 0);

        log.info("Validation completed for execution {}: {} ({}/{} passed)",
                execution.getId(),
                summary.isPassed() ? "PASSED" : "FAILED",
                summary.getPassedAssertions(),
                summary.getTotalAssertions());

        return summary;
    }

    private void validateGlobalResults(TnrExecution execution, TnrExpectedResults expected,
                                        TnrTolerances tolerances, TnrValidationSummary summary) {
        // 1. Statut de complétion
        boolean allCompleted = "COMPLETED".equals(execution.getStatus()) || "PASSED".equals(execution.getStatus());
        summary.addComparison(new TnrValidationSummary.ExpectedVsActual(
                "status.completed",
                expected.isAllSessionsCompleted(),
                allCompleted,
                expected.isAllSessionsCompleted() == allCompleted,
                null
        ));

        // 2. Erreurs OCPP
        int actualOcppErrors = countOcppErrors(execution);
        boolean ocppErrorsPassed = actualOcppErrors <= expected.getOcppErrors();
        summary.addComparison(new TnrValidationSummary.ExpectedVsActual(
                "ocpp.errors",
                expected.getOcppErrors(),
                actualOcppErrors,
                ocppErrorsPassed,
                (double) (actualOcppErrors - expected.getOcppErrors())
        ));

        if (!ocppErrorsPassed) {
            summary.addRecommendation("OCPP errors detected (" + actualOcppErrors + " vs expected " +
                    expected.getOcppErrors() + "). Check message formatting and CSMS responses.");
        }

        // 3. Énergie totale (si définie)
        if (expected.getTotalEnergyKwh() != null) {
            double actualEnergy = extractTotalEnergy(execution);
            boolean energyPassed = tolerances.isWithinEnergyTolerance(expected.getTotalEnergyKwh(), actualEnergy);
            summary.addComparison(new TnrValidationSummary.ExpectedVsActual(
                    "energy.totalKwh",
                    expected.getTotalEnergyKwh(),
                    actualEnergy,
                    energyPassed,
                    actualEnergy - expected.getTotalEnergyKwh()
            ));

            if (!energyPassed) {
                summary.addRecommendation("Energy delivered differs from expected. Check charging profiles and vehicle behavior.");
            }
        }

        // 4. Durée totale (si définie)
        if (expected.getTotalDurationSec() != null) {
            int actualDuration = (int) (execution.getDurationMs() / 1000);
            boolean durationPassed = tolerances.isWithinDurationTolerance(expected.getTotalDurationSec(), actualDuration);
            summary.addComparison(new TnrValidationSummary.ExpectedVsActual(
                    "duration.totalSec",
                    expected.getTotalDurationSec(),
                    actualDuration,
                    durationPassed,
                    (double) (actualDuration - expected.getTotalDurationSec())
            ));
        }

        // 5. Profils SCP appliqués (si défini)
        if (expected.getScpProfilesApplied() != null) {
            int actualScpCount = countScpProfiles(execution);
            boolean scpCountPassed = actualScpCount >= expected.getScpProfilesApplied();
            summary.addComparison(new TnrValidationSummary.ExpectedVsActual(
                    "scp.profilesApplied",
                    expected.getScpProfilesApplied(),
                    actualScpCount,
                    scpCountPassed,
                    (double) (actualScpCount - expected.getScpProfilesApplied())
            ));
        }

        // 6. Respect des limites SCP (si défini)
        if (expected.getScpLimitRespected() != null && expected.getScpLimitRespected()) {
            boolean limitRespected = validateScpLimitsRespected(execution);
            summary.addComparison(new TnrValidationSummary.ExpectedVsActual(
                    "scp.limitRespected",
                    true,
                    limitRespected,
                    limitRespected,
                    null
            ));

            if (!limitRespected) {
                summary.addRecommendation("SCP power limits were not respected during charging.");
            }
        }
    }

    private void validateCustomAssertions(TnrExecution execution,
                                           List<TnrExpectedResults.TnrAssertion> assertions,
                                           TnrValidationSummary summary) {
        for (TnrExpectedResults.TnrAssertion assertion : assertions) {
            Object actualValue = resolveField(execution, assertion.getField());
            boolean passed = assertion.evaluate(actualValue);

            summary.addComparison(new TnrValidationSummary.ExpectedVsActual(
                    assertion.getField(),
                    assertion.getExpected(),
                    actualValue,
                    passed,
                    calculateDiff(assertion.getExpected(), actualValue)
            ));

            if (!passed && assertion.getErrorMessage() != null) {
                summary.addRecommendation(assertion.getErrorMessage());
            }
        }
    }

    private int countOcppErrors(TnrExecution execution) {
        if (execution.getEvents() == null) return 0;
        return (int) execution.getEvents().stream()
                .filter(e -> "ERROR".equals(e.getType()) ||
                            (e.getCategory() != null && e.getCategory().name().contains("ERROR")))
                .count();
    }

    private double extractTotalEnergy(TnrExecution execution) {
        if (execution.getEvents() == null) return 0.0;

        return execution.getEvents().stream()
                .filter(e -> "MeterValues".equals(e.getAction()) && e.getPayload() != null)
                .mapToDouble(e -> {
                    Object payload = e.getPayload();
                    if (payload instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payloadMap = (Map<String, Object>) payload;
                        Object energy = payloadMap.get("energyKwh");
                        if (energy instanceof Number) {
                            return ((Number) energy).doubleValue();
                        }
                    }
                    return 0.0;
                })
                .max()
                .orElse(0.0);
    }

    private int countScpProfiles(TnrExecution execution) {
        if (execution.getEvents() == null) return 0;
        return (int) execution.getEvents().stream()
                .filter(e -> "SetChargingProfile".equals(e.getAction()))
                .count();
    }

    private boolean validateScpLimitsRespected(TnrExecution execution) {
        // Logique simplifiée - à enrichir selon les besoins
        if (execution.getEvents() == null) return true;

        // Chercher les violations de limite
        return execution.getEvents().stream()
                .filter(e -> "PowerLimitExceeded".equals(e.getType()) ||
                            (e.getPayload() instanceof Map &&
                             ((Map<?, ?>) e.getPayload()).containsKey("limitViolation")))
                .findAny()
                .isEmpty();
    }

    private Object resolveField(TnrExecution execution, String field) {
        if (field == null || execution == null) return null;

        String[] parts = field.split("\\.");
        Object current = execution;

        for (String part : parts) {
            if (current == null) return null;

            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
            } else if (current instanceof TnrExecution) {
                TnrExecution exec = (TnrExecution) current;
                current = switch (part) {
                    case "id" -> exec.getId();
                    case "status" -> exec.getStatus();
                    case "durationMs" -> exec.getDurationMs();
                    case "events" -> exec.getEvents();
                    case "eventCount" -> exec.getEventCount();
                    case "passedSteps" -> exec.getPassedSteps();
                    case "failedSteps" -> exec.getFailedSteps();
                    case "totalSteps" -> exec.getTotalSteps();
                    case "metadata" -> exec.getMetadata();
                    default -> null;
                };
            } else if (current instanceof List) {
                try {
                    int index = Integer.parseInt(part);
                    List<?> list = (List<?>) current;
                    current = index < list.size() ? list.get(index) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current;
    }

    private Double calculateDiff(Object expected, Object actual) {
        if (expected instanceof Number && actual instanceof Number) {
            return ((Number) actual).doubleValue() - ((Number) expected).doubleValue();
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // RÉSUMÉ DE VALIDATION
    // ═══════════════════════════════════════════════════════════════════

    @lombok.Data
    public static class TnrValidationSummary {
        private String executionId;
        private String scenarioId;
        private boolean passed;
        private int totalAssertions;
        private int passedAssertions;
        private int failedAssertions;
        private List<ExpectedVsActual> comparisons = new ArrayList<>();
        private List<String> recommendations = new ArrayList<>();

        public void addComparison(ExpectedVsActual comparison) {
            comparisons.add(comparison);
            totalAssertions++;
            if (comparison.isPassed()) {
                passedAssertions++;
            } else {
                failedAssertions++;
            }
        }

        public void addRecommendation(String recommendation) {
            if (!recommendations.contains(recommendation)) {
                recommendations.add(recommendation);
            }
        }

        @lombok.Data
        @lombok.AllArgsConstructor
        public static class ExpectedVsActual {
            private String field;
            private Object expected;
            private Object actual;
            private boolean passed;
            private Double diff;
        }
    }
}
