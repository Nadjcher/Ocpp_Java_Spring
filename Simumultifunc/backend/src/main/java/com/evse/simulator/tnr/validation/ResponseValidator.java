package com.evse.simulator.tnr.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validateur de réponses OCPP avec support de multiples assertions.
 * <p>
 * Supporte les matchers: equals, notEquals, contains, matches (regex),
 * greaterThan, lessThan, between, isNull, isNotNull, isEmpty, isNotEmpty, hasSize
 * </p>
 *
 * @example
 * <pre>
 * ValidationResult result = validator.validate(response, List.of(
 *     Assertion.equals("$.status", "Accepted"),
 *     Assertion.greaterThan("$.transactionId", 0)
 * ));
 * </pre>
 */
@Slf4j
@Component
public class ResponseValidator {

    private final ObjectMapper objectMapper;
    private final JsonPathValidator jsonPathValidator;

    public ResponseValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jsonPathValidator = new JsonPathValidator(objectMapper);
    }

    /**
     * Valide une réponse contre une liste d'assertions.
     *
     * @param response   Réponse JSON à valider
     * @param assertions Liste des assertions
     * @return Résultat de validation
     */
    public ValidationResult validate(JsonNode response, List<Assertion> assertions) {
        ValidationResult result = new ValidationResult();
        result.setTotalAssertions(assertions.size());

        for (Assertion assertion : assertions) {
            AssertionResult assertionResult = validateAssertion(response, assertion);
            result.addResult(assertionResult);
        }

        result.calculateSummary();
        return result;
    }

    /**
     * Valide une réponse (Map) contre une liste d'assertions.
     */
    public ValidationResult validate(Map<String, Object> response, List<Assertion> assertions) {
        JsonNode jsonNode = objectMapper.valueToTree(response);
        return validate(jsonNode, assertions);
    }

    /**
     * Valide une seule assertion.
     */
    public AssertionResult validateAssertion(JsonNode response, Assertion assertion) {
        AssertionResult result = AssertionResult.builder()
                .assertion(assertion)
                .build();

        try {
            Object actualValue = jsonPathValidator.extract(response, assertion.getPath());
            result.setActualValue(actualValue);

            boolean passed = evaluateMatcher(actualValue, assertion);
            result.setPassed(passed);

            if (!passed) {
                result.setMessage(buildFailureMessage(assertion, actualValue));
            }

        } catch (Exception e) {
            result.setPassed(false);
            result.setMessage("Error evaluating path '" + assertion.getPath() + "': " + e.getMessage());
            result.setError(e.getMessage());
        }

        return result;
    }

    /**
     * Évalue un matcher contre une valeur.
     */
    private boolean evaluateMatcher(Object actual, Assertion assertion) {
        Matcher matcher = assertion.getMatcher();
        Object expected = assertion.getExpectedValue();

        return switch (matcher) {
            case EQUALS -> equals(actual, expected);
            case NOT_EQUALS -> !equals(actual, expected);
            case CONTAINS -> contains(actual, expected);
            case NOT_CONTAINS -> !contains(actual, expected);
            case STARTS_WITH -> startsWith(actual, expected);
            case ENDS_WITH -> endsWith(actual, expected);
            case MATCHES -> matches(actual, expected);
            case GREATER_THAN -> compare(actual, expected) > 0;
            case GREATER_OR_EQUAL -> compare(actual, expected) >= 0;
            case LESS_THAN -> compare(actual, expected) < 0;
            case LESS_OR_EQUAL -> compare(actual, expected) <= 0;
            case BETWEEN -> between(actual, assertion.getMinValue(), assertion.getMaxValue());
            case IS_NULL -> actual == null;
            case IS_NOT_NULL -> actual != null;
            case IS_EMPTY -> isEmpty(actual);
            case IS_NOT_EMPTY -> !isEmpty(actual);
            case HAS_SIZE -> hasSize(actual, expected);
            case IS_TRUE -> Boolean.TRUE.equals(actual);
            case IS_FALSE -> Boolean.FALSE.equals(actual);
            case TYPE_OF -> isTypeOf(actual, expected);
        };
    }

    // =========================================================================
    // Comparaison helpers
    // =========================================================================

    private boolean equals(Object actual, Object expected) {
        if (actual == null && expected == null) return true;
        if (actual == null || expected == null) return false;

        // Comparaison de strings (case-insensitive pour certains cas)
        String actualStr = actual.toString();
        String expectedStr = expected.toString();

        // Comparaison numérique si applicable
        try {
            double actualNum = Double.parseDouble(actualStr);
            double expectedNum = Double.parseDouble(expectedStr);
            return Math.abs(actualNum - expectedNum) < 0.0001;
        } catch (NumberFormatException ignored) {}

        return actualStr.equals(expectedStr);
    }

    private boolean contains(Object actual, Object expected) {
        if (actual == null) return false;

        if (actual instanceof ArrayNode) {
            ArrayNode array = (ArrayNode) actual;
            for (JsonNode node : array) {
                if (equals(node.asText(), expected)) {
                    return true;
                }
            }
            return false;
        }

        return actual.toString().contains(expected.toString());
    }

    private boolean startsWith(Object actual, Object expected) {
        if (actual == null) return false;
        return actual.toString().startsWith(expected.toString());
    }

    private boolean endsWith(Object actual, Object expected) {
        if (actual == null) return false;
        return actual.toString().endsWith(expected.toString());
    }

    private boolean matches(Object actual, Object expected) {
        if (actual == null) return false;
        Pattern pattern = Pattern.compile(expected.toString());
        return pattern.matcher(actual.toString()).matches();
    }

    private int compare(Object actual, Object expected) {
        if (actual == null) return -1;

        try {
            double actualNum = Double.parseDouble(actual.toString());
            double expectedNum = Double.parseDouble(expected.toString());
            return Double.compare(actualNum, expectedNum);
        } catch (NumberFormatException e) {
            return actual.toString().compareTo(expected.toString());
        }
    }

    private boolean between(Object actual, Object min, Object max) {
        if (actual == null || min == null || max == null) return false;

        try {
            double actualNum = Double.parseDouble(actual.toString());
            double minNum = Double.parseDouble(min.toString());
            double maxNum = Double.parseDouble(max.toString());
            return actualNum >= minNum && actualNum <= maxNum;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isEmpty(Object actual) {
        if (actual == null) return true;
        if (actual instanceof String) return ((String) actual).isEmpty();
        if (actual instanceof ArrayNode) return ((ArrayNode) actual).isEmpty();
        if (actual instanceof JsonNode) return ((JsonNode) actual).isEmpty();
        return actual.toString().isEmpty();
    }

    private boolean hasSize(Object actual, Object expected) {
        if (actual == null || expected == null) return false;

        int expectedSize = Integer.parseInt(expected.toString());

        if (actual instanceof ArrayNode) {
            return ((ArrayNode) actual).size() == expectedSize;
        }
        if (actual instanceof String) {
            return ((String) actual).length() == expectedSize;
        }

        return false;
    }

    private boolean isTypeOf(Object actual, Object expected) {
        if (actual == null) return "null".equalsIgnoreCase(expected.toString());

        String expectedType = expected.toString().toLowerCase();
        return switch (expectedType) {
            case "string" -> actual instanceof String;
            case "number", "int", "integer", "double" -> {
                try {
                    Double.parseDouble(actual.toString());
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "boolean" -> actual instanceof Boolean ||
                    "true".equalsIgnoreCase(actual.toString()) ||
                    "false".equalsIgnoreCase(actual.toString());
            case "array" -> actual instanceof ArrayNode;
            case "object" -> actual instanceof JsonNode && !((JsonNode) actual).isValueNode();
            default -> false;
        };
    }

    /**
     * Construit un message d'erreur détaillé.
     */
    private String buildFailureMessage(Assertion assertion, Object actual) {
        String matcherDesc = switch (assertion.getMatcher()) {
            case EQUALS -> "to equal '" + assertion.getExpectedValue() + "'";
            case NOT_EQUALS -> "to not equal '" + assertion.getExpectedValue() + "'";
            case CONTAINS -> "to contain '" + assertion.getExpectedValue() + "'";
            case NOT_CONTAINS -> "to not contain '" + assertion.getExpectedValue() + "'";
            case STARTS_WITH -> "to start with '" + assertion.getExpectedValue() + "'";
            case ENDS_WITH -> "to end with '" + assertion.getExpectedValue() + "'";
            case MATCHES -> "to match pattern '" + assertion.getExpectedValue() + "'";
            case GREATER_THAN -> "to be > " + assertion.getExpectedValue();
            case GREATER_OR_EQUAL -> "to be >= " + assertion.getExpectedValue();
            case LESS_THAN -> "to be < " + assertion.getExpectedValue();
            case LESS_OR_EQUAL -> "to be <= " + assertion.getExpectedValue();
            case BETWEEN -> "to be between " + assertion.getMinValue() + " and " + assertion.getMaxValue();
            case IS_NULL -> "to be null";
            case IS_NOT_NULL -> "to not be null";
            case IS_EMPTY -> "to be empty";
            case IS_NOT_EMPTY -> "to not be empty";
            case HAS_SIZE -> "to have size " + assertion.getExpectedValue();
            case IS_TRUE -> "to be true";
            case IS_FALSE -> "to be false";
            case TYPE_OF -> "to be of type '" + assertion.getExpectedValue() + "'";
        };

        return String.format("Expected '%s' %s, but was '%s'",
                assertion.getPath(), matcherDesc, actual);
    }

    // =========================================================================
    // Inner Classes
    // =========================================================================

    /**
     * Types de matchers disponibles.
     */
    public enum Matcher {
        EQUALS,
        NOT_EQUALS,
        CONTAINS,
        NOT_CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        MATCHES,
        GREATER_THAN,
        GREATER_OR_EQUAL,
        LESS_THAN,
        LESS_OR_EQUAL,
        BETWEEN,
        IS_NULL,
        IS_NOT_NULL,
        IS_EMPTY,
        IS_NOT_EMPTY,
        HAS_SIZE,
        IS_TRUE,
        IS_FALSE,
        TYPE_OF
    }

    /**
     * Définition d'une assertion.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Assertion {
        private String path;
        private Matcher matcher;
        private Object expectedValue;
        private Object minValue;  // Pour BETWEEN
        private Object maxValue;  // Pour BETWEEN
        private String description;

        // Factory methods
        public static Assertion equals(String path, Object value) {
            return Assertion.builder().path(path).matcher(Matcher.EQUALS).expectedValue(value).build();
        }

        public static Assertion notEquals(String path, Object value) {
            return Assertion.builder().path(path).matcher(Matcher.NOT_EQUALS).expectedValue(value).build();
        }

        public static Assertion contains(String path, Object value) {
            return Assertion.builder().path(path).matcher(Matcher.CONTAINS).expectedValue(value).build();
        }

        public static Assertion matches(String path, String regex) {
            return Assertion.builder().path(path).matcher(Matcher.MATCHES).expectedValue(regex).build();
        }

        public static Assertion greaterThan(String path, Number value) {
            return Assertion.builder().path(path).matcher(Matcher.GREATER_THAN).expectedValue(value).build();
        }

        public static Assertion lessThan(String path, Number value) {
            return Assertion.builder().path(path).matcher(Matcher.LESS_THAN).expectedValue(value).build();
        }

        public static Assertion between(String path, Number min, Number max) {
            return Assertion.builder().path(path).matcher(Matcher.BETWEEN).minValue(min).maxValue(max).build();
        }

        public static Assertion isNull(String path) {
            return Assertion.builder().path(path).matcher(Matcher.IS_NULL).build();
        }

        public static Assertion isNotNull(String path) {
            return Assertion.builder().path(path).matcher(Matcher.IS_NOT_NULL).build();
        }

        public static Assertion isEmpty(String path) {
            return Assertion.builder().path(path).matcher(Matcher.IS_EMPTY).build();
        }

        public static Assertion isNotEmpty(String path) {
            return Assertion.builder().path(path).matcher(Matcher.IS_NOT_EMPTY).build();
        }

        public static Assertion hasSize(String path, int size) {
            return Assertion.builder().path(path).matcher(Matcher.HAS_SIZE).expectedValue(size).build();
        }
    }

    /**
     * Résultat d'une assertion.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssertionResult {
        private Assertion assertion;
        private boolean passed;
        private Object actualValue;
        private String message;
        private String error;
    }

    /**
     * Résultat global de validation.
     */
    @Data
    public static class ValidationResult {
        private int totalAssertions;
        private int passedAssertions;
        private int failedAssertions;
        private boolean success;
        private List<AssertionResult> results = new ArrayList<>();

        public void addResult(AssertionResult result) {
            results.add(result);
        }

        public void calculateSummary() {
            passedAssertions = (int) results.stream().filter(AssertionResult::isPassed).count();
            failedAssertions = totalAssertions - passedAssertions;
            success = failedAssertions == 0;
        }

        public List<AssertionResult> getFailures() {
            return results.stream().filter(r -> !r.isPassed()).toList();
        }

        public String getSummary() {
            return String.format("%d/%d assertions passed%s",
                    passedAssertions, totalAssertions,
                    success ? "" : " (" + failedAssertions + " failed)");
        }
    }
}
