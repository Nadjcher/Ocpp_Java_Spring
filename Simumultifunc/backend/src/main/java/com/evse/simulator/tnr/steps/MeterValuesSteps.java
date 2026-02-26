package com.evse.simulator.tnr.steps;

import com.evse.simulator.model.Session;
import com.evse.simulator.service.SessionService;
import com.evse.simulator.tnr.engine.TnrContext;
import com.evse.simulator.tnr.model.OcppMessageCapture;
import com.evse.simulator.tnr.steps.annotations.*;
import com.evse.simulator.tnr.validation.EnergyValidator;
import com.evse.simulator.tnr.validation.EnergyValidator.MeterValuePoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Steps Gherkin pour les MeterValues OCPP.
 * <p>
 * Gère l'envoi, la génération et la validation des MeterValues
 * pendant les sessions de charge.
 * </p>
 *
 * @example
 * <pre>
 * Feature: MeterValues
 *   Scenario: Envoi périodique de MeterValues
 *     Given une transaction active à 11kW
 *     When j'envoie MeterValues toutes les 30 secondes pendant 5 minutes
 *     Then les MeterValues sont cohérentes
 *     And l'énergie totale est > 900Wh
 * </pre>
 */
@Slf4j
@Component
@StepDefinitions(category = "meter-values", description = "Steps pour les MeterValues OCPP")
@RequiredArgsConstructor
public class MeterValuesSteps {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;
    private final EnergyValidator energyValidator;

    // Clés de contexte
    private static final String CTX_TRANSACTION_ID = "transactionId";
    private static final String CTX_METER_VALUES_LIST = "meterValuesList";
    private static final String CTX_TOTAL_ENERGY_WH = "totalEnergyWh";
    private static final String CTX_CURRENT_POWER_W = "chargingPowerW";
    private static final String CTX_CURRENT_SOC = "currentSoc";
    private static final String CTX_VIRTUAL_TIME = "virtualTime";

    // =========================================================================
    // GIVEN Steps - Configuration
    // =========================================================================

    /**
     * Configure un intervalle de MeterValues.
     */
    @Given("un intervalle MeterValues de {int} secondes")
    public void givenMeterValuesInterval(int intervalSeconds, TnrContext context) {
        context.set("meterValuesInterval", intervalSeconds);
        log.info("[METER-VALUES] Interval set to {}s", intervalSeconds);
    }

    /**
     * Configure les mesurandes à inclure.
     */
    @Given("les mesurandes configurés:")
    public void givenMeasurandsConfigured(List<String> measurands, TnrContext context) {
        context.set("configuredMeasurands", measurands);
        log.info("[METER-VALUES] Configured measurands: {}", measurands);
    }

    /**
     * Configure un SoC initial.
     */
    @Given("un SoC initial de {int}%")
    public void givenInitialSoc(int soc, TnrContext context) {
        context.set(CTX_CURRENT_SOC, (double) soc);
        context.set("initialSoc", soc);
        log.info("[METER-VALUES] Initial SoC: {}%", soc);
    }

    // =========================================================================
    // WHEN Steps - Envoi de MeterValues
    // =========================================================================

    /**
     * Envoie une MeterValue avec les mesures actuelles.
     */
    @When("j'envoie MeterValues avec les mesures actuelles")
    public void whenSendMeterValuesWithCurrentMeasures(TnrContext context) {
        Integer transactionId = context.get(CTX_TRANSACTION_ID);
        Double powerW = context.getOrDefault(CTX_CURRENT_POWER_W, 11000.0);
        Double energyWh = context.getOrDefault(CTX_TOTAL_ENERGY_WH, 0.0);
        Double soc = context.getOrDefault(CTX_CURRENT_SOC, 50.0);

        Map<String, Object> meterValue = buildMeterValue(transactionId, powerW, energyWh, soc);
        sendMeterValues(context, meterValue);

        log.info("[METER-VALUES] Sent: power={}W, energy={}Wh, SoC={}%", powerW, energyWh, soc);
    }

