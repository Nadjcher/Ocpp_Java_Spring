package com.evse.simulator.tnr.steps;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.service.SessionService;
import com.evse.simulator.tnr.engine.TnrContext;
import com.evse.simulator.tnr.steps.annotations.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Steps Gherkin pour la gestion des sessions EVSE.
 * <p>
 * Permet de créer, configurer et gérer les sessions de charge.
 * </p>
 *
 * @example
 * <pre>
 * Feature: Gestion des sessions
 *   Scenario: Créer une session
 *     Given une nouvelle session "TEST_001"
 *     And le ChargePoint ID est "CP001"
 *     And l'URL CSMS est "ws://localhost:8080/ocpp"
 *     When je connecte la session
 *     Then la session est connectée
 * </pre>
 */
@Slf4j
@Component
@StepDefinitions(category = "session", description = "Steps pour la gestion des sessions")
@RequiredArgsConstructor
public class SessionSteps {

    private final SessionService sessionService;
    private final OCPPService ocppService;

    // =========================================================================
    // GIVEN Steps - Création de session
    // =========================================================================

    @Given("une nouvelle session")
    public void givenNewSession(TnrContext context) {
        String sessionId = "tnr-" + UUID.randomUUID().toString().substring(0, 8);
        createSession(sessionId, "TNR Session", context);
    }

    @Given("une nouvelle session {string}")
    public void givenNewSessionWithName(String name, TnrContext context) {
        String sessionId = "tnr-" + name.toLowerCase().replaceAll("[^a-z0-9]", "-");
        createSession(sessionId, name, context);
    }

    @Given("une session créée avec cpId {string}")
    public void givenSessionWithCpId(String cpId, TnrContext context) {
        String sessionId = "tnr-" + cpId.toLowerCase().replaceAll("[^a-z0-9]", "-");
        Session session = Session.builder()
                .id(sessionId)
                .title("TNR Session " + cpId)
                .cpId(cpId)
                .url(context.getOrDefault("csmsUrl", "ws://localhost:8080/ocpp"))
                .idTag(context.getOrDefault("idTag", "TNR_TAG_001"))
                .soc(context.getOrDefault("soc", 20.0))
                .targetSoc(context.getOrDefault("targetSoc", 80.0))
                .chargerType(ChargerType.AC_TRI)
                .build();

        Session created = sessionService.createSession(session);
        context.setCurrentSessionId(created.getId());
        context.set("session", created);
        context.set("cpId", cpId);

        log.info("[SESSION] Created session {} with cpId {}", sessionId, cpId);
    }

    @Given("une session existante {string}")
    public void givenExistingSession(String sessionId, TnrContext context) {
        Session session = sessionService.getSession(sessionId);
        context.setCurrentSessionId(session.getId());
        context.set("session", session);
        log.info("[SESSION] Using existing session {}", sessionId);
    }

    // =========================================================================
    // GIVEN Steps - Configuration
    // =========================================================================

    @Given("le ChargePoint ID est {string}")
    public void givenCpId(String cpId, TnrContext context) {
        updateCurrentSession(context, session -> session.setCpId(cpId));
        context.set("cpId", cpId);
    }

    @Given("l'URL CSMS est {string}")
    public void givenCsmsUrl(String url, TnrContext context) {
        updateCurrentSession(context, session -> session.setUrl(url));
        context.set("csmsUrl", url);
    }

    @Given("l'idTag est {string}")
    public void givenIdTag(String idTag, TnrContext context) {
        updateCurrentSession(context, session -> session.setIdTag(idTag));
        context.set("idTag", idTag);
    }

    @Given("le SoC initial est {int}%")
    public void givenInitialSoc(int soc, TnrContext context) {
        updateCurrentSession(context, session -> session.setSoc(soc));
        context.set("soc", soc);
    }

    @Given("le SoC cible est {int}%")
    public void givenTargetSoc(int targetSoc, TnrContext context) {
        updateCurrentSession(context, session -> session.setTargetSoc(targetSoc));
        context.set("targetSoc", targetSoc);
    }

