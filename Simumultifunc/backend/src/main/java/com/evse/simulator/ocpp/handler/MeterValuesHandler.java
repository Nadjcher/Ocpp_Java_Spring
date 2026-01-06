package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message MeterValues.
 * Envoie les valeurs de compteur pendant une charge.
 * Format simplifié: Energy, Current/Voltage par phase, Temperature, Power.Offered
 */
@Component
public class MeterValuesHandler extends AbstractOcppHandler {

    private static final String CONTEXT_PERIODIC = "Sample.Periodic";
    private static final String LOCATION_INLET = "Inlet";
    private static final String LOCATION_BODY = "Body";

    @Override
    public OCPPAction getAction() {
        return OCPPAction.METER_VALUES;
    }

    @Override
    public Map<String, Object> buildPayload(OcppMessageContext context) {
        int connectorId = context.getConnectorId() > 0 ? context.getConnectorId() : 1;
        Integer transactionId = context.getTransactionId();

        List<Map<String, Object>> meterValue = new ArrayList<>();
        meterValue.add(buildMeterValueSample(context));

        Map<String, Object> payload = createPayload(
            "connectorId", connectorId,
            "meterValue", meterValue
        );

        if (transactionId != null) {
            payload.put("transactionId", transactionId);
        }

        return payload;
    }

    /**
     * Construit un échantillon de valeurs de compteur simplifié.
     * Format: Energy totale, Current par phase, Voltage par phase, Temperature, Power.Offered
     */
    private Map<String, Object> buildMeterValueSample(OcppMessageContext context) {
        List<Map<String, Object>> sampledValues = new ArrayList<>();

        Session session = context.getSession();
        int phases = session != null ? session.getEffectivePhases() : 3;

        // 1. Énergie active importée totale (Wh)
        long energyWh = context.getMeterValue() != null ? context.getMeterValue() : 0L;
        sampledValues.add(createSampledValue(
            String.valueOf(energyWh),
            "Energy.Active.Import.Register",
            "Wh",
            LOCATION_INLET,
            null
        ));

        if (session != null) {
            double currentA = session.getMaxCurrentA();
            double voltageV = session.getVoltage();
            double powerOfferedW = session.getMaxPowerKw() * 1000;

            // Calculer la tension phase-neutre si nécessaire
            double phaseVoltage = voltageV > 350 ? voltageV / Math.sqrt(3) : voltageV;

            // 2. Courant par phase (L1, L2, L3)
            String[] phaseNames = {"L1", "L2", "L3"};
            for (int i = 0; i < Math.min(phases, 3); i++) {
                sampledValues.add(createSampledValue(
                    String.format("%.3f", currentA),
                    "Current.Import",
                    "A",
                    LOCATION_INLET,
                    phaseNames[i]
                ));
            }

            // 3. Tension par phase (L1, L2, L3)
            // Légère variation réaliste entre phases
            double[] voltageVariations = {0, -3, 1};
            for (int i = 0; i < Math.min(phases, 3); i++) {
                int voltage = (int) Math.round(phaseVoltage + voltageVariations[i]);
                sampledValues.add(createSampledValue(
                    String.valueOf(voltage),
                    "Voltage",
                    "V",
                    LOCATION_INLET,
                    phaseNames[i]
                ));
            }

            // 4. Température
            double temperature = session.getTemperature();
            if (temperature <= 0 || temperature == 25.0) {
                temperature = 12; // Valeur par défaut réaliste
            }
            sampledValues.add(createSampledValue(
                String.valueOf((int) temperature),
                "Temperature",
                "Celsius",
                LOCATION_BODY,
                null
            ));

            // 5. Power.Offered (puissance maximale offerte)
            sampledValues.add(createSampledValue(
                String.valueOf((int) powerOfferedW),
                "Power.Offered",
                "W",
                null,
                null
            ));
        }

        return createPayload(
            "timestamp", formatTimestamp(),
            "sampledValue", sampledValues
        );
    }

    /**
     * Crée une valeur échantillonnée avec context Sample.Periodic.
     */
    private Map<String, Object> createSampledValue(String value, String measurand, String unit, String location, String phase) {
        Map<String, Object> sv = createPayload(
            "value", value,
            "context", CONTEXT_PERIODIC,
            "measurand", measurand,
            "unit", unit
        );

        if (location != null) {
            sv.put("location", location);
        }

        if (phase != null) {
            sv.put("phase", phase);
        }

        return sv;
    }

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        // MeterValues.conf est vide selon OCPP 1.6
        log.debug("[{}] MeterValues acknowledged", sessionId);
        return CompletableFuture.completedFuture(response);
    }
}
