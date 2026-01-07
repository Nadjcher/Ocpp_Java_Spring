package com.evse.simulator.tnr.steps;

import com.evse.simulator.domain.service.SmartChargingService;
import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.ChargingProfile.*;
import com.evse.simulator.model.Session;
import com.evse.simulator.service.SessionService;
import com.evse.simulator.tnr.engine.TnrContext;
import com.evse.simulator.tnr.steps.annotations.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Steps Gherkin pour le Smart Charging OCPP 1.6.
 * <p>
 * Supporte SetChargingProfile, ClearChargingProfile, GetCompositeSchedule
 * et la validation des limites de puissance.
 * </p>
 *
 * @example
 * <pre>
 * Feature: SC100 - SetChargingProfile TxProfile
 *
 *   Scenario: Profil limite la puissance
 *     Given une transaction active
 *     And la puissance initiale est de 22kW
 *     When j'envoie SetChargingProfile:
 *       | chargingProfileId | 100       |
 *       | stackLevel        | 0         |
 *       | purpose           | TxProfile |
 *       | limit             | 11000     |
 *       | unit              | W         |
 *     Then le profil est "Accepted"
 *     And la puissance effective est <= 11000W
 * </pre>
 */
@Slf4j
@Component
@StepDefinitions(category = "smartcharging", description = "Steps pour le Smart Charging OCPP 1.6")
@RequiredArgsConstructor
public class SmartChargingSteps {

    private final SmartChargingService smartChargingService;
    private final SessionService sessionService;

    // Clés de contexte
    private static final String CTX_LAST_PROFILE_STATUS = "lastProfileStatus";
    private static final String CTX_LAST_PROFILE = "lastProfile";
    private static final String CTX_COMPOSITE_SCHEDULE = "compositeSchedule";
    private static final String CTX_INITIAL_POWER_KW = "initialPowerKw";

    // Constantes électriques par défaut
    private static final double DEFAULT_VOLTAGE = 230.0;
    private static final int DEFAULT_PHASES = 3;

    // =========================================================================
    // GIVEN Steps
    // =========================================================================

    @Given("un profil {word} actif avec limite {int}W")
    public void givenActiveProfileWithLimit(String purpose, int limitW, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        ChargingProfilePurpose profilePurpose = parseProfilePurpose(purpose);

        // Créer et appliquer le profil
        ChargingProfile profile = ChargingProfile.builder()
                .chargingProfileId(context.incrementCounter("profileId"))
                .stackLevel(0)
                .chargingProfilePurpose(profilePurpose)
                .chargingProfileKind(ChargingProfileKind.ABSOLUTE)
                .chargingSchedule(ChargingSchedule.builder()
                        .duration(86400) // 24h
                        .startSchedule(LocalDateTime.now())
                        .chargingRateUnit(ChargingRateUnit.W)
                        .chargingSchedulePeriod(List.of(
                                ChargingSchedulePeriod.builder()
                                        .startPeriod(0)
                                        .limit(limitW)
                                        .numberPhases(DEFAULT_PHASES)
                                        .build()
                        ))
                        .build())
                .build();

        String status = smartChargingService.setChargingProfile(sessionId, profile);
        context.set(CTX_LAST_PROFILE, profile);
        context.set(CTX_LAST_PROFILE_STATUS, status);
        context.set("activeProfileLimit", limitW);

        log.info("[SMART] Active profile {} with limit {}W (status={})", purpose, limitW, status);
    }

    @Given("un profil {word} actif avec limite {int}A")
    public void givenActiveProfileWithLimitAmps(String purpose, int limitA, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        ChargingProfilePurpose profilePurpose = parseProfilePurpose(purpose);

        ChargingProfile profile = ChargingProfile.builder()
                .chargingProfileId(context.incrementCounter("profileId"))
                .stackLevel(0)
                .chargingProfilePurpose(profilePurpose)
                .chargingProfileKind(ChargingProfileKind.ABSOLUTE)
                .chargingSchedule(ChargingSchedule.builder()
                        .duration(86400)
                        .startSchedule(LocalDateTime.now())
                        .chargingRateUnit(ChargingRateUnit.A)
                        .chargingSchedulePeriod(List.of(
                                ChargingSchedulePeriod.builder()
                                        .startPeriod(0)
                                        .limit(limitA)
                                        .numberPhases(DEFAULT_PHASES)
                                        .build()
                        ))
                        .build())
                .build();

        String status = smartChargingService.setChargingProfile(sessionId, profile);
        context.set(CTX_LAST_PROFILE, profile);
        context.set(CTX_LAST_PROFILE_STATUS, status);
        context.set("activeProfileLimitA", limitA);

        log.info("[SMART] Active profile {} with limit {}A (status={})", purpose, limitA, status);
    }