    /**
     * Envoie des MeterValues périodiquement.
     */
    @When("j'envoie MeterValues toutes les {int} secondes pendant {int} minutes")
    public void whenSendMeterValuesPeriodically(int intervalSec, int durationMin, TnrContext context) {
        Integer transactionId = context.get(CTX_TRANSACTION_ID);
        Double powerW = context.getOrDefault(CTX_CURRENT_POWER_W, 11000.0);
        Double currentEnergy = context.getOrDefault(CTX_TOTAL_ENERGY_WH, 0.0);
        Double currentSoc = context.getOrDefault(CTX_CURRENT_SOC, 50.0);
        Double batteryCapacity = context.getOrDefault("batteryCapacityWh", 60000.0);

        int totalSeconds = durationMin * 60;
        int numSamples = totalSeconds / intervalSec;

        List<Map<String, Object>> meterValuesList = new ArrayList<>();
        Instant currentTime = context.getOrDefault(CTX_VIRTUAL_TIME, Instant.now());

        for (int i = 0; i <= numSamples; i++) {
            // Calculer l'énergie accumulée
            double energyDelta = energyValidator.calculateExpectedEnergy(powerW, intervalSec);
            currentEnergy += (i > 0 ? energyDelta : 0);

            // Calculer le SoC
            double socDelta = (energyDelta / batteryCapacity) * 100;
            currentSoc = Math.min(100, currentSoc + (i > 0 ? socDelta : 0));

            // Créer le MeterValue
            Map<String, Object> meterValue = new HashMap<>();
            meterValue.put("timestamp", currentTime.plusSeconds((long) i * intervalSec).toString());
            meterValue.put("power", powerW);
            meterValue.put("energy", currentEnergy);
            meterValue.put("soc", currentSoc);
            meterValue.put("current", powerW / 230.0);
            meterValue.put("voltage", 230.0);

            meterValuesList.add(meterValue);
        }

        context.set(CTX_METER_VALUES_LIST, meterValuesList);
        context.set("meterValues", meterValuesList); // Pour validation
        context.set(CTX_TOTAL_ENERGY_WH, currentEnergy);
        context.set(CTX_CURRENT_SOC, currentSoc);
        context.set(CTX_VIRTUAL_TIME, currentTime.plusSeconds(totalSeconds));

        // Enregistrer dans l'historique
        for (Map<String, Object> mv : meterValuesList) {
            OcppMessageCapture capture = OcppMessageCapture.builder()
                    .action("MeterValues")
                    .direction(OcppMessageCapture.Direction.OUTBOUND)
                    .messageType(OcppMessageCapture.MessageType.CALL)
                    .timestamp(Instant.parse((String) mv.get("timestamp")))
                    .build();
            context.addOcppMessage(capture);
        }

        log.info("[METER-VALUES] Sent {} MeterValues over {}min at {}s intervals",
                meterValuesList.size(), durationMin, intervalSec);
    }

    /**
     * Envoie une MeterValue avec des valeurs spécifiques.
     */
    @When("j'envoie MeterValues:")
    public void whenSendMeterValuesWithTable(Map<String, String> values, TnrContext context) {
        Integer transactionId = context.get(CTX_TRANSACTION_ID);

        double power = parseDouble(values.get("power"), 0);
        double energy = parseDouble(values.get("energy"), 0);
        double soc = parseDouble(values.get("soc"), 0);

        Map<String, Object> meterValue = buildMeterValue(transactionId, power, energy, soc);
        sendMeterValues(context, meterValue);

        context.set(CTX_CURRENT_POWER_W, power);
        context.set(CTX_TOTAL_ENERGY_WH, energy);
        context.set(CTX_CURRENT_SOC, soc);

        log.info("[METER-VALUES] Sent custom: power={}W, energy={}Wh, SoC={}%", power, energy, soc);
    }

    /**
     * Simule un changement de puissance et envoie MeterValues.
     */
    @When("la puissance change à {double}kW et j'envoie MeterValues")
    public void whenPowerChangesAndSendMeterValues(double newPowerKw, TnrContext context) {
        context.set(CTX_CURRENT_POWER_W, newPowerKw * 1000);

        whenSendMeterValuesWithCurrentMeasures(context);

        log.info("[METER-VALUES] Power changed to {}kW", newPowerKw);
    }

    // =========================================================================
    // THEN Steps - Validations
    // =========================================================================

    /**
     * Vérifie que les MeterValues sont cohérentes.
     */
    @Then("les MeterValues sont cohérentes")
    public void thenMeterValuesAreConsistent(TnrContext context) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> meterValuesList = context.get(CTX_METER_VALUES_LIST);

        if (meterValuesList == null || meterValuesList.size() < 2) {
            log.info("[METER-VALUES] Not enough values to validate consistency");
            return;
        }

        List<MeterValuePoint> points = meterValuesList.stream()
                .map(this::mapToMeterValuePoint)
                .collect(Collectors.toList());

        var result = energyValidator.validateMeterValuesSeries(points, 10.0);

        if (!result.isValid()) {
            StringBuilder sb = new StringBuilder("MeterValues inconsistent:\n");
            result.getErrors().forEach(e -> sb.append("  - ").append(e).append("\n"));
            throw new AssertionError(sb.toString());
        }