    @Given("le type de chargeur est {string}")
    public void givenChargerType(String type, TnrContext context) {
        ChargerType chargerType = ChargerType.valueOf(type.toUpperCase().replace("-", "_"));
        updateCurrentSession(context, session -> session.setChargerType(chargerType));
        context.set("chargerType", chargerType);
    }

    @Given("la puissance max est {float} kW")
    public void givenMaxPower(double maxPowerKw, TnrContext context) {
        updateCurrentSession(context, session -> session.setMaxPowerKw(maxPowerKw));
        context.set("maxPowerKw", maxPowerKw);
    }

    @Given("le connecteur ID est {int}")
    public void givenConnectorId(int connectorId, TnrContext context) {
        updateCurrentSession(context, session -> session.setConnectorId(connectorId));
        context.set("connectorId", connectorId);
    }

    @Given("les paramètres de session:")
    public void givenSessionParameters(TnrContext context, List<Map<String, String>> dataTable) {
        for (Map<String, String> row : dataTable) {
            String key = row.get("paramètre");
            String value = row.get("valeur");

            switch (key.toLowerCase()) {
                case "cpid" -> givenCpId(value, context);
                case "url", "csmsurl" -> givenCsmsUrl(value, context);
                case "idtag" -> givenIdTag(value, context);
                case "soc" -> givenInitialSoc(Integer.parseInt(value), context);
                case "targetsoc" -> givenTargetSoc(Integer.parseInt(value), context);
                case "chargertype" -> givenChargerType(value, context);
                case "maxpower" -> givenMaxPower(Double.parseDouble(value), context);
                case "connectorid" -> givenConnectorId(Integer.parseInt(value), context);
            }
        }
    }

    // =========================================================================
    // WHEN Steps - Actions
    // =========================================================================

    @When("je connecte la session")
    public void whenConnectSession(TnrContext context) throws Exception {
        String sessionId = context.getCurrentSessionId();
        log.info("[SESSION] Connecting session {}", sessionId);

        boolean connected = ocppService.connect(sessionId)
                .get(30, TimeUnit.SECONDS);

        if (!connected) {
            throw new AssertionError("Failed to connect session " + sessionId);
        }

        context.set("connected", true);
        log.info("[SESSION] Session {} connected", sessionId);
    }

    @When("je déconnecte la session")
    public void whenDisconnectSession(TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        log.info("[SESSION] Disconnecting session {}", sessionId);

        ocppService.disconnect(sessionId);

        context.set("connected", false);
        log.info("[SESSION] Session {} disconnected", sessionId);
    }

    @When("je crée {int} sessions avec préfixe {string}")
    public void whenCreateMultipleSessions(int count, String prefix, TnrContext context) {
        for (int i = 1; i <= count; i++) {
            String sessionId = prefix + "_" + String.format("%03d", i);
            String cpId = prefix + "_CP_" + String.format("%03d", i);

            Session session = Session.builder()
                    .id(sessionId)
                    .title("TNR Batch " + sessionId)
                    .cpId(cpId)
                    .url(context.getOrDefault("csmsUrl", "ws://localhost:8080/ocpp"))
                    .idTag(context.getOrDefault("idTag", "TNR_TAG_001"))
                    .soc(20.0)
                    .targetSoc(80.0)
                    .chargerType(ChargerType.AC_TRI)
                    .build();

            Session created = sessionService.createSession(session);
            context.addToList("sessions", created.getId());
            context.addActiveSession(created.getId());
        }

        context.set("sessionCount", count);
        log.info("[SESSION] Created {} sessions with prefix {}", count, prefix);
    }

    @When("je supprime la session")
    public void whenDeleteSession(TnrContext context) {
        String sessionId = context.getCurrentSessionId();

        // S'assurer que la session est déconnectée
        if (ocppService.isConnected(sessionId)) {
            ocppService.disconnect(sessionId);
        }

        sessionService.deleteSession(sessionId);
        context.removeSession(sessionId);

        log.info("[SESSION] Deleted session {}", sessionId);
    }