    @Given("la puissance initiale est de {int}kW")
    public void givenInitialPower(int powerKw, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        Session session = sessionService.getSession(sessionId);

        session.setCurrentPowerKw(powerKw);
        session.setMaxPowerKw(powerKw);
        sessionService.updateSession(sessionId, session);

        context.set(CTX_INITIAL_POWER_KW, powerKw);
        log.info("[SMART] Initial power set to {}kW", powerKw);
    }

    @Given("aucun profil de charge n'est actif")
    public void givenNoActiveProfile(TnrContext context) {
        String sessionId = context.getCurrentSessionId();

        // Clear tous les profils
        smartChargingService.clearChargingProfile(sessionId, null, null, null);

        log.info("[SMART] Cleared all charging profiles for session {}", sessionId);
    }

    // =========================================================================
    // WHEN Steps - SetChargingProfile
    // =========================================================================

    @When("j'envoie SetChargingProfile:")
    public void whenSendSetChargingProfile(TnrContext context, List<Map<String, String>> dataTable) {
        String sessionId = context.getCurrentSessionId();

        // Parser les paramètres du profil
        int chargingProfileId = 1;
        int stackLevel = 0;
        ChargingProfilePurpose purpose = ChargingProfilePurpose.TX_DEFAULT_PROFILE;
        double limit = 0;
        ChargingRateUnit unit = ChargingRateUnit.W;
        int duration = 86400;
        Integer numberPhases = null;

        for (Map<String, String> row : dataTable) {
            String key = row.get("clé") != null ? row.get("clé") : row.keySet().iterator().next();
            String value = row.get("valeur") != null ? row.get("valeur") : row.values().iterator().next();

            // Support des deux formats de DataTable
            if (row.containsKey("chargingProfileId")) {
                chargingProfileId = Integer.parseInt(row.get("chargingProfileId"));
                stackLevel = Integer.parseInt(row.getOrDefault("stackLevel", "0"));
                purpose = parseProfilePurpose(row.getOrDefault("purpose", "TxDefaultProfile"));
                limit = Double.parseDouble(row.getOrDefault("limit", "0"));
                unit = ChargingRateUnit.fromValue(row.getOrDefault("unit", "W"));
                duration = Integer.parseInt(row.getOrDefault("duration", "86400"));
                if (row.containsKey("numberPhases")) {
                    numberPhases = Integer.parseInt(row.get("numberPhases"));
                }
                break;
            }

            switch (key.toLowerCase()) {
                case "chargingprofileid" -> chargingProfileId = Integer.parseInt(value);
                case "stacklevel" -> stackLevel = Integer.parseInt(value);
                case "purpose" -> purpose = parseProfilePurpose(value);
                case "limit" -> limit = Double.parseDouble(value);
                case "unit" -> unit = ChargingRateUnit.fromValue(value);
                case "duration" -> duration = Integer.parseInt(value);
                case "numberphases" -> numberPhases = Integer.parseInt(value);
            }
        }

        // Créer le profil
        ChargingProfile profile = ChargingProfile.builder()
                .chargingProfileId(chargingProfileId)
                .stackLevel(stackLevel)
                .chargingProfilePurpose(purpose)
                .chargingProfileKind(ChargingProfileKind.ABSOLUTE)
                .chargingSchedule(ChargingSchedule.builder()
                        .duration(duration)
                        .startSchedule(LocalDateTime.now())
                        .chargingRateUnit(unit)
                        .chargingSchedulePeriod(List.of(
                                ChargingSchedulePeriod.builder()
                                        .startPeriod(0)
                                        .limit(limit)
                                        .numberPhases(numberPhases)
                                        .build()
                        ))
                        .build())
                .build();

        String status = smartChargingService.setChargingProfile(sessionId, profile);

        context.set(CTX_LAST_PROFILE, profile);
        context.set(CTX_LAST_PROFILE_STATUS, status);
        context.set("lastProfileLimit", limit);
        context.set("lastProfileUnit", unit);

        log.info("[SMART] SetChargingProfile: id={}, purpose={}, limit={}{} -> {}",
                chargingProfileId, purpose, limit, unit.getValue(), status);
    }