        log.info("[METER-VALUES] {} points validated successfully", points.size());
    }

    /**
     * Vérifie que l'énergie totale est supérieure à une valeur.
     */
    @Then("l'énergie totale est > {double}Wh")
    public void thenTotalEnergyGreaterThan(double minEnergy, TnrContext context) {
        Double totalEnergy = context.get(CTX_TOTAL_ENERGY_WH);

        if (totalEnergy == null || totalEnergy <= minEnergy) {
            throw new AssertionError(
                    String.format("Expected total energy > %.2fWh but got %.2f",
                            minEnergy, totalEnergy));
        }

        log.info("[METER-VALUES] Total energy {}Wh > {}Wh: OK", totalEnergy, minEnergy);
    }

    /**
     * Vérifie que l'énergie totale est dans une plage.
     */
    @Then("l'énergie totale est entre {double}Wh et {double}Wh")
    public void thenTotalEnergyBetween(double minEnergy, double maxEnergy, TnrContext context) {
        Double totalEnergy = context.get(CTX_TOTAL_ENERGY_WH);

        if (totalEnergy == null || totalEnergy < minEnergy || totalEnergy > maxEnergy) {
            throw new AssertionError(
                    String.format("Expected energy between %.2f and %.2fWh but got %.2f",
                            minEnergy, maxEnergy, totalEnergy));
        }

        log.info("[METER-VALUES] Total energy {}Wh in [{}, {}]Wh: OK",
                totalEnergy, minEnergy, maxEnergy);
    }

    /**
     * Vérifie que le nombre de MeterValues est correct.
     */
    @Then("{int} MeterValues ont été envoyées")
    public void thenMeterValuesCount(int expectedCount, TnrContext context) {
        List<OcppMessageCapture> meterValues = context.getMessagesByAction("MeterValues");

        if (meterValues.size() != expectedCount) {
            throw new AssertionError(
                    String.format("Expected %d MeterValues but got %d",
                            expectedCount, meterValues.size()));
        }

        log.info("[METER-VALUES] {} MeterValues sent: OK", expectedCount);
    }

    /**
     * Vérifie que le SoC final est correct.
     */
    @Then("le SoC final est {int}%")
    public void thenFinalSocIs(int expectedSoc, TnrContext context) {
        Double currentSoc = context.get(CTX_CURRENT_SOC);
        int actualSoc = currentSoc != null ? currentSoc.intValue() : 0;

        if (actualSoc != expectedSoc) {
            throw new AssertionError(
                    String.format("Expected final SoC %d%% but got %d%%",
                            expectedSoc, actualSoc));
        }

        log.info("[METER-VALUES] Final SoC {}%: OK", actualSoc);
    }

    /**
     * Vérifie que le SoC a augmenté.
     */
    @Then("le SoC a augmenté")
    public void thenSocIncreased(TnrContext context) {
        Integer initialSoc = context.get("initialSoc");
        Double currentSoc = context.get(CTX_CURRENT_SOC);

        if (initialSoc == null || currentSoc == null) {
            throw new AssertionError("SoC values not found in context");
        }

        if (currentSoc <= initialSoc) {
            throw new AssertionError(
                    String.format("SoC should have increased: initial=%d%%, current=%.1f%%",
                            initialSoc, currentSoc));
        }

        log.info("[METER-VALUES] SoC increased: {}% -> {:.1f}%", initialSoc, currentSoc);
    }

    /**
     * Vérifie que l'énergie augmente monotoniquement.
     */
    @Then("l'énergie augmente monotoniquement")
    public void thenEnergyIncreasesMonotonically(TnrContext context) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> meterValuesList = context.get(CTX_METER_VALUES_LIST);

        if (meterValuesList == null || meterValuesList.size() < 2) {
            return; // Pas assez de valeurs
        }

        double previousEnergy = 0;
        for (int i = 0; i < meterValuesList.size(); i++) {
            double energy = ((Number) meterValuesList.get(i).get("energy")).doubleValue();
            if (energy < previousEnergy) {
                throw new AssertionError(
                        String.format("Energy decreased at sample %d: %.2f -> %.2f",
                                i, previousEnergy, energy));
            }
            previousEnergy = energy;
        }

        log.info("[METER-VALUES] Energy increases monotonically: OK");
    }

    /**
     * Vérifie la puissance moyenne.
     */
    @Then("la puissance moyenne est environ {double}kW")
    public void thenAveragePowerIs(double expectedPowerKw, TnrContext context) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> meterValuesList = context.get(CTX_METER_VALUES_LIST);

        if (meterValuesList == null || meterValuesList.isEmpty()) {
            throw new AssertionError("No meter values to analyze");
        }

        double avgPower = meterValuesList.stream()
                .mapToDouble(mv -> ((Number) mv.get("power")).doubleValue())
                .average()
                .orElse(0) / 1000.0;

        double tolerance = expectedPowerKw * 0.1; // 10% tolerance
        if (Math.abs(avgPower - expectedPowerKw) > tolerance) {
            throw new AssertionError(
                    String.format("Expected average power ~%.1fkW but got %.1fkW",
                            expectedPowerKw, avgPower));
        }

        log.info("[METER-VALUES] Average power {:.1f}kW ~= {:.1f}kW: OK", avgPower, expectedPowerKw);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Map<String, Object> buildMeterValue(Integer transactionId, double powerW,
                                                  double energyWh, double soc) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("connectorId", 1);
        payload.put("transactionId", transactionId);

        List<Map<String, Object>> sampledValues = new ArrayList<>();

        // Energy.Active.Import.Register
        sampledValues.add(createSampledValue(
                String.valueOf((int) energyWh),
                "Energy.Active.Import.Register", "Wh", "Outlet", null));

        // Power.Active.Import
        sampledValues.add(createSampledValue(
                String.valueOf((int) powerW),
                "Power.Active.Import", "W", "Outlet", null));

        // Current.Import
        double current = powerW / 230.0;
        sampledValues.add(createSampledValue(
                String.format("%.1f", current),
                "Current.Import", "A", "Outlet", "L1"));

        // Voltage
        sampledValues.add(createSampledValue(
                "230", "Voltage", "V", "Outlet", "L1"));

        // SoC
        if (soc > 0 && soc <= 100) {
            sampledValues.add(createSampledValue(
                    String.valueOf((int) soc),
                    "SoC", "Percent", "EV", null));
        }

        List<Map<String, Object>> meterValue = List.of(
                Map.of(
                        "timestamp", Instant.now().toString(),
                        "sampledValue", sampledValues
                )
        );

        payload.put("meterValue", meterValue);
        return payload;
    }

    private Map<String, Object> createSampledValue(String value, String measurand,
                                                    String unit, String location, String phase) {
        Map<String, Object> sv = new HashMap<>();
        sv.put("value", value);
        sv.put("context", "Sample.Periodic");
        sv.put("format", "Raw");
        sv.put("measurand", measurand);
        sv.put("unit", unit);
        sv.put("location", location);
        if (phase != null) {
            sv.put("phase", phase);
        }
        return sv;
    }

    private void sendMeterValues(TnrContext context, Map<String, Object> payload) {
        // Stocker pour validation ultérieure
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> meterValuesList = context.getOrDefault(CTX_METER_VALUES_LIST, new ArrayList<>());

        Map<String, Object> record = new HashMap<>();
        record.put("timestamp", Instant.now().toString());
        record.put("power", context.getOrDefault(CTX_CURRENT_POWER_W, 0.0));
        record.put("energy", context.getOrDefault(CTX_TOTAL_ENERGY_WH, 0.0));
        record.put("soc", context.getOrDefault(CTX_CURRENT_SOC, 0.0));
        meterValuesList.add(record);

        context.set(CTX_METER_VALUES_LIST, meterValuesList);

        // Capturer le message
        OcppMessageCapture capture = OcppMessageCapture.builder()
                .action("MeterValues")
                .direction(OcppMessageCapture.Direction.OUTBOUND)
                .messageType(OcppMessageCapture.MessageType.CALL)
                .timestamp(Instant.now())
                .build();
        context.addOcppMessage(capture);
    }

    private MeterValuePoint mapToMeterValuePoint(Map<String, Object> raw) {
        return MeterValuePoint.builder()
                .timestampSec(parseTimestamp(raw.get("timestamp")))
                .powerW(parseDouble(raw.get("power"), 0))
                .energyWh(parseDouble(raw.get("energy"), 0))
                .socPercent(parseDouble(raw.get("soc"), 0))
                .currentA(parseDouble(raw.get("current"), 0))
                .voltageV(parseDouble(raw.get("voltage"), 230))
                .build();
    }

    private long parseTimestamp(Object value) {
        if (value == null) return Instant.now().getEpochSecond();
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Instant.parse(value.toString()).getEpochSecond();
        } catch (Exception e) {
            return Instant.now().getEpochSecond();
        }
    }

    private double parseDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString().replaceAll("[^0-9.-]", ""));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
