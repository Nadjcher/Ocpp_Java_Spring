package com.evse.simulator.tnr.steps;

import com.evse.simulator.tnr.engine.TnrContext;
import com.evse.simulator.tnr.model.OcppMessageCapture;
import com.evse.simulator.tnr.steps.annotations.*;
import com.evse.simulator.tnr.validation.*;
import com.evse.simulator.tnr.validation.ResponseValidator.Assertion;
import com.evse.simulator.tnr.validation.ResponseValidator.Matcher;
import com.evse.simulator.tnr.validation.ResponseValidator.ValidationResult;
import com.evse.simulator.tnr.validation.SequenceValidator.SequenceRule;
import com.evse.simulator.tnr.validation.SequenceValidator.SequenceValidationResult;
import com.evse.simulator.tnr.validation.EnergyValidator.EnergyValidationResult;
import com.evse.simulator.tnr.validation.EnergyValidator.MeterValuePoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Steps Gherkin pour les validations avancées.
 * <p>
 * Intègre les validateurs ResponseValidator, JsonPathValidator,
 * SequenceValidator et EnergyValidator.
 * </p>
 *
 * @example
 * <pre>
 * Feature: Validations Avancées
 *   Scenario: Vérifier cohérence énergétique
 *     Given une session de charge à 11kW pendant 1h
 *     When la session termine avec 11kWh mesurés
 *     Then l'énergie est cohérente avec tolérance 5%
 *
 *   Scenario: Vérifier séquence OCPP
 *     Given une session connectée
 *     When j'exécute le scénario de boot
 *     Then la séquence est valide:
 *       | action              | after            | count |
 *       | BootNotification    |                  | 1     |
 *       | StatusNotification  | BootNotification | >=1   |
 * </pre>
 */
@Slf4j
@Component
@StepDefinitions(category = "advanced-validation", description = "Steps pour les validations avancées")
@RequiredArgsConstructor
public class AdvancedValidationSteps {

    private final ResponseValidator responseValidator;
    private final JsonPathValidator jsonPathValidator;
    private final SequenceValidator sequenceValidator;
    private final EnergyValidator energyValidator;
    private final ObjectMapper objectMapper;

    // Clés de contexte
    private static final String CTX_LAST_RESPONSE = "lastResponse";
    private static final String CTX_ASSERTIONS = "pendingAssertions";
    private static final String CTX_SEQUENCE_RULES = "pendingSequenceRules";

    // =========================================================================
    // THEN Steps - DataTable Assertions
    // =========================================================================

    /**
     * Valide la réponse avec une DataTable d'assertions.
     *
     * @example
     * <pre>
     * Then la réponse contient:
     *   | path              | matcher     | value    |
     *   | $.status          | equals      | Accepted |
     *   | $.transactionId   | greaterThan | 0        |
     *   | $.idTagInfo.status| equals      | Accepted |
     * </pre>
     */
    @Then("la réponse contient:")
    public void thenResponseContainsTable(List<Map<String, String>> dataTable, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        if (response == null) {
            throw new AssertionError("No response in context");
        }

        JsonNode jsonResponse = objectMapper.valueToTree(response);
        List<Assertion> assertions = parseAssertionsFromTable(dataTable);

        ValidationResult result = responseValidator.validate(jsonResponse, assertions);

        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder("Validation failed:\n");
            result.getFailures().forEach(f ->
                sb.append("  - ").append(f.getMessage()).append("\n"));
            throw new AssertionError(sb.toString());
        }