    @When("j'envoie SetChargingProfile en Ampères avec limit={int}A")
    public void whenSendSetChargingProfileAmps(int limitA, TnrContext context) {
        String sessionId = context.getCurrentSessionId();

        ChargingProfile profile = ChargingProfile.builder()
                .chargingProfileId(context.incrementCounter("profileId"))
                .stackLevel(0)
                .chargingProfilePurpose(ChargingProfilePurpose.TX_PROFILE)
                .chargingProfileKind(ChargingProfileKind.ABSOLUTE)
                .chargingSchedule(ChargingSchedule.builder()
                        .duration(86400)
                        .startSchedule(LocalDateTime.now())
                        .chargingRateUnit(ChargingRateUnit.A)
                        .chargingSchedulePeriod(List.of(
                                ChargingSchedulePeriod.builder()
                                        .startPeriod(0)
                                        .limit(limitA)
                                        .numberPhases(DEFAULT_PHASES)
                                        .build()
                        ))
                        .build())
                .build();

        String status = smartChargingService.setChargingProfile(sessionId, profile);

        context.set(CTX_LAST_PROFILE, profile);
        context.set(CTX_LAST_PROFILE_STATUS, status);
        context.set("lastProfileLimitA", limitA);
        context.set("lastProfileUnit", ChargingRateUnit.A);

        log.info("[SMART] SetChargingProfile: {}A -> {}", limitA, status);
    }

    @When("j'envoie SetChargingProfile en Watts avec limit={int}W")
    public void whenSendSetChargingProfileWatts(int limitW, TnrContext context) {
        String sessionId = context.getCurrentSessionId();

        ChargingProfile profile = ChargingProfile.builder()
                .chargingProfileId(context.incrementCounter("profileId"))
                .stackLevel(0)
                .chargingProfilePurpose(ChargingProfilePurpose.TX_PROFILE)
                .chargingProfileKind(ChargingProfileKind.ABSOLUTE)
                .chargingSchedule(ChargingSchedule.builder()
                        .duration(86400)
                        .startSchedule(LocalDateTime.now())
                        .chargingRateUnit(ChargingRateUnit.W)
                        .chargingSchedulePeriod(List.of(
                                ChargingSchedulePeriod.builder()
                                        .startPeriod(0)
                                        .limit(limitW)
                                        .numberPhases(DEFAULT_PHASES)
                                        .build()
                        ))
                        .build())
                .build();

        String status = smartChargingService.setChargingProfile(sessionId, profile);

        context.set(CTX_LAST_PROFILE, profile);
        context.set(CTX_LAST_PROFILE_STATUS, status);
        context.set("lastProfileLimit", limitW);
        context.set("lastProfileUnit", ChargingRateUnit.W);

        log.info("[SMART] SetChargingProfile: {}W -> {}", limitW, status);
    }

    // =========================================================================
    // WHEN Steps - ClearChargingProfile
    // =========================================================================

    @When("j'envoie ClearChargingProfile")
    public void whenSendClearChargingProfile(TnrContext context) {
        String sessionId = context.getCurrentSessionId();

        String status = smartChargingService.clearChargingProfile(sessionId, null, null, null);

        context.set(CTX_LAST_PROFILE_STATUS, status);
        log.info("[SMART] ClearChargingProfile (all) -> {}", status);
    }

    @When("j'envoie ClearChargingProfile avec id={int}")
    public void whenSendClearChargingProfileById(int profileId, TnrContext context) {
        String sessionId = context.getCurrentSessionId();

        String status = smartChargingService.clearChargingProfile(sessionId, profileId, null, null);

        context.set(CTX_LAST_PROFILE_STATUS, status);
        log.info("[SMART] ClearChargingProfile (id={}) -> {}", profileId, status);
    }

    @When("j'envoie ClearChargingProfile avec stackLevel={int}")
    public void whenSendClearChargingProfileByStackLevel(int stackLevel, TnrContext context) {
        String sessionId = context.getCurrentSessionId();

        String status = smartChargingService.clearChargingProfile(sessionId, null, stackLevel, null);

        context.set(CTX_LAST_PROFILE_STATUS, status);
        log.info("[SMART] ClearChargingProfile (stackLevel={}) -> {}", stackLevel, status);
    }

    // =========================================================================
    // WHEN Steps - GetCompositeSchedule
    // =========================================================================

    @When("je demande GetCompositeSchedule")
    public void whenGetCompositeSchedule(TnrContext context) {
        whenGetCompositeScheduleWithParams(3600, "W", context);
    }

    @When("je demande GetCompositeSchedule pour {int}s en {word}")
    public void whenGetCompositeScheduleWithParams(int duration, String unit, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        ChargingRateUnit rateUnit = ChargingRateUnit.fromValue(unit);

        ChargingSchedule schedule = smartChargingService.getCompositeSchedule(sessionId, duration, rateUnit);

        context.set(CTX_COMPOSITE_SCHEDULE, schedule);
        context.set("compositeScheduleDuration", duration);
        context.set("compositeScheduleUnit", rateUnit);

        log.info("[SMART] GetCompositeSchedule: duration={}s, unit={}, periods={}",
                duration, unit, schedule.getChargingSchedulePeriod().size());
    }