    @When("j'attends {int} secondes")
    public void whenWait(int seconds, TnrContext context) throws InterruptedException {
        log.info("[SESSION] Waiting {} seconds", seconds);
        Thread.sleep(seconds * 1000L);
    }

    @When("je mets à jour le SoC à {int}%")
    public void whenUpdateSoc(int soc, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);
        sessionService.updateChargingData(sessionId, soc, session.getCurrentPowerKw(),
                session.getEnergyDeliveredKwh());
        context.set("soc", soc);
        log.info("[SESSION] Updated SoC to {}%", soc);
    }

    // =========================================================================
    // THEN Steps - Vérifications
    // =========================================================================

    @Then("la session est connectée")
    public void thenSessionConnected(TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        boolean connected = ocppService.isConnected(sessionId);

        if (!connected) {
            throw new AssertionError("Session " + sessionId + " is not connected");
        }
        log.info("[SESSION] Verified: session {} is connected", sessionId);
    }

    @Then("la session est déconnectée")
    public void thenSessionDisconnected(TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        boolean connected = ocppService.isConnected(sessionId);

        if (connected) {
            throw new AssertionError("Session " + sessionId + " is still connected");
        }
        log.info("[SESSION] Verified: session {} is disconnected", sessionId);
    }

    @Then("l'état de la session est {string}")
    public void thenSessionState(String expectedState, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);
        SessionState state = session.getState();

        if (!state.name().equalsIgnoreCase(expectedState)) {
            throw new AssertionError("Expected state " + expectedState + " but got " + state);
        }
        log.info("[SESSION] Verified: session state is {}", state);
    }

    @Then("la session est en charge")
    public void thenSessionCharging(TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);

        if (!session.isCharging()) {
            throw new AssertionError("Session " + sessionId + " is not charging");
        }
        log.info("[SESSION] Verified: session {} is charging", sessionId);
    }

    @Then("le nombre de sessions est {int}")
    public void thenSessionCount(int expectedCount, TnrContext context) {
        long count = sessionService.countSessions();

        if (count != expectedCount) {
            throw new AssertionError("Expected " + expectedCount + " sessions but got " + count);
        }
        log.info("[SESSION] Verified: session count is {}", count);
    }

    @Then("{int} sessions sont créées")
    public void thenMultipleSessionsCreated(int expectedCount, TnrContext context) {
        List<String> sessionIds = context.getList("sessions");

        if (sessionIds.size() != expectedCount) {
            throw new AssertionError("Expected " + expectedCount + " sessions but created " + sessionIds.size());
        }
        log.info("[SESSION] Verified: {} sessions created", expectedCount);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void createSession(String sessionId, String title, TnrContext context) {
        Session session = Session.builder()
                .id(sessionId)
                .title(title)
                .cpId(context.getOrDefault("cpId", "TNR_CP_001"))
                .url(context.getOrDefault("csmsUrl", "ws://localhost:8080/ocpp"))
                .idTag(context.getOrDefault("idTag", "TNR_TAG_001"))
                .soc(context.getOrDefault("soc", 20.0))
                .targetSoc(context.getOrDefault("targetSoc", 80.0))
                .chargerType(ChargerType.AC_TRI)
                .build();

        Session created = sessionService.createSession(session);
        context.setCurrentSessionId(created.getId());
        context.set("session", created);

        log.info("[SESSION] Created session {} - {}", sessionId, title);
    }

    private void updateCurrentSession(TnrContext context, java.util.function.Consumer<Session> updater) {
        String sessionId = context.getCurrentSessionId();
        if (sessionId == null) {
            throw new IllegalStateException("No current session in context");
        }

        Session session = sessionService.getSession(sessionId);
        updater.accept(session);
        sessionService.updateSession(sessionId, session);

        context.set("session", session);
    }
}
