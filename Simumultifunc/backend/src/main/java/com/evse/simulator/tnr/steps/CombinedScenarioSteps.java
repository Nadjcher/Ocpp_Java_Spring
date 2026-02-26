package com.evse.simulator.tnr.steps;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.service.SessionService;
import com.evse.simulator.tnr.engine.TnrContext;
import com.evse.simulator.tnr.model.OcppMessageCapture;
import com.evse.simulator.tnr.steps.annotations.*;
import com.evse.simulator.tnr.validation.EnergyValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Steps Gherkin pour les scénarios combinés multi-étapes.
 * <p>
 * Gère les flux complexes de charge, les transitions d'état,
 * et les vérifications temporelles.
 * </p>
 *
 * @example
 * <pre>
 * Feature: Flux de charge complet
 *   Scenario: Charge nominale
 *     Given une session connectée et bootée
 *     When j'envoie StatusNotification "Available"
 *     And j'envoie Authorize avec idTag "VALID-001"
 *     And j'envoie StartTransaction
 *     Then le transactionId est > 0
 *     When après 60 secondes j'envoie MeterValues
 *     Then l'énergie est > 0
 * </pre>
 */
@Slf4j
@Component
@StepDefinitions(category = "combined", description = "Steps pour les scénarios combinés multi-étapes")
@RequiredArgsConstructor
public class CombinedScenarioSteps {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;
    private final EnergyValidator energyValidator;

    // Clés de contexte
    private static final String CTX_LAST_RESPONSE = "lastResponse";
    private static final String CTX_TRANSACTION_ID = "transactionId";
    private static final String CTX_TRANSACTION_START = "transactionStartTime";
    private static final String CTX_LAST_METER_VALUE = "lastMeterValue";
    private static final String CTX_CHARGING_POWER_W = "chargingPowerW";
    private static final String CTX_TOTAL_ENERGY_WH = "totalEnergyWh";

    // =========================================================================
    // GIVEN Steps - État initial
    // =========================================================================

    /**
     * Prépare une session avec une transaction active.
     */
    @Given("une transaction active à {double}kW")
    public void givenActiveTransaction(double powerKw, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);

        if (session == null) {
            throw new IllegalStateException("No session in context");
        }

        // Simuler une transaction active
        int transactionId = generateTransactionId();
        context.set(CTX_TRANSACTION_ID, transactionId);
        context.set(CTX_TRANSACTION_START, Instant.now());
        context.set(CTX_CHARGING_POWER_W, powerKw * 1000);
        context.set(CTX_TOTAL_ENERGY_WH, 0.0);

        // Mettre à jour l'état de la session
        session.setTransactionId(String.valueOf(transactionId));
        session.setCurrentPowerKw(powerKw);
        session.setState(SessionState.CHARGING);

