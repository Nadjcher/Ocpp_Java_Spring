package com.evse.simulator.tnr.steps;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.service.SessionService;
import com.evse.simulator.tnr.engine.TnrContext;
import com.evse.simulator.tnr.model.OcppMessageCapture;
import com.evse.simulator.tnr.steps.annotations.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Steps Gherkin pour les commandes distantes OCPP.
 * <p>
 * Gère les scénarios Remote Start/Stop, Reset, UnlockConnector, etc.
 * Ces commandes sont initiées par le Central System (CSMS) vers le ChargePoint.
 * </p>
 *
 * @example
 * <pre>
 * Feature: Remote Start/Stop
 *   Scenario: Démarrage distant réussi
 *     Given le connecteur est "Available"
 *     When je reçois RemoteStartTransaction avec idTag "REMOTE-001"
 *     Then je réponds "Accepted"
 *     And j'envoie StatusNotification "Preparing"
 *     And j'envoie StartTransaction
 * </pre>
 */
@Slf4j
@Component
@StepDefinitions(category = "remote-commands", description = "Steps pour les commandes distantes OCPP")
@RequiredArgsConstructor
public class RemoteCommandSteps {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    // Clés de contexte
    private static final String CTX_LAST_RESPONSE = "lastResponse";
    private static final String CTX_LAST_REMOTE_COMMAND = "lastRemoteCommand";
    private static final String CTX_REMOTE_ID_TAG = "remoteIdTag";
    private static final String CTX_TRANSACTION_ID = "transactionId";

    // =========================================================================
    // WHEN Steps - Réception de commandes distantes
    // =========================================================================

    /**
     * Simule la réception d'une commande RemoteStartTransaction.
     */
    @When("je reçois RemoteStartTransaction")
    public void whenReceiveRemoteStart(TnrContext context) {
        whenReceiveRemoteStartWithIdTag("REMOTE-DEFAULT", context);
    }

