package com.evse.simulator.tnr.steps;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.service.SessionService;
import com.evse.simulator.tnr.engine.TnrContext;
import com.evse.simulator.tnr.model.OcppMessageCapture;
import com.evse.simulator.tnr.steps.annotations.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Steps Gherkin pour les validations et assertions.
 * <p>
 * Permet de vérifier les réponses OCPP, les états, les valeurs
 * et les performances.
 * </p>
 *
 * @example
 * <pre>
 * Feature: Validations
 *   Scenario: Vérifier réponse BootNotification
 *     Given une session connectée
 *     When j'envoie BootNotification
 *     Then le status est "Accepted"
 *     And la réponse contient "interval"
 *     And le temps de réponse est < 500ms
 * </pre>
 */
@Slf4j
@Component
@StepDefinitions(category = "validation", description = "Steps pour les validations et assertions")
@RequiredArgsConstructor
public class ValidationSteps {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    // Clés de contexte
    private static final String CTX_LAST_RESPONSE = "lastResponse";
    private static final String CTX_LAST_RESPONSE_TIME = "lastResponseTimeMs";

    // =========================================================================
    // THEN Steps - Status
    // =========================================================================

    @Then("le status est {string}")
    public void thenStatusIs(String expectedStatus, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        if (response == null) {
            throw new AssertionError("No response in context");
        }

        String actualStatus = extractStatus(response);

        if (!expectedStatus.equalsIgnoreCase(actualStatus)) {
            throw new AssertionError(
                    String.format("Expected status '%s' but got '%s'", expectedStatus, actualStatus));
        }

        log.info("[VALIDATION] Status verified: {}", actualStatus);
    }

    @Then("le status n'est pas {string}")
    public void thenStatusIsNot(String unexpectedStatus, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        if (response == null) {
            throw new AssertionError("No response in context");
        }

        String actualStatus = extractStatus(response);

        if (unexpectedStatus.equalsIgnoreCase(actualStatus)) {
            throw new AssertionError(
                    String.format("Status should not be '%s'", unexpectedStatus));
        }

        log.info("[VALIDATION] Status verified: not {}", unexpectedStatus);
    }

    // =========================================================================
    // THEN Steps - Response Content
    // =========================================================================

    @Then("la réponse contient {string}")
    public void thenResponseContains(String key, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        if (response == null) {
            throw new AssertionError("No response in context");
        }

        if (!containsKey(response, key)) {
            throw new AssertionError(
                    String.format("Response does not contain key '%s'. Response: %s", key, response));
        }

        log.info("[VALIDATION] Response contains key: {}", key);
    }

    @Then("la réponse ne contient pas {string}")
    public void thenResponseNotContains(String key, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        if (response == null) {
            throw new AssertionError("No response in context");
        }

        if (containsKey(response, key)) {
            throw new AssertionError(
                    String.format("Response should not contain key '%s'", key));
        }

        log.info("[VALIDATION] Response does not contain key: {}", key);
    }

    @Then("la valeur de {string} est {string}")
    public void thenValueIs(String path, String expectedValue, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        if (response == null) {
            throw new AssertionError("No response in context");
        }

        Object actualValue = getValueByPath(response, path);

        if (actualValue == null) {
            throw new AssertionError(
                    String.format("Value at path '%s' is null", path));
        }

        // Résoudre les variables
        String resolved = context.resolveVariables(expectedValue);

        if (!String.valueOf(actualValue).equals(resolved)) {
            throw new AssertionError(
                    String.format("Expected '%s' at path '%s' but got '%s'", resolved, path, actualValue));
        }

        log.info("[VALIDATION] Value at {} = {}", path, actualValue);
    }

    @Then("la valeur de {string} est > {int}")
    public void thenValueGreaterThan(String path, int expected, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);
        Object value = getValueByPath(response, path);

        if (value == null) {
            throw new AssertionError(String.format("Value at path '%s' is null", path));
        }

        double actual = ((Number) value).doubleValue();
        if (actual <= expected) {
            throw new AssertionError(
                    String.format("Expected %s > %d but got %f", path, expected, actual));
        }

