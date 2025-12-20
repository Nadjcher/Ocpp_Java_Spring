package com.evse.simulator.tnr.steps;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.service.SessionService;
import com.evse.simulator.tnr.engine.TnrContext;
import com.evse.simulator.tnr.model.OcppMessageCapture;
import com.evse.simulator.tnr.steps.annotations.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Steps Gherkin pour les messages OCPP 1.6.
 * <p>
 * Supporte les principales actions OCPP:
 * BootNotification, Authorize, StartTransaction, StopTransaction,
 * Heartbeat, MeterValues, StatusNotification
 * </p>
 *
 * @example
 * <pre>
 * Feature: SC001 - BootNotification
 *   Scenario: Boot nominal
 *     Given une session connectée
 *     When j'envoie BootNotification
 *     Then le status est "Accepted"
 *     And le temps de réponse est < 500ms
 * </pre>
 */
@Slf4j
@Component
@StepDefinitions(category = "ocpp", description = "Steps pour les messages OCPP 1.6")
@RequiredArgsConstructor
public class OcppSteps {

    private final OCPPService ocppService;
    private final SessionService sessionService;

    // Clés de contexte
    private static final String CTX_LAST_RESPONSE = "lastResponse";
    private static final String CTX_LAST_RESPONSE_TIME = "lastResponseTimeMs";
    private static final String CTX_LAST_ACTION = "lastAction";
    private static final String CTX_LAST_ERROR = "lastError";

    // =========================================================================
    // GIVEN Steps
    // =========================================================================

    @Given("une session connectée")
    public void givenSessionConnected(TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("No session in context. Use 'Given une session créée' first.");
        }

        if (!ocppService.isConnected(sessionId)) {
            log.info("[OCPP] Connecting session {}", sessionId);
            boolean connected = ocppService.connect(sessionId)
                    .get(30, TimeUnit.SECONDS);
            if (!connected) {
                throw new AssertionError("Failed to connect session " + sessionId);
            }
        }