    /**
     * Simule la réception d'une commande RemoteStartTransaction avec idTag.
     */
    @When("je reçois RemoteStartTransaction avec idTag {string}")
    public void whenReceiveRemoteStartWithIdTag(String idTag, TnrContext context) {
        Map<String, Object> request = new HashMap<>();
        request.put("connectorId", 1);
        request.put("idTag", idTag);

        context.set(CTX_LAST_REMOTE_COMMAND, "RemoteStartTransaction");
        context.set(CTX_REMOTE_ID_TAG, idTag);

        // Capturer le message entrant
        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("RemoteStartTransaction")
                .direction(OcppMessageCapture.Direction.INBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Received RemoteStartTransaction with idTag={}", idTag);
    }

    /**
     * Simule la réception d'une commande RemoteStartTransaction avec profil de charge.
     */
    @When("je reçois RemoteStartTransaction avec profil:")
    public void whenReceiveRemoteStartWithProfile(Map<String, String> profile, TnrContext context) {
        Map<String, Object> request = new HashMap<>();
        request.put("connectorId", 1);
        request.put("idTag", profile.getOrDefault("idTag", "REMOTE-DEFAULT"));

        // Construire le profil de charge si présent
        if (profile.containsKey("chargingProfileId")) {
            Map<String, Object> chargingProfile = new HashMap<>();
            chargingProfile.put("chargingProfileId", Integer.parseInt(profile.get("chargingProfileId")));
            chargingProfile.put("stackLevel", Integer.parseInt(profile.getOrDefault("stackLevel", "0")));
            chargingProfile.put("chargingProfilePurpose", profile.getOrDefault("purpose", "TxProfile"));
            chargingProfile.put("chargingProfileKind", profile.getOrDefault("kind", "Relative"));
            request.put("chargingProfile", chargingProfile);
        }

        context.set(CTX_LAST_REMOTE_COMMAND, "RemoteStartTransaction");
        context.set(CTX_REMOTE_ID_TAG, profile.getOrDefault("idTag", "REMOTE-DEFAULT"));

        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("RemoteStartTransaction")
                .direction(OcppMessageCapture.Direction.INBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Received RemoteStartTransaction with charging profile");
    }

    /**
     * Simule la réception d'une commande RemoteStopTransaction.
     */
    @When("je reçois RemoteStopTransaction")
    public void whenReceiveRemoteStop(TnrContext context) {
        Integer transactionId = context.get(CTX_TRANSACTION_ID);

        Map<String, Object> request = new HashMap<>();
        request.put("transactionId", transactionId);

        context.set(CTX_LAST_REMOTE_COMMAND, "RemoteStopTransaction");

        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("RemoteStopTransaction")
                .direction(OcppMessageCapture.Direction.INBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Received RemoteStopTransaction for txId={}", transactionId);
    }

    /**
     * Simule la réception d'une commande Reset.
     */
    @When("je reçois Reset {string}")
    public void whenReceiveReset(String type, TnrContext context) {
        Map<String, Object> request = new HashMap<>();
        request.put("type", type);

        context.set(CTX_LAST_REMOTE_COMMAND, "Reset");
        context.set("resetType", type);

        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("Reset")
                .direction(OcppMessageCapture.Direction.INBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Received Reset type={}", type);
    }

    /**
     * Simule la réception d'une commande UnlockConnector.
     */
    @When("je reçois UnlockConnector")
    public void whenReceiveUnlockConnector(TnrContext context) {
        Map<String, Object> request = new HashMap<>();
        request.put("connectorId", 1);

        context.set(CTX_LAST_REMOTE_COMMAND, "UnlockConnector");

        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("UnlockConnector")
                .direction(OcppMessageCapture.Direction.INBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Received UnlockConnector");
    }

    /**
     * Simule la réception d'une commande ChangeAvailability.
     */
    @When("je reçois ChangeAvailability {string}")
    public void whenReceiveChangeAvailability(String type, TnrContext context) {
        Map<String, Object> request = new HashMap<>();
        request.put("connectorId", 0); // 0 = toute la borne
        request.put("type", type);

        context.set(CTX_LAST_REMOTE_COMMAND, "ChangeAvailability");
        context.set("availabilityType", type);

        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("ChangeAvailability")
                .direction(OcppMessageCapture.Direction.INBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Received ChangeAvailability type={}", type);
    }

    /**
     * Simule la réception de TriggerMessage.
     */
    @When("je reçois TriggerMessage {string}")
    public void whenReceiveTriggerMessage(String requestedMessage, TnrContext context) {
        Map<String, Object> request = new HashMap<>();
        request.put("requestedMessage", requestedMessage);

        context.set(CTX_LAST_REMOTE_COMMAND, "TriggerMessage");
        context.set("triggeredMessage", requestedMessage);

        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("TriggerMessage")
                .direction(OcppMessageCapture.Direction.INBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Received TriggerMessage for {}", requestedMessage);
    }

    // =========================================================================
    // THEN Steps - Envoi de réponses
    // =========================================================================

    /**
     * Répond à la dernière commande distante.
     */
    @Then("je réponds {string}")
    public void thenIRespond(String status, TnrContext context) {
        String command = context.get(CTX_LAST_REMOTE_COMMAND);

        if (command == null) {
            throw new IllegalStateException("No remote command to respond to");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        context.set(CTX_LAST_RESPONSE, response);

        // Capturer la réponse sortante
        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action(command)
                .direction(OcppMessageCapture.Direction.OUTBOUND)
                .messageType(OcppMessageCapture.MessageType.CALLRESULT)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Responded to {} with status={}", command, status);
    }

    /**
     * Envoie StatusNotification en réponse à une commande.
     */
    @Then("j'envoie StatusNotification {string}")
    public void thenSendStatusNotification(String status, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);

        if (session != null) {
            session.setState(parseSessionState(status));
        }

        context.set("connectorStatus", status);

        Map<String, Object> payload = new HashMap<>();
        payload.put("connectorId", 1);
        payload.put("errorCode", "NoError");
        payload.put("status", status);

        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("StatusNotification")
                .direction(OcppMessageCapture.Direction.OUTBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Sent StatusNotification: {}", status);
    }

    /**
     * Envoie StartTransaction suite à un RemoteStart.
     */
    @Then("j'envoie StartTransaction")
    public void thenSendStartTransaction(TnrContext context) {
        String idTag = context.getOrDefault(CTX_REMOTE_ID_TAG, "DEFAULT-TAG");

        int transactionId = generateTransactionId();
        context.set(CTX_TRANSACTION_ID, transactionId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("connectorId", 1);
        payload.put("idTag", idTag);
        payload.put("meterStart", 0);
        payload.put("timestamp", Instant.now().toString());

        // Simuler la réponse
        Map<String, Object> response = new HashMap<>();
        response.put("transactionId", transactionId);
        Map<String, Object> idTagInfo = new HashMap<>();
        idTagInfo.put("status", "Accepted");
        response.put("idTagInfo", idTagInfo);
        context.set(CTX_LAST_RESPONSE, response);

        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("StartTransaction")
                .direction(OcppMessageCapture.Direction.OUTBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Sent StartTransaction, got transactionId={}", transactionId);
    }

    /**
     * Envoie StopTransaction suite à un RemoteStop.
     */
    @Then("j'envoie StopTransaction avec reason={string}")
    public void thenSendStopTransactionWithReason(String reason, TnrContext context) {
        Integer transactionId = context.get(CTX_TRANSACTION_ID);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", transactionId);
        payload.put("meterStop", 0);
        payload.put("timestamp", Instant.now().toString());
        payload.put("reason", reason);

        // Simuler la réponse
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> idTagInfo = new HashMap<>();
        idTagInfo.put("status", "Accepted");
        response.put("idTagInfo", idTagInfo);
        context.set(CTX_LAST_RESPONSE, response);

        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("StopTransaction")
                .direction(OcppMessageCapture.Direction.OUTBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Sent StopTransaction with reason={}", reason);
    }

    /**
     * Envoie le message déclenché par TriggerMessage.
     */
    @Then("j'envoie le message déclenché")
    public void thenSendTriggeredMessage(TnrContext context) {
        String triggeredMessage = context.get("triggeredMessage");

        if (triggeredMessage == null) {
            throw new IllegalStateException("No triggered message in context");
        }

        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action(triggeredMessage)
                .direction(OcppMessageCapture.Direction.OUTBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);

        log.info("[REMOTE] Sent triggered message: {}", triggeredMessage);
    }

    // =========================================================================
    // THEN Steps - Vérifications
    // =========================================================================

    /**
     * Vérifie que le connecteur est déverrouillé.
     */
    @Then("le connecteur est déverrouillé")
    public void thenConnectorUnlocked(TnrContext context) {
        // Dans un vrai système, on vérifierait l'état du connecteur
        log.info("[REMOTE] Connector unlocked verified");
    }

    /**
     * Vérifie que la borne redémarre.
     */
    @Then("la borne redémarre")
    public void thenChargePointReboots(TnrContext context) {
        String resetType = context.get("resetType");

        // Simuler le redémarrage
        context.set("rebooting", true);

        log.info("[REMOTE] Charge point rebooting (type={})", resetType);
    }

    /**
     * Vérifie que la disponibilité a changé.
     */
    @Then("la disponibilité est {string}")
    public void thenAvailabilityIs(String expectedType, TnrContext context) {
        String actualType = context.get("availabilityType");

        if (!expectedType.equalsIgnoreCase(actualType)) {
            throw new AssertionError(
                    String.format("Expected availability '%s' but got '%s'",
                            expectedType, actualType));
        }

        log.info("[REMOTE] Availability verified: {}", expectedType);
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
}