        log.info("[VALIDATION] {} = {} > {}", path, actual, expected);
    }

    @Then("la valeur de {string} est < {int}")
    public void thenValueLessThan(String path, int expected, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);
        Object value = getValueByPath(response, path);

        if (value == null) {
            throw new AssertionError(String.format("Value at path '%s' is null", path));
        }

        double actual = ((Number) value).doubleValue();
        if (actual >= expected) {
            throw new AssertionError(
                    String.format("Expected %s < %d but got %f", path, expected, actual));
        }

        log.info("[VALIDATION] {} = {} < {}", path, actual, expected);
    }

    // =========================================================================
    // THEN Steps - Transaction
    // =========================================================================

    @Then("le transactionId est > 0")
    public void thenTransactionIdPositive(TnrContext context) {
        Integer transactionId = context.get("transactionId");

        if (transactionId == null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = context.get(CTX_LAST_RESPONSE);
            if (response != null) {
                transactionId = (Integer) response.get("transactionId");
            }
        }

        if (transactionId == null || transactionId <= 0) {
            throw new AssertionError(
                    String.format("Expected positive transactionId but got %s", transactionId));
        }

        log.info("[VALIDATION] TransactionId = {} (> 0)", transactionId);
    }

    @Then("le transactionId est stocké")
    public void thenTransactionIdStored(TnrContext context) {
        Integer transactionId = context.get("transactionId");

        if (transactionId == null) {
            throw new AssertionError("TransactionId not found in context");
        }

        log.info("[VALIDATION] TransactionId {} stored in context", transactionId);
    }

    // =========================================================================
    // THEN Steps - Performance
    // =========================================================================

    @Then("le temps de réponse est < {int}ms")
    public void thenResponseTimeLessThan(int maxMs, TnrContext context) {
        Long responseTime = context.get(CTX_LAST_RESPONSE_TIME);

        if (responseTime == null) {
            throw new AssertionError("No response time in context");
        }

        if (responseTime >= maxMs) {
            throw new AssertionError(
                    String.format("Expected response time < %dms but got %dms", maxMs, responseTime));
        }

        log.info("[VALIDATION] Response time {}ms < {}ms", responseTime, maxMs);
    }

    @Then("le temps de réponse est > {int}ms")
    public void thenResponseTimeGreaterThan(int minMs, TnrContext context) {
        Long responseTime = context.get(CTX_LAST_RESPONSE_TIME);

        if (responseTime == null) {
            throw new AssertionError("No response time in context");
        }

        if (responseTime <= minMs) {
            throw new AssertionError(
                    String.format("Expected response time > %dms but got %dms", minMs, responseTime));
        }

        log.info("[VALIDATION] Response time {}ms > {}ms", responseTime, minMs);
    }

    @Then("le temps de réponse moyen est < {int}ms")
    public void thenAverageResponseTimeLessThan(int maxMs, TnrContext context) {
        List<Long> times = context.getList("heartbeatTimes");

        if (times == null || times.isEmpty()) {
            throw new AssertionError("No response times collected");
        }

        double average = times.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        if (average >= maxMs) {
            throw new AssertionError(
                    String.format("Expected average response time < %dms but got %.1fms", maxMs, average));
        }

        log.info("[VALIDATION] Average response time {:.1f}ms < {}ms", average, maxMs);
    }

    // =========================================================================
    // THEN Steps - Connector Status
    // =========================================================================

    @Then("l'état du connecteur est {string}")
    public void thenConnectorStatus(String expectedStatus, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);

        // Essayer de récupérer du contexte ou de la dernière réponse StatusNotification
        String actualStatus = context.get("connectorStatus");

        if (actualStatus == null) {
            // Fallback sur l'état de la session
            actualStatus = session.getState().name();
        }

        // Normaliser pour comparaison
        String normalizedExpected = expectedStatus.toUpperCase().replace("-", "_");
        String normalizedActual = actualStatus.toUpperCase().replace("-", "_");

        if (!normalizedExpected.equals(normalizedActual)) {
            throw new AssertionError(
                    String.format("Expected connector status '%s' but got '%s'", expectedStatus, actualStatus));
        }

        log.info("[VALIDATION] Connector status = {}", actualStatus);
    }

    // =========================================================================
    // THEN Steps - Message Count
    // =========================================================================

    @Then("{int} messages {word} ont été envoyés")
    public void thenMessageCountSent(int expectedCount, String action, TnrContext context) {
        List<OcppMessageCapture> messages = context.getMessagesByAction(action);
        int count = messages.size();

        if (count != expectedCount) {
            throw new AssertionError(
                    String.format("Expected %d %s messages but got %d", expectedCount, action, count));
        }

        log.info("[VALIDATION] {} {} messages sent", count, action);
    }

    @Then("au moins {int} messages {word} ont été envoyés")
    public void thenAtLeastMessageCount(int minCount, String action, TnrContext context) {
        List<OcppMessageCapture> messages = context.getMessagesByAction(action);
        int count = messages.size();

        if (count < minCount) {
            throw new AssertionError(
                    String.format("Expected at least %d %s messages but got %d", minCount, action, count));
        }

        log.info("[VALIDATION] At least {} {} messages sent (actual: {})", minCount, action, count);
    }

    // =========================================================================
    // THEN Steps - Session State
    // =========================================================================

    @Then("le SoC est {int}%")
    public void thenSocIs(int expectedSoc, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);

        int actualSoc = (int) session.getSoc();

        if (actualSoc != expectedSoc) {
            throw new AssertionError(
                    String.format("Expected SoC %d%% but got %d%%", expectedSoc, actualSoc));
        }

        log.info("[VALIDATION] SoC = {}%", actualSoc);
    }

    @Then("le SoC est entre {int}% et {int}%")
    public void thenSocBetween(int minSoc, int maxSoc, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);

        int actualSoc = (int) session.getSoc();

        if (actualSoc < minSoc || actualSoc > maxSoc) {
            throw new AssertionError(
                    String.format("Expected SoC between %d%% and %d%% but got %d%%", minSoc, maxSoc, actualSoc));
        }

        log.info("[VALIDATION] SoC = {}% (between {}% and {}%)", actualSoc, minSoc, maxSoc);
    }

    @Then("la puissance est {float} kW")
    public void thenPowerIs(double expectedPower, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);

        double actualPower = session.getCurrentPowerKw();

        if (Math.abs(actualPower - expectedPower) > 0.1) {
            throw new AssertionError(
                    String.format("Expected power %.1f kW but got %.1f kW", expectedPower, actualPower));
        }

        log.info("[VALIDATION] Power = {:.1f} kW", actualPower);
    }

    // =========================================================================
    // THEN Steps - Regex
    // =========================================================================

    @Then("la réponse correspond à {string}")
    public void thenResponseMatchesRegex(String regex, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        if (response == null) {
            throw new AssertionError("No response in context");
        }

        String responseStr = response.toString();
        Pattern pattern = Pattern.compile(regex);

        if (!pattern.matcher(responseStr).find()) {
            throw new AssertionError(
                    String.format("Response does not match pattern '%s'", regex));
        }

        log.info("[VALIDATION] Response matches pattern: {}", regex);
    }

    // =========================================================================
    // THEN Steps - Variable Storage
    // =========================================================================

    @Then("je stocke {string} dans {string}")
    public void thenStoreValue(String path, String variableName, TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        Object value = getValueByPath(response, path);
        context.set(variableName, value);

        log.info("[VALIDATION] Stored {} = {}", variableName, value);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private String extractStatus(Map<String, Object> response) {
        // Direct status
        Object status = response.get("status");
        if (status != null) return status.toString();

        // idTagInfo.status (Authorize)
        @SuppressWarnings("unchecked")
        Map<String, Object> idTagInfo = (Map<String, Object>) response.get("idTagInfo");
        if (idTagInfo != null) {
            status = idTagInfo.get("status");
            if (status != null) return status.toString();
        }

        return null;
    }

    private boolean containsKey(Map<String, Object> map, String key) {
        if (map.containsKey(key)) return true;

        // Chercher dans les sous-maps
        for (Object value : map.values()) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) value;
                if (containsKey(subMap, key)) return true;
            }
        }

        return false;
    }

    private Object getValueByPath(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current == null) return null;

            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> currentMap = (Map<String, Object>) current;
                current = currentMap.get(part);
            } else if (current instanceof JsonNode) {
                current = ((JsonNode) current).path(part);
                if (((JsonNode) current).isMissingNode()) return null;
            } else {
                return null;
            }
        }

        // Convertir JsonNode en valeur
        if (current instanceof JsonNode) {
            JsonNode node = (JsonNode) current;
            if (node.isTextual()) return node.asText();
            if (node.isNumber()) return node.numberValue();
            if (node.isBoolean()) return node.asBoolean();
        }

        return current;
    }
}