        context.set("connected", true);
        log.info("[OCPP] Session {} is connected", sessionId);
    }

    @Given("une session connectée et bootée")
    public void givenSessionConnectedAndBooted(TnrContext context) throws Exception {
        givenSessionConnected(context);

        String sessionId = context.getCurrentSessionId();
        log.info("[OCPP] Sending BootNotification for session {}", sessionId);

        Map<String, Object> response = ocppService.sendBootNotification(sessionId)
                .get(30, TimeUnit.SECONDS);

        context.set(CTX_LAST_RESPONSE, response);
        context.set("booted", true);

        String status = getStatus(response);
        if (!"Accepted".equalsIgnoreCase(status)) {
            throw new AssertionError("BootNotification not accepted: " + status);
        }

        log.info("[OCPP] Session {} booted successfully", sessionId);
    }

    @Given("une transaction active")
    public void givenTransactionActive(TnrContext context) throws Exception {
        givenSessionConnectedAndBooted(context);

        String sessionId = context.getCurrentSessionId();

        // Authorize
        Map<String, Object> authResponse = ocppService.sendAuthorize(sessionId)
                .get(30, TimeUnit.SECONDS);
        context.set("authorizeResponse", authResponse);

        // StartTransaction
        Map<String, Object> startResponse = ocppService.sendStartTransaction(sessionId)
                .get(30, TimeUnit.SECONDS);

        Integer transactionId = (Integer) startResponse.get("transactionId");
        if (transactionId == null || transactionId <= 0) {
            throw new AssertionError("Invalid transactionId: " + transactionId);
        }

        context.set("transactionId", transactionId);
        context.set("transactionActive", true);
        log.info("[OCPP] Transaction {} active for session {}", transactionId, sessionId);
    }

    @Given("le connecteur est en état {string}")
    public void givenConnectorStatus(String status, TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        ConnectorStatus connectorStatus = ConnectorStatus.valueOf(status.toUpperCase());

        Map<String, Object> response = ocppService.sendStatusNotification(sessionId, connectorStatus)
                .get(30, TimeUnit.SECONDS);

        context.set("connectorStatus", status);
        context.set(CTX_LAST_RESPONSE, response);
        log.info("[OCPP] Connector status set to {} for session {}", status, sessionId);
    }

    // =========================================================================
    // WHEN Steps - Generic
    // =========================================================================

    @When("j'envoie {word}")
    public void whenSendMessage(String action, TnrContext context) throws Exception {
        sendOcppMessage(action, new HashMap<>(), context);
    }

    @When("j'envoie {word} avec:")
    public void whenSendMessageWithPayload(String action, TnrContext context, List<Map<String, String>> dataTable) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        for (Map<String, String> row : dataTable) {
            String key = row.get("clé");
            String value = row.get("valeur");
            if (key != null && value != null) {
                payload.put(key, parseValue(value, context));
            }
        }
        sendOcppMessage(action, payload, context);
    }

    // =========================================================================
    // WHEN Steps - Specific OCPP Actions
    // =========================================================================

    @When("j'envoie BootNotification")
    public void whenSendBootNotification(TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        long startTime = System.currentTimeMillis();

        Map<String, Object> response = ocppService.sendBootNotification(sessionId)
                .get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        storeResponse("BootNotification", response, duration, context);
    }

    @When("j'envoie Authorize")
    public void whenSendAuthorize(TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        long startTime = System.currentTimeMillis();

        Map<String, Object> response = ocppService.sendAuthorize(sessionId)
                .get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        storeResponse("Authorize", response, duration, context);
    }

    @When("j'envoie Authorize avec idTag {string}")
    public void whenSendAuthorizeWithIdTag(String idTag, TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        context.set("idTag", idTag);

        // Mettre à jour l'idTag de la session
        var session = sessionService.getSession(sessionId);
        session.setIdTag(idTag);
        sessionService.updateSession(sessionId, session);

        long startTime = System.currentTimeMillis();
        Map<String, Object> response = ocppService.sendAuthorize(sessionId)
                .get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        storeResponse("Authorize", response, duration, context);
    }

    @When("j'envoie StartTransaction")
    public void whenSendStartTransaction(TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        long startTime = System.currentTimeMillis();

        Map<String, Object> response = ocppService.sendStartTransaction(sessionId)
                .get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        storeResponse("StartTransaction", response, duration, context);

        // Stocker le transactionId
        Integer transactionId = (Integer) response.get("transactionId");
        if (transactionId != null) {
            context.set("transactionId", transactionId);
        }
    }

    @When("j'envoie StopTransaction")
    public void whenSendStopTransaction(TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        long startTime = System.currentTimeMillis();

        Map<String, Object> response = ocppService.sendStopTransaction(sessionId)
                .get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        storeResponse("StopTransaction", response, duration, context);
    }

    @When("j'envoie Heartbeat")
    public void whenSendHeartbeat(TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        long startTime = System.currentTimeMillis();

        Map<String, Object> response = ocppService.sendHeartbeat(sessionId)
                .get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        storeResponse("Heartbeat", response, duration, context);
    }

    @When("j'envoie MeterValues")
    public void whenSendMeterValues(TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        long startTime = System.currentTimeMillis();

        Map<String, Object> response = ocppService.sendMeterValues(sessionId)
                .get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        storeResponse("MeterValues", response, duration, context);
    }

    @When("j'envoie StatusNotification avec status {string}")
    public void whenSendStatusNotification(String status, TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        ConnectorStatus connectorStatus = ConnectorStatus.valueOf(status);

        long startTime = System.currentTimeMillis();
        Map<String, Object> response = ocppService.sendStatusNotification(sessionId, connectorStatus)
                .get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        storeResponse("StatusNotification", response, duration, context);
    }

    @When("après {int} secondes j'envoie {word}")
    public void whenAfterDelayISend(int seconds, String action, TnrContext context) throws Exception {
        log.info("[OCPP] Waiting {} seconds before sending {}", seconds, action);
        Thread.sleep(seconds * 1000L);
        sendOcppMessage(action, new HashMap<>(), context);
    }

    @When("j'envoie {int} Heartbeat à {int}ms d'intervalle")
    public void whenSendMultipleHeartbeats(int count, int intervalMs, TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();

        for (int i = 0; i < count; i++) {
            long startTime = System.currentTimeMillis();
            Map<String, Object> response = ocppService.sendHeartbeat(sessionId)
                    .get(30, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;

            context.addToList("heartbeatResponses", response);
            context.addToList("heartbeatTimes", duration);

            if (i < count - 1) {
                Thread.sleep(intervalMs);
            }
        }

        context.set("heartbeatCount", count);
        log.info("[OCPP] Sent {} heartbeats", count);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void sendOcppMessage(String action, Map<String, Object> payload, TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        OCPPAction ocppAction = OCPPAction.fromValue(action);

        if (ocppAction == null) {
            throw new IllegalArgumentException("Unknown OCPP action: " + action);
        }

        log.info("[OCPP] Sending {} to session {}", action, sessionId);

        long startTime = System.currentTimeMillis();
        Map<String, Object> response = ocppService.sendCall(sessionId, ocppAction, payload)
                .get(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        storeResponse(action, response, duration, context);
    }

    private void storeResponse(String action, Map<String, Object> response, long durationMs, TnrContext context) {
        context.set(CTX_LAST_ACTION, action);
        context.set(CTX_LAST_RESPONSE, response);
        context.set(CTX_LAST_RESPONSE_TIME, durationMs);

        // Capturer le message pour l'historique
        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action(action)
                .direction(OcppMessageCapture.Direction.INBOUND)
                .messageType(OcppMessageCapture.MessageType.CALLRESULT)
                .timestamp(Instant.now())
                .latencyMs(durationMs)
                .sessionId(context.getCurrentSessionId())
                .build();
        context.addOcppMessage(capture);

        log.info("[OCPP] {} response received in {}ms: {}", action, durationMs, response);
    }

    private String getStatus(Map<String, Object> response) {
        if (response == null) return null;

        // Chercher dans différents formats de réponse
        Object status = response.get("status");
        if (status != null) return status.toString();

        // idTagInfo.status pour Authorize
        @SuppressWarnings("unchecked")
        Map<String, Object> idTagInfo = (Map<String, Object>) response.get("idTagInfo");
        if (idTagInfo != null) {
            status = idTagInfo.get("status");
            if (status != null) return status.toString();
        }

        return null;
    }

    private Object parseValue(String value, TnrContext context) {
        // Variable
        if (value.startsWith("${") && value.endsWith("}")) {
            return context.resolveVariables(value);
        }
        // Integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {}
        // Double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}
        // Boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }
}