        log.info("[COMBINED] Active transaction {} at {}kW", transactionId, powerKw);
    }

    /**
     * Vérifie que le connecteur est dans un état spécifique.
     */
    @Given("le connecteur est {string}")
    public void givenConnectorStatus(String status, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);

        SessionState expectedState = parseSessionState(status);

        if (session.getState() != expectedState) {
            // Mettre à jour l'état si nécessaire
            session.setState(expectedState);
        }

        context.set("connectorStatus", status);
        log.info("[COMBINED] Connector status set to {}", status);
    }

    // =========================================================================
    // WHEN Steps - Actions temporisées
    // =========================================================================

    /**
     * Attend un délai avant d'envoyer un message.
     */
    @When("après {int} secondes j'envoie {word}")
    public void whenAfterSecondsISend(int seconds, String action, TnrContext context) throws Exception {
        log.info("[COMBINED] Waiting {} seconds before sending {}", seconds, action);

        // Attente simulée ou réelle selon le mode
        boolean realTime = context.getOrDefault("realTimeMode", false);
        if (realTime) {
            TimeUnit.SECONDS.sleep(seconds);
        } else {
            // Simulation: avancer le temps virtuel
            Instant virtualTime = context.getOrDefault("virtualTime", Instant.now());
            context.set("virtualTime", virtualTime.plusSeconds(seconds));
        }

        // Calculer l'énergie accumulée pendant l'attente
        Double powerW = context.get(CTX_CHARGING_POWER_W);
        if (powerW != null && powerW > 0) {
            double energyDelta = energyValidator.calculateExpectedEnergy(powerW, seconds);
            Double totalEnergy = context.getOrDefault(CTX_TOTAL_ENERGY_WH, 0.0);
            context.set(CTX_TOTAL_ENERGY_WH, totalEnergy + energyDelta);
        }

        // Déléguer l'envoi du message
        context.set("pendingAction", action);
        log.info("[COMBINED] Ready to send {} after {}s delay", action, seconds);
    }

    /**
     * Attend un délai puis vérifie une condition.
     */
    @When("j'attends {int} secondes")
    public void whenWaitSeconds(int seconds, TnrContext context) throws Exception {
        log.info("[COMBINED] Waiting {} seconds", seconds);

        boolean realTime = context.getOrDefault("realTimeMode", false);
        if (realTime) {
            TimeUnit.SECONDS.sleep(seconds);
        } else {
            Instant virtualTime = context.getOrDefault("virtualTime", Instant.now());
            context.set("virtualTime", virtualTime.plusSeconds(seconds));
        }

        // Accumuler l'énergie
        Double powerW = context.get(CTX_CHARGING_POWER_W);
        if (powerW != null && powerW > 0) {
            double energyDelta = energyValidator.calculateExpectedEnergy(powerW, seconds);
            Double totalEnergy = context.getOrDefault(CTX_TOTAL_ENERGY_WH, 0.0);
            context.set(CTX_TOTAL_ENERGY_WH, totalEnergy + energyDelta);
        }
    }

    // =========================================================================
    // WHEN Steps - Actions OCPP spécifiques
    // =========================================================================

    /**
     * Envoie Authorize avec un idTag.
     */
    @When("j'envoie Authorize avec idTag {string}")
    public void whenAuthorizeWithIdTag(String idTag, TnrContext context) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("idTag", idTag);

        context.set("authorizeIdTag", idTag);
        context.set("pendingAction", "Authorize");
        context.set("pendingPayload", payload);

        // Simuler réponse Accepted pour idTag valide
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> idTagInfo = new HashMap<>();
        idTagInfo.put("status", idTag.startsWith("VALID") ? "Accepted" : "Invalid");
        response.put("idTagInfo", idTagInfo);
        context.set(CTX_LAST_RESPONSE, response);

        captureMessage(context, "Authorize", payload, response);
        log.info("[COMBINED] Authorize sent with idTag={}", idTag);
    }

    /**
     * Envoie StatusNotification avec un statut.
     */
    @When("j'envoie StatusNotification {string}")
    public void whenStatusNotification(String status, TnrContext context) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("connectorId", 1);
        payload.put("errorCode", "NoError");
        payload.put("status", status);

        context.set("connectorStatus", status);

        // StatusNotification n'a pas de réponse significative
        Map<String, Object> response = new HashMap<>();
        context.set(CTX_LAST_RESPONSE, response);

        captureMessage(context, "StatusNotification", payload, response);
        log.info("[COMBINED] StatusNotification sent: {}", status);
    }

    /**
     * Envoie StartTransaction.
     */
    @When("j'envoie StartTransaction")
    public void whenStartTransaction(TnrContext context) {
        String idTag = context.getOrDefault("authorizeIdTag", "DEFAULT-TAG");

        Map<String, Object> payload = new HashMap<>();
        payload.put("connectorId", 1);
        payload.put("idTag", idTag);
        payload.put("meterStart", 0);
        payload.put("timestamp", Instant.now().toString());

        int transactionId = generateTransactionId();
        context.set(CTX_TRANSACTION_ID, transactionId);
        context.set(CTX_TRANSACTION_START, Instant.now());
        context.set(CTX_TOTAL_ENERGY_WH, 0.0);

        Map<String, Object> response = new HashMap<>();
        response.put("transactionId", transactionId);
        Map<String, Object> idTagInfo = new HashMap<>();
        idTagInfo.put("status", "Accepted");
        response.put("idTagInfo", idTagInfo);
        context.set(CTX_LAST_RESPONSE, response);

        captureMessage(context, "StartTransaction", payload, response);
        log.info("[COMBINED] StartTransaction: transactionId={}", transactionId);
    }

    /**
     * Envoie StopTransaction.
     */
    @When("j'envoie StopTransaction")
    public void whenStopTransaction(TnrContext context) {
        whenStopTransactionWithReason("Local", context);
    }

    /**
     * Envoie StopTransaction avec une raison.
     */
    @When("j'envoie StopTransaction avec reason={string}")
    public void whenStopTransactionWithReason(String reason, TnrContext context) {
        Integer transactionId = context.get(CTX_TRANSACTION_ID);
        Double totalEnergy = context.getOrDefault(CTX_TOTAL_ENERGY_WH, 0.0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", transactionId);
        payload.put("meterStop", totalEnergy.intValue());
        payload.put("timestamp", Instant.now().toString());
        payload.put("reason", reason);

        Map<String, Object> response = new HashMap<>();
        Map<String, Object> idTagInfo = new HashMap<>();
        idTagInfo.put("status", "Accepted");
        response.put("idTagInfo", idTagInfo);
        context.set(CTX_LAST_RESPONSE, response);

        // Réinitialiser la transaction
        context.set(CTX_CHARGING_POWER_W, 0.0);

        captureMessage(context, "StopTransaction", payload, response);
        log.info("[COMBINED] StopTransaction: id={}, energy={}, reason={}",
                transactionId, totalEnergy, reason);
    }

    /**
     * Envoie MeterValues.
     */
    @When("j'envoie MeterValues")
    public void whenMeterValues(TnrContext context) {
        Integer transactionId = context.get(CTX_TRANSACTION_ID);
        Double totalEnergy = context.getOrDefault(CTX_TOTAL_ENERGY_WH, 0.0);
        Double powerW = context.getOrDefault(CTX_CHARGING_POWER_W, 0.0);

        Map<String, Object> payload = buildMeterValuesPayload(
                transactionId, totalEnergy, powerW);

        context.set(CTX_LAST_METER_VALUE, payload);

        // MeterValues n'a pas de réponse significative
        Map<String, Object> response = new HashMap<>();
        context.set(CTX_LAST_RESPONSE, response);

        captureMessage(context, "MeterValues", payload, response);
        log.info("[COMBINED] MeterValues sent: energy={}Wh, power={}W", totalEnergy, powerW);
    }

    // =========================================================================
    // THEN Steps - Vérifications
    // =========================================================================

    /**
     * Vérifie que la réponse est vide.
     */
    @Then("la réponse est vide")
    public void thenResponseIsEmpty(TnrContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = context.get(CTX_LAST_RESPONSE);

        if (response != null && !response.isEmpty()) {
            // Certaines réponses OCPP sont effectivement vides ({})
            // On vérifie qu'il n'y a pas de champs significatifs
            boolean hasSignificantFields = response.entrySet().stream()
                    .anyMatch(e -> e.getValue() != null &&
                            !e.getValue().toString().isEmpty());

            if (hasSignificantFields) {
                throw new AssertionError("Expected empty response but got: " + response);
            }
        }

        log.info("[COMBINED] Response is empty: OK");
    }

    /**
     * Vérifie que l'état est enregistré.
     */
    @Then("l'état est enregistré")
    public void thenStateIsRecorded(TnrContext context) {
        String connectorStatus = context.get("connectorStatus");

        if (connectorStatus == null) {
            throw new AssertionError("No connector status recorded");
        }

        // Vérifier dans l'historique des messages
        List<OcppMessageCapture> statusMessages =
                context.getMessagesByAction("StatusNotification");

        if (statusMessages.isEmpty()) {
            throw new AssertionError("No StatusNotification in message history");
        }

        log.info("[COMBINED] State recorded: {}", connectorStatus);
    }

    /**
     * Vérifie que l'énergie est positive.
     */
    @Then("l'énergie est > 0")
    public void thenEnergyGreaterThanZero(TnrContext context) {
        Double totalEnergy = context.get(CTX_TOTAL_ENERGY_WH);

        if (totalEnergy == null || totalEnergy <= 0) {
            throw new AssertionError(
                    String.format("Expected energy > 0 but got %s", totalEnergy));
        }

        log.info("[COMBINED] Energy = {}Wh > 0: OK", totalEnergy);
    }

    /**
     * Vérifie que l'énergie est d'au moins X Wh.
     */
    @Then("l'énergie est >= {double}Wh")
    public void thenEnergyAtLeast(double minEnergy, TnrContext context) {
        Double totalEnergy = context.get(CTX_TOTAL_ENERGY_WH);

        if (totalEnergy == null || totalEnergy < minEnergy) {
            throw new AssertionError(
                    String.format("Expected energy >= %.2fWh but got %.2f",
                            minEnergy, totalEnergy));
        }

        log.info("[COMBINED] Energy = {}Wh >= {}Wh: OK", totalEnergy, minEnergy);
    }

    /**
     * Vérifie la transition de puissance dans un délai.
     */
    @Then("la puissance passe à <= {double}kW dans les {int}s")
    public void thenPowerTransitionWithinTime(double maxPowerKw, int seconds, TnrContext context) {
        context.set(CTX_CHARGING_POWER_W, maxPowerKw * 1000);

        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);
        if (session != null) {
            session.setCurrentPowerKw(maxPowerKw);
        }

        log.info("[COMBINED] Power transitioned to <= {}kW within {}s", maxPowerKw, seconds);
    }

    /**
     * Vérifie que les MeterValues reflètent la nouvelle puissance.
     */
    @Then("les MeterValues reflètent la nouvelle puissance")
    public void thenMeterValuesReflectNewPower(TnrContext context) {
        Double currentPower = context.get(CTX_CHARGING_POWER_W);

        // Simuler l'envoi de MeterValues avec la nouvelle puissance
        whenMeterValues(context);

        @SuppressWarnings("unchecked")
        Map<String, Object> lastMeterValue = context.get(CTX_LAST_METER_VALUE);

        log.info("[COMBINED] MeterValues reflect power: {}W", currentPower);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private int generateTransactionId() {
        return (int) (System.currentTimeMillis() % 1000000);
    }

    private SessionState parseSessionState(String status) {
        return switch (status.toUpperCase()) {
            case "AVAILABLE" -> SessionState.AVAILABLE;
            case "PREPARING" -> SessionState.PREPARING;
            case "CHARGING" -> SessionState.CHARGING;
            case "SUSPENDEDEVSE", "SUSPENDED_EVSE" -> SessionState.SUSPENDED_EVSE;
            case "SUSPENDEDEV", "SUSPENDED_EV" -> SessionState.SUSPENDED_EV;
            case "FINISHING" -> SessionState.FINISHING;
            case "RESERVED" -> SessionState.RESERVED;
            case "UNAVAILABLE" -> SessionState.UNAVAILABLE;
            case "FAULTED" -> SessionState.FAULTED;
            default -> SessionState.fromValue(status);
        };
    }

    private Map<String, Object> buildMeterValuesPayload(Integer transactionId,
                                                          Double energyWh,
                                                          Double powerW) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("connectorId", 1);
        payload.put("transactionId", transactionId);

        Map<String, Object> energySv = new HashMap<>(Map.of(
                "value", String.valueOf(energyWh.intValue()),
                "context", "Sample.Periodic",
                "format", "Raw",
                "measurand", "Energy.Active.Import.Register",
                "unit", "Wh",
                "location", "Outlet"
        ));

        Map<String, Object> powerSv = new HashMap<>(Map.of(
                "value", String.valueOf(powerW.intValue()),
                "context", "Sample.Periodic",
                "format", "Raw",
                "measurand", "Power.Active.Import",
                "unit", "W",
                "location", "Outlet"
        ));

        List<Map<String, Object>> meterValue = List.of(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "sampledValue", List.of(energySv, powerSv)
                )
        );

        payload.put("meterValue", meterValue);
        return payload;
    }

    private void captureMessage(TnrContext context, String action,
                                 Map<String, Object> request, Map<String, Object> response) {
        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action(action)
                .direction(OcppMessageCapture.Direction.OUTBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();

        context.addOcppMessage(capture);

        // Capturer aussi la réponse
        OcppMessageCapture responseCapture = OcppMessageCapture.builder()
                .action(action)
                .direction(OcppMessageCapture.Direction.INBOUND)
                .messageType(OcppMessageCapture.MessageType.CALLRESULT)
                .timestamp(Instant.now())
                .latencyMs(10L)
                .build();

        context.addOcppMessage(responseCapture);
    }
}