    // =========================================================================
    // THEN Steps - Profile Status
    // =========================================================================

    @Then("le profil est {string}")
    public void thenProfileStatus(String expectedStatus, TnrContext context) {
        String actualStatus = context.get(CTX_LAST_PROFILE_STATUS);

        if (actualStatus == null) {
            throw new AssertionError("No profile status in context");
        }

        if (!expectedStatus.equalsIgnoreCase(actualStatus)) {
            throw new AssertionError(
                    String.format("Expected profile status '%s' but got '%s'", expectedStatus, actualStatus));
        }

        log.info("[SMART] Profile status verified: {}", actualStatus);
    }

    // =========================================================================
    // THEN Steps - Power Limits
    // =========================================================================

    @Then("la puissance effective est <= {int}W")
    public void thenEffectivePowerLessOrEqual(int maxWatts, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        double currentLimitKw = smartChargingService.getCurrentLimit(sessionId);
        double currentLimitW = currentLimitKw * 1000;

        if (currentLimitW > maxWatts + 0.1) { // Tolérance 0.1W
            throw new AssertionError(
                    String.format("Expected power <= %dW but got %.1fW", maxWatts, currentLimitW));
        }

        log.info("[SMART] Effective power verified: {:.1f}W <= {}W", currentLimitW, maxWatts);
    }

    @Then("la puissance effective est >= {int}W")
    public void thenEffectivePowerGreaterOrEqual(int minWatts, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        double currentLimitKw = smartChargingService.getCurrentLimit(sessionId);
        double currentLimitW = currentLimitKw * 1000;

        if (currentLimitW < minWatts - 0.1) {
            throw new AssertionError(
                    String.format("Expected power >= %dW but got %.1fW", minWatts, currentLimitW));
        }

        log.info("[SMART] Effective power verified: {:.1f}W >= {}W", currentLimitW, minWatts);
    }

    @Then("la limite de charge est {int}W")
    public void thenChargingLimitEquals(int expectedWatts, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        double currentLimitKw = smartChargingService.getCurrentLimit(sessionId);
        double currentLimitW = currentLimitKw * 1000;

        if (Math.abs(currentLimitW - expectedWatts) > 1) { // Tolérance 1W
            throw new AssertionError(
                    String.format("Expected limit %dW but got %.1fW", expectedWatts, currentLimitW));
        }

        log.info("[SMART] Charging limit verified: {:.1f}W", currentLimitW);
    }

    // =========================================================================
    // THEN Steps - Conversion Validation
    // =========================================================================

    @Then("la conversion A->W est correcte:")
    public void thenConversionAToWCorrect(TnrContext context, List<Map<String, String>> dataTable) {
        for (Map<String, String> row : dataTable) {
            int limitA = Integer.parseInt(row.get("limitA"));
            int voltage = Integer.parseInt(row.getOrDefault("voltage", "230"));
            int phases = Integer.parseInt(row.getOrDefault("phases", "3"));
            int expectedW = Integer.parseInt(row.get("expectedW"));

            // Calculer la conversion
            // Triphasé avec tension phase-neutre (230V): factor = phases
            // Triphasé avec tension ligne-ligne (400V): factor = √3
            double factor;
            if (phases == 1) {
                factor = 1.0;
            } else if (voltage < 300) {
                factor = phases;
            } else {
                factor = Math.sqrt(3);
            }
            double calculatedW = voltage * limitA * factor;

            // Tolérance de 1%
            double tolerance = expectedW * 0.01;
            if (Math.abs(calculatedW - expectedW) > tolerance) {
                throw new AssertionError(
                        String.format("Conversion error: %dA * %dV * %.2f = %.1fW (expected %dW)",
                                limitA, voltage, factor, calculatedW, expectedW));
            }

            log.info("[SMART] Conversion verified: {}A @ {}V {}ph = {:.1f}W (expected {}W)",
                    limitA, voltage, phases, calculatedW, expectedW);
        }
    }