        log.info("[ADVANCED-VALIDATION] {} assertions passed", result.getPassedAssertions());
    }

    /**
     * Valide une assertion JSONPath unique.
     */
    @Then("le chemin {string} {word} {string}")
    public void thenPathMatcherValue(String path, String matcherStr, String value, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        if (response == null) {
            throw new AssertionError("No response in context");
        }

        JsonNode jsonResponse = objectMapper.valueToTree(response);
        Matcher matcher = parseMatcher(matcherStr);
        Assertion assertion = Assertion.builder()
                .path(path)
                .matcher(matcher)
                .expectedValue(value)
                .build();

        var result = responseValidator.validateAssertion(jsonResponse, assertion);

        if (!result.isPassed()) {
            throw new AssertionError(result.getMessage());
        }

        log.info("[ADVANCED-VALIDATION] Path {} {} {}: PASS", path, matcherStr, value);
    }

    /**
     * Valide que le chemin existe et extrait sa valeur.
     */
    @Then("le chemin {string} existe")
    public void thenPathExists(String path, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        JsonNode jsonResponse = objectMapper.valueToTree(response);

        if (!jsonPathValidator.exists(jsonResponse, path)) {
            throw new AssertionError(String.format("Path '%s' does not exist in response", path));
        }

        log.info("[ADVANCED-VALIDATION] Path {} exists", path);
    }

    /**
     * Valide qu'un chemin n'existe pas.
     */
    @Then("le chemin {string} n'existe pas")
    public void thenPathNotExists(String path, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        JsonNode jsonResponse = objectMapper.valueToTree(response);

        if (jsonPathValidator.exists(jsonResponse, path)) {
            throw new AssertionError(String.format("Path '%s' should not exist in response", path));
        }

        log.info("[ADVANCED-VALIDATION] Path {} does not exist", path);
    }

    /**
     * Compte les éléments correspondant à un chemin.
     */
    @Then("le chemin {string} contient {int} éléments")
    public void thenPathHasSize(String path, int expectedSize, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        JsonNode jsonResponse = objectMapper.valueToTree(response);
        int actualSize = jsonPathValidator.count(jsonResponse, path);

        if (actualSize != expectedSize) {
            throw new AssertionError(String.format(
                    "Expected %d elements at path '%s' but found %d",
                    expectedSize, path, actualSize));
        }

        log.info("[ADVANCED-VALIDATION] Path {} has {} elements", path, actualSize);
    }

    // =========================================================================
    // THEN Steps - Sequence Validation
    // =========================================================================

    /**
     * Valide une séquence de messages OCPP.
     *
     * @example
     * <pre>
     * Then la séquence est valide:
     *   | action              | after            | within | count |
     *   | BootNotification    |                  | 5s     | 1     |
     *   | StatusNotification  | BootNotification | 10s    | >=1   |
     *   | Heartbeat           |                  | 60s    | >=1   |
     * </pre>
     */
    @Then("la séquence est valide:")
    public void thenSequenceIsValid(List<Map<String, String>> dataTable, TnrContext context) {
        List<OcppMessageCapture> messages = context.getMessageHistory();
        List<SequenceRule> rules = parseSequenceRulesFromTable(dataTable);

        SequenceValidationResult result = sequenceValidator.validate(messages, rules);

        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder("Sequence validation failed:\n");
            result.getFailures().forEach(f ->
                sb.append("  - ").append(f.getMessage()).append("\n"));
            throw new AssertionError(sb.toString());
        }

        log.info("[ADVANCED-VALIDATION] Sequence validated: {}", result.getSummary());
    }

    /**
     * Vérifie qu'un message apparaît après un autre.
     */
    @Then("{word} apparaît après {word}")
    public void thenActionAfterAction(String action, String afterAction, TnrContext context) {
        List<OcppMessageCapture> messages = context.getMessageHistory();

        int actionIndex = findFirstMessageIndex(messages, action);
        int afterIndex = findFirstMessageIndex(messages, afterAction);

        if (actionIndex < 0) {
            throw new AssertionError(String.format("Message '%s' not found in history", action));
        }
        if (afterIndex < 0) {
            throw new AssertionError(String.format("Message '%s' not found in history", afterAction));
        }
        if (actionIndex <= afterIndex) {
            throw new AssertionError(String.format(
                    "Expected '%s' (index %d) to appear after '%s' (index %d)",
                    action, actionIndex, afterAction, afterIndex));
        }

        log.info("[ADVANCED-VALIDATION] {} appears after {}", action, afterAction);
    }

    /**
     * Vérifie le nombre de messages d'un type.
     */
    @Then("il y a exactement {int} message(s) {word}")
    public void thenExactlyNMessages(int count, String action, TnrContext context) {
        List<OcppMessageCapture> messages = context.getMessagesByAction(action);

        if (messages.size() != count) {
            throw new AssertionError(String.format(
                    "Expected exactly %d '%s' messages but found %d",
                    count, action, messages.size()));
        }

        log.info("[ADVANCED-VALIDATION] Found exactly {} {} messages", count, action);
    }

    // =========================================================================
    // THEN Steps - Energy Validation
    // =========================================================================

    /**
     * Configure une charge pour validation énergétique.
     */
    @Given("une session de charge à {double}kW pendant {int}h")
    public void givenChargingSession(double powerKw, int durationHours, TnrContext context) {
        context.set("expectedPowerW", powerKw * 1000);
        context.set("expectedDurationSec", durationHours * 3600L);
        context.set("expectedEnergyWh", powerKw * 1000 * durationHours);

        log.info("[ADVANCED-VALIDATION] Set up charging: {}kW for {}h = {}kWh",
                powerKw, durationHours, powerKw * durationHours);
    }

    /**
     * Définit l'énergie mesurée.
     */
    @When("la session termine avec {double}kWh mesurés")
    public void whenSessionEndsWithEnergy(double energyKwh, TnrContext context) {
        context.set("actualEnergyWh", energyKwh * 1000);

        log.info("[ADVANCED-VALIDATION] Measured energy: {}kWh", energyKwh);
    }

    /**
     * Vérifie la cohérence énergétique avec tolérance.
     */
    @Then("l'énergie est cohérente avec tolérance {double}%")
    public void thenEnergyConsistentWithTolerance(double tolerancePercent, TnrContext context) {
        Double powerW = context.get("expectedPowerW");
        Long durationSec = context.get("expectedDurationSec");
        Double actualEnergyWh = context.get("actualEnergyWh");

        if (powerW == null || durationSec == null || actualEnergyWh == null) {
            throw new AssertionError("Missing energy context. Use 'une session de charge' first.");
        }

        EnergyValidationResult result = energyValidator.validateWithDetails(
                powerW, durationSec, actualEnergyWh, tolerancePercent);

        if (!result.isValid()) {
            throw new AssertionError(String.format(
                    "Energy inconsistency: %s", result.getSummary()));
        }

        log.info("[ADVANCED-VALIDATION] Energy consistent: {}", result.getSummary());
    }

    /**
     * Vérifie la cohérence énergétique simple.
     */
    @Then("l'énergie mesurée {double}Wh est cohérente avec {double}W pendant {long}s")
    public void thenEnergyConsistent(double energyWh, double powerW, long durationSec, TnrContext context) {
        boolean valid = energyValidator.validateEnergyConsistency(powerW, durationSec, energyWh);

        if (!valid) {
            double expected = energyValidator.calculateExpectedEnergy(powerW, durationSec);
            throw new AssertionError(String.format(
                    "Energy inconsistency: expected %.2fWh, got %.2fWh (P=%.1fW, t=%ds)",
                    expected, energyWh, powerW, durationSec));
        }

        log.info("[ADVANCED-VALIDATION] Energy consistent: {}W × {}s = {}Wh", powerW, durationSec, energyWh);
    }

    /**
     * Vérifie la cohérence SoC.
     */
    @Then("le SoC est cohérent entre {double}% et {double}% avec {double}Wh et capacité {double}Wh")
    public void thenSocConsistent(double startSoc, double endSoc, double energyWh,
                                   double capacityWh, TnrContext context) {
        boolean valid = energyValidator.validateSocConsistency(
                startSoc, endSoc, energyWh, capacityWh, 5.0);

        if (!valid) {
            double expectedDelta = (energyWh / capacityWh) * 100;
            double actualDelta = endSoc - startSoc;
            throw new AssertionError(String.format(
                    "SoC inconsistency: expected delta %.1f%%, got %.1f%% (%.0fWh / %.0fWh)",
                    expectedDelta, actualDelta, energyWh, capacityWh));
        }

        log.info("[ADVANCED-VALIDATION] SoC consistent: {}% -> {}% with {}Wh", startSoc, endSoc, energyWh);
    }

    /**
     * Valide une série de MeterValues.
     */
    @Then("les MeterValues sont cohérentes")
    public void thenMeterValuesConsistent(TnrContext context) {
        List<Map<String, Object>> meterValuesRaw = context.get("meterValues");

        if (meterValuesRaw == null || meterValuesRaw.isEmpty()) {
            throw new AssertionError("No meter values in context");
        }

        List<MeterValuePoint> points = meterValuesRaw.stream()
                .map(this::mapToMeterValuePoint)
                .collect(Collectors.toList());

        var result = energyValidator.validateMeterValuesSeries(points, 10.0);

        if (!result.isValid()) {
            StringBuilder sb = new StringBuilder("MeterValues validation failed:\n");
            result.getErrors().forEach(e -> sb.append("  - ").append(e).append("\n"));
            throw new AssertionError(sb.toString());
        }

        log.info("[ADVANCED-VALIDATION] MeterValues consistent: {} points validated", points.size());
    }

    // =========================================================================
    // THEN Steps - Power Validation
    // =========================================================================

    /**
     * Vérifie que la puissance ne dépasse pas une limite.
     */
    @Then("la puissance ne dépasse pas {double}W")
    public void thenPowerNotExceeds(double maxPowerW, TnrContext context) {
        Double actualPower = context.get("currentPowerW");

        if (actualPower == null) {
            throw new AssertionError("No power value in context");
        }

        if (!energyValidator.validatePowerLimit(actualPower, maxPowerW)) {
            throw new AssertionError(String.format(
                    "Power limit exceeded: %.1fW > %.1fW", actualPower, maxPowerW));
        }

        log.info("[ADVANCED-VALIDATION] Power within limit: {}W <= {}W", actualPower, maxPowerW);
    }

    /**
     * Vérifie que le courant ne dépasse pas une limite.
     */
    @Then("le courant ne dépasse pas {double}A")
    public void thenCurrentNotExceeds(double maxCurrentA, TnrContext context) {
        Double actualCurrent = context.get("currentA");

        if (actualCurrent == null) {
            throw new AssertionError("No current value in context");
        }

        if (!energyValidator.validateCurrentLimit(actualCurrent, maxCurrentA)) {
            throw new AssertionError(String.format(
                    "Current limit exceeded: %.1fA > %.1fA", actualCurrent, maxCurrentA));
        }

        log.info("[ADVANCED-VALIDATION] Current within limit: {}A <= {}A", actualCurrent, maxCurrentA);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private List<Assertion> parseAssertionsFromTable(List<Map<String, String>> dataTable) {
        List<Assertion> assertions = new ArrayList<>();

        for (Map<String, String> row : dataTable) {
            String path = row.get("path");
            String matcherStr = row.get("matcher");
            String value = row.get("value");
            String min = row.get("min");
            String max = row.get("max");

            Matcher matcher = parseMatcher(matcherStr);

            Assertion.AssertionBuilder builder = Assertion.builder()
                    .path(path)
                    .matcher(matcher);

            if (value != null && !value.isEmpty()) {
                builder.expectedValue(value);
            }
            if (min != null && !min.isEmpty()) {
                builder.minValue(Double.parseDouble(min));
            }
            if (max != null && !max.isEmpty()) {
                builder.maxValue(Double.parseDouble(max));
            }

            assertions.add(builder.build());
        }

        return assertions;
    }

    private Matcher parseMatcher(String matcherStr) {
        return switch (matcherStr.toLowerCase()) {
            case "equals", "eq", "=" -> Matcher.EQUALS;
            case "notequals", "neq", "!=" -> Matcher.NOT_EQUALS;
            case "contains" -> Matcher.CONTAINS;
            case "notcontains" -> Matcher.NOT_CONTAINS;
            case "startswith" -> Matcher.STARTS_WITH;
            case "endswith" -> Matcher.ENDS_WITH;
            case "matches", "regex" -> Matcher.MATCHES;
            case "greaterthan", "gt", ">" -> Matcher.GREATER_THAN;
            case "greaterorequal", "gte", ">=" -> Matcher.GREATER_OR_EQUAL;
            case "lessthan", "lt", "<" -> Matcher.LESS_THAN;
            case "lessorequal", "lte", "<=" -> Matcher.LESS_OR_EQUAL;
            case "between" -> Matcher.BETWEEN;
            case "isnull", "null" -> Matcher.IS_NULL;
            case "isnotnull", "notnull" -> Matcher.IS_NOT_NULL;
            case "isempty", "empty" -> Matcher.IS_EMPTY;
            case "isnotempty", "notempty" -> Matcher.IS_NOT_EMPTY;
            case "hassize", "size" -> Matcher.HAS_SIZE;
            case "istrue", "true" -> Matcher.IS_TRUE;
            case "isfalse", "false" -> Matcher.IS_FALSE;
            case "typeof", "type" -> Matcher.TYPE_OF;
            default -> throw new IllegalArgumentException("Unknown matcher: " + matcherStr);
        };
    }

    private List<SequenceRule> parseSequenceRulesFromTable(List<Map<String, String>> dataTable) {
        List<SequenceRule> rules = new ArrayList<>();

        for (Map<String, String> row : dataTable) {
            String action = row.get("action");
            String after = row.get("after");
            String within = row.get("within");
            String count = row.get("count");

            SequenceValidator.SequenceRuleBuilder builder = SequenceValidator.expect(action);

            if (after != null && !after.isEmpty()) {
                builder.after(after);
            }

            if (within != null && !within.isEmpty()) {
                builder.within(parseDuration(within));
            }

            if (count != null && !count.isEmpty()) {
                parseCount(count, builder);
            }

            rules.add(builder.build());
        }

        return rules;
    }

    private Duration parseDuration(String durationStr) {
        String value = durationStr.replaceAll("[^0-9]", "");
        String unit = durationStr.replaceAll("[0-9]", "").toLowerCase();

        long amount = Long.parseLong(value);

        return switch (unit) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m", "min" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> Duration.ofSeconds(amount);
        };
    }

    private void parseCount(String countStr, SequenceValidator.SequenceRuleBuilder builder) {
        if (countStr.startsWith(">=")) {
            builder.atLeast(Integer.parseInt(countStr.substring(2).trim()));
        } else if (countStr.startsWith("<=")) {
            builder.atMost(Integer.parseInt(countStr.substring(2).trim()));
        } else if (countStr.startsWith(">")) {
            builder.atLeast(Integer.parseInt(countStr.substring(1).trim()) + 1);
        } else if (countStr.startsWith("<")) {
            builder.atMost(Integer.parseInt(countStr.substring(1).trim()) - 1);
        } else {
            builder.exactly(Integer.parseInt(countStr.trim()));
        }
    }

    private int findFirstMessageIndex(List<OcppMessageCapture> messages, String action) {
        for (int i = 0; i < messages.size(); i++) {
            if (action.equalsIgnoreCase(messages.get(i).getAction())) {
                return i;
            }
        }
        return -1;
    }

    private MeterValuePoint mapToMeterValuePoint(Map<String, Object> raw) {
        return MeterValuePoint.builder()
                .timestampSec(toLong(raw.get("timestamp")))
                .powerW(toDouble(raw.get("power")))
                .energyWh(toDouble(raw.get("energy")))
                .socPercent(toDouble(raw.get("soc")))
                .currentA(toDouble(raw.get("current")))
                .voltageV(toDouble(raw.get("voltage")))
                .build();
    }

    private long toLong(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private double toDouble(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }
}
