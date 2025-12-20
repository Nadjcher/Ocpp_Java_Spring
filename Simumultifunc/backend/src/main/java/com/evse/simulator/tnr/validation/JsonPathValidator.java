package com.evse.simulator.tnr.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validateur basé sur JSONPath simplifié.
 * <p>
 * Supporte un sous-ensemble de JSONPath:
 * - $.field - Accès à un champ
 * - $.parent.child - Accès imbriqué
 * - $.array[0] - Accès par index
 * - $.array[*] - Tous les éléments
 * - $.array[?(@.field == 'value')] - Filtre
 * - $..field - Recherche récursive
 * </p>
 *
 * @example
 * <pre>
 * Object value = validator.extract(response, "$.meterValue[0].sampledValue[0].value");
 * List<Object> all = validator.extractAll(response, "$.meterValue[*].sampledValue[*]");
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonPathValidator {

    private final ObjectMapper objectMapper;

    // Pattern pour parser les segments de path
    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("^(.+?)\\[(\\d+|\\*)]$");
    private static final Pattern FILTER_PATTERN = Pattern.compile("^(.+?)\\[\\?\\(@\\.(.+?)\\s*(==|!=|>|<|>=|<=)\\s*'?(.+?)'?\\)]$");
    private static final Pattern RECURSIVE_PATTERN = Pattern.compile("^\\.\\.");

    /**
     * Extrait une valeur à partir d'un path JSONPath.
     *
     * @param json JSON source
     * @param path Chemin JSONPath
     * @return Valeur extraite ou null
     */
    public Object extract(JsonNode json, String path) {
        if (json == null || path == null) return null;

        // Normaliser le path
        String normalizedPath = normalizePath(path);

        try {
            JsonNode result = navigate(json, normalizedPath);
            return convertToJavaObject(result);
        } catch (Exception e) {
            log.debug("Error extracting path '{}': {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Extrait une valeur à partir d'une Map.
     */
    public Object extract(Map<String, Object> json, String path) {
        JsonNode jsonNode = objectMapper.valueToTree(json);
        return extract(jsonNode, path);
    }

    /**
     * Extrait toutes les valeurs correspondant à un path avec wildcard.
     *
     * @param json JSON source
     * @param path Chemin JSONPath avec wildcards
     * @return Liste des valeurs
     */
    public List<Object> extractAll(JsonNode json, String path) {
        if (json == null || path == null) return List.of();

        String normalizedPath = normalizePath(path);
        List<JsonNode> results = new ArrayList<>();

        try {
            navigateAll(json, normalizedPath, results);
        } catch (Exception e) {
            log.debug("Error extracting all for path '{}': {}", path, e.getMessage());
        }

        return results.stream()
                .map(this::convertToJavaObject)
                .toList();
    }

    /**
     * Vérifie si un path existe dans le JSON.
     */
    public boolean exists(JsonNode json, String path) {
        return extract(json, path) != null;
    }

    /**
     * Compte le nombre d'éléments correspondant à un path.
     */
    public int count(JsonNode json, String path) {
        return extractAll(json, path).size();
    }

    // =========================================================================
    // Navigation
    // =========================================================================

    private String normalizePath(String path) {
        // Retirer le $ initial si présent
        if (path.startsWith("$.")) {
            return path.substring(2);
        }
        if (path.startsWith("$")) {
            return path.substring(1);
        }
        return path;
    }

    private JsonNode navigate(JsonNode node, String path) {
        if (node == null || path.isEmpty()) return node;

        // Gestion de la recherche récursive ($..field)
        if (path.startsWith("..")) {
            String field = path.substring(2).split("\\.")[0].split("\\[")[0];
            String remaining = path.substring(2 + field.length());
            if (remaining.startsWith(".")) remaining = remaining.substring(1);

            JsonNode found = findRecursive(node, field);
            return remaining.isEmpty() ? found : navigate(found, remaining);
        }

        // Extraire le premier segment
        int dotIndex = findNextDot(path);
        String segment = dotIndex > 0 ? path.substring(0, dotIndex) : path;
        String remaining = dotIndex > 0 ? path.substring(dotIndex + 1) : "";

        // Gestion des filtres [?(@.field == 'value')]
        Matcher filterMatcher = FILTER_PATTERN.matcher(segment);
        if (filterMatcher.matches()) {
            String arrayField = filterMatcher.group(1);
            String filterField = filterMatcher.group(2);
            String operator = filterMatcher.group(3);
            String filterValue = filterMatcher.group(4);

            JsonNode array = arrayField.isEmpty() ? node : node.path(arrayField);
            JsonNode filtered = filterArray(array, filterField, operator, filterValue);
            return remaining.isEmpty() ? filtered : navigate(filtered, remaining);
        }

        // Gestion des index [n] ou [*]
        Matcher arrayMatcher = ARRAY_INDEX_PATTERN.matcher(segment);
        if (arrayMatcher.matches()) {
            String field = arrayMatcher.group(1);
            String index = arrayMatcher.group(2);

            JsonNode array = field.isEmpty() ? node : node.path(field);

            if ("*".equals(index)) {
                // Retourner le premier élément pour extract() simple
                if (array.isArray() && array.size() > 0) {
                    return remaining.isEmpty() ? array.get(0) : navigate(array.get(0), remaining);
                }
                return null;
            } else {
                int idx = Integer.parseInt(index);
                JsonNode element = array.get(idx);
                return remaining.isEmpty() ? element : navigate(element, remaining);
            }
        }

        // Accès simple à un champ
        JsonNode next = node.path(segment);
        return remaining.isEmpty() ? next : navigate(next, remaining);
    }

    private void navigateAll(JsonNode node, String path, List<JsonNode> results) {
        if (node == null) return;

        if (path.isEmpty()) {
            if (!node.isMissingNode()) {
                results.add(node);
            }
            return;
        }

        // Gestion de la recherche récursive
        if (path.startsWith("..")) {
            String field = path.substring(2).split("\\.")[0].split("\\[")[0];
            String remaining = path.substring(2 + field.length());
            if (remaining.startsWith(".")) remaining = remaining.substring(1);

            findAllRecursive(node, field, remaining, results);
            return;
        }

        // Extraire le premier segment
        int dotIndex = findNextDot(path);
        String segment = dotIndex > 0 ? path.substring(0, dotIndex) : path;
        String remaining = dotIndex > 0 ? path.substring(dotIndex + 1) : "";

        // Gestion des wildcards [*]
        Matcher arrayMatcher = ARRAY_INDEX_PATTERN.matcher(segment);
        if (arrayMatcher.matches()) {
            String field = arrayMatcher.group(1);
            String index = arrayMatcher.group(2);

            JsonNode array = field.isEmpty() ? node : node.path(field);

            if ("*".equals(index) && array.isArray()) {
                for (JsonNode element : array) {
                    navigateAll(element, remaining, results);
                }
            } else if (!index.equals("*")) {
                int idx = Integer.parseInt(index);
                if (array.isArray() && idx < array.size()) {
                    navigateAll(array.get(idx), remaining, results);
                }
            }
            return;
        }

        // Accès simple
        JsonNode next = node.path(segment);
        navigateAll(next, remaining, results);
    }

    private int findNextDot(String path) {
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == '.' && depth == 0) return i;
        }
        return -1;
    }

    private JsonNode findRecursive(JsonNode node, String field) {
        if (node == null) return null;

        // Vérifier le noeud actuel
        if (node.has(field)) {
            return node.get(field);
        }

        // Chercher dans les enfants
        if (node.isObject()) {
            for (JsonNode child : node) {
                JsonNode result = findRecursive(child, field);
                if (result != null) return result;
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                JsonNode result = findRecursive(element, field);
                if (result != null) return result;
            }
        }

        return null;
    }

    private void findAllRecursive(JsonNode node, String field, String remaining, List<JsonNode> results) {
        if (node == null) return;

        // Vérifier le noeud actuel
        if (node.has(field)) {
            navigateAll(node.get(field), remaining, results);
        }

        // Chercher dans les enfants
        if (node.isObject()) {
            for (JsonNode child : node) {
                findAllRecursive(child, field, remaining, results);
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                findAllRecursive(element, field, remaining, results);
            }
        }
    }

    private JsonNode filterArray(JsonNode array, String field, String operator, String value) {
        if (!array.isArray()) return null;

        for (JsonNode element : array) {
            JsonNode fieldValue = element.path(field);
            if (matchesFilter(fieldValue, operator, value)) {
                return element;
            }
        }

        return null;
    }

    private boolean matchesFilter(JsonNode actual, String operator, String expected) {
        if (actual == null || actual.isMissingNode()) return false;

        String actualStr = actual.asText();

        return switch (operator) {
            case "==" -> actualStr.equals(expected);
            case "!=" -> !actualStr.equals(expected);
            case ">" -> compareNumeric(actualStr, expected) > 0;
            case "<" -> compareNumeric(actualStr, expected) < 0;
            case ">=" -> compareNumeric(actualStr, expected) >= 0;
            case "<=" -> compareNumeric(actualStr, expected) <= 0;
            default -> false;
        };
    }

    private int compareNumeric(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    // =========================================================================
    // Conversion
    // =========================================================================

    private Object convertToJavaObject(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode element : node) {
                list.add(convertToJavaObject(element));
            }
            return list;
        }
        if (node.isObject()) {
            return objectMapper.convertValue(node, Map.class);
        }
        return node.asText();
    }

    // =========================================================================
    // Assertions fluides
    // =========================================================================

    /**
     * Crée un validateur fluide pour un path.
     */
    public PathAssertion assertThat(JsonNode json, String path) {
        Object value = extract(json, path);
        return new PathAssertion(path, value);
    }

    /**
     * Helper pour assertions fluides.
     */
    public static class PathAssertion {
        private final String path;
        private final Object value;

        public PathAssertion(String path, Object value) {
            this.path = path;
            this.value = value;
        }

        public PathAssertion isEqualTo(Object expected) {
            if (!equals(value, expected)) {
                throw new AssertionError(
                        String.format("Expected '%s' to be '%s' but was '%s'", path, expected, value));
            }
            return this;
        }

        public PathAssertion isNotNull() {
            if (value == null) {
                throw new AssertionError(String.format("Expected '%s' to be not null", path));
            }
            return this;
        }

        public PathAssertion isGreaterThan(Number expected) {
            if (value == null) {
                throw new AssertionError(String.format("Expected '%s' to be > %s but was null", path, expected));
            }
            double actual = Double.parseDouble(value.toString());
            if (actual <= expected.doubleValue()) {
                throw new AssertionError(
                        String.format("Expected '%s' to be > %s but was %s", path, expected, value));
            }
            return this;
        }

        public PathAssertion isLessThan(Number expected) {
            if (value == null) {
                throw new AssertionError(String.format("Expected '%s' to be < %s but was null", path, expected));
            }
            double actual = Double.parseDouble(value.toString());
            if (actual >= expected.doubleValue()) {
                throw new AssertionError(
                        String.format("Expected '%s' to be < %s but was %s", path, expected, value));
            }
            return this;
        }

        public PathAssertion contains(String substring) {
            if (value == null || !value.toString().contains(substring)) {
                throw new AssertionError(
                        String.format("Expected '%s' to contain '%s' but was '%s'", path, substring, value));
            }
            return this;
        }

        public Object getValue() {
            return value;
        }

        private boolean equals(Object a, Object b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            return a.toString().equals(b.toString());
        }
    }
}