    @Then("la conversion W->A est correcte:")
    public void thenConversionWToACorrect(TnrContext context, List<Map<String, String>> dataTable) {
        for (Map<String, String> row : dataTable) {
            int limitW = Integer.parseInt(row.get("limitW"));
            int voltage = Integer.parseInt(row.getOrDefault("voltage", "230"));
            int phases = Integer.parseInt(row.getOrDefault("phases", "3"));
            double expectedA = Double.parseDouble(row.get("expectedA"));

            // Calculer la conversion
            // Triphasé avec tension phase-neutre (230V): factor = phases
            // Triphasé avec tension ligne-ligne (400V): factor = √3
            double factor;
            if (phases == 1) {
                factor = 1.0;
            } else if (voltage < 300) {
                factor = phases;
            } else {
                factor = Math.sqrt(3);
            }
            double calculatedA = limitW / (voltage * factor);

            // Tolérance de 1%
            double tolerance = expectedA * 0.01;
            if (Math.abs(calculatedA - expectedA) > tolerance) {
                throw new AssertionError(
                        String.format("Conversion error: %dW / (%dV * %.2f) = %.2fA (expected %.2fA)",
                                limitW, voltage, factor, calculatedA, expectedA));
            }

            log.info("[SMART] Conversion verified: {}W @ {}V {}ph = {:.2fA (expected {:.2f}A)",
                    limitW, voltage, phases, calculatedA, expectedA);
        }
    }

    // =========================================================================
    // THEN Steps - CompositeSchedule
    // =========================================================================

    @Then("le CompositeSchedule contient {int} périodes")
    public void thenCompositeSchedulePeriodCount(int expectedCount, TnrContext context) {
        ChargingSchedule schedule = context.get(CTX_COMPOSITE_SCHEDULE);

        if (schedule == null) {
            throw new AssertionError("No composite schedule in context");
        }

        int actualCount = schedule.getChargingSchedulePeriod().size();

        if (actualCount != expectedCount) {
            throw new AssertionError(
                    String.format("Expected %d periods but got %d", expectedCount, actualCount));
        }

        log.info("[SMART] CompositeSchedule periods verified: {}", actualCount);
    }

    @Then("le CompositeSchedule a une limite de {int}W")
    public void thenCompositeScheduleLimit(int expectedLimitW, TnrContext context) {
        ChargingSchedule schedule = context.get(CTX_COMPOSITE_SCHEDULE);

        if (schedule == null) {
            throw new AssertionError("No composite schedule in context");
        }

        // Vérifier la première période
        if (schedule.getChargingSchedulePeriod().isEmpty()) {
            throw new AssertionError("CompositeSchedule has no periods");
        }

        double actualLimit = schedule.getChargingSchedulePeriod().get(0).getLimit();

        // Si en Ampères, convertir
        if (schedule.getChargingRateUnit() == ChargingRateUnit.A) {
            String sessionId = context.getCurrentSessionId();
            Session session = sessionService.getSession(sessionId);
            int phases = session.getChargerType().getPhases();
            double voltage = session.getVoltage();
            // Triphasé avec tension phase-neutre (230V): factor = phases
            // Triphasé avec tension ligne-ligne (400V): factor = √3
            double factor;
            if (phases == 1) {
                factor = 1.0;
            } else if (voltage < 300) {
                factor = phases;
            } else {
                factor = Math.sqrt(3);
            }
            actualLimit = voltage * actualLimit * factor;
        }

        if (Math.abs(actualLimit - expectedLimitW) > 1) {
            throw new AssertionError(
                    String.format("Expected limit %dW but got %.1fW", expectedLimitW, actualLimit));
        }

        log.info("[SMART] CompositeSchedule limit verified: {:.1f}W", actualLimit);
    }

    @Then("il y a {int} profils actifs")
    public void thenActiveProfileCount(int expectedCount, TnrContext context) {
        String sessionId = context.getCurrentSessionId();
        List<ChargingProfile> profiles = smartChargingService.getActiveProfiles(sessionId);

        if (profiles.size() != expectedCount) {
            throw new AssertionError(
                    String.format("Expected %d active profiles but got %d", expectedCount, profiles.size()));
        }

        log.info("[SMART] Active profiles count verified: {}", profiles.size());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private ChargingProfilePurpose parseProfilePurpose(String purpose) {
        String normalized = purpose.toLowerCase()
                .replace("_", "")
                .replace("-", "");

        return switch (normalized) {
            case "txprofile", "txp" -> ChargingProfilePurpose.TX_PROFILE;
            case "txdefaultprofile", "txdefault", "default" -> ChargingProfilePurpose.TX_DEFAULT_PROFILE;
            case "chargepointmaxprofile", "cpmaxprofile", "max" -> ChargingProfilePurpose.CHARGE_POINT_MAX_PROFILE;
            default -> ChargingProfilePurpose.TX_DEFAULT_PROFILE;
        };
    }
}
