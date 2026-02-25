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
    private static final String CONTEXT_CLOCK_ALIGNED = "Sample.Clock";
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

        // Déterminer le contexte de lecture (Sample.Periodic ou Sample.Clock)
        String readingContext = context.getReadingContext() != null ?
            context.getReadingContext() : CONTEXT_PERIODIC;

        // 1. Énergie active importée totale (Wh)
        long energyWh = context.getMeterValue() != null ? context.getMeterValue() : 0L;
        sampledValues.add(createSampledValue(
            String.valueOf(energyWh),
            "Energy.Active.Import.Register",
            "Wh",
            LOCATION_INLET,
            null,
            readingContext
        ));

        if (session != null) {
            double voltageV = session.getVoltage();
            double powerActiveW = session.getCurrentPowerKw() * 1000;  // Puissance actuelle consommée (Import)

            // Power.Offered = MIN(setpoint, physLim) - calculé dans simulateCharging
            double offeredPowerKw = session.getOfferedPowerKw();
            double powerOfferedW = offeredPowerKw > 0 ? offeredPowerKw * 1000 : session.getMaxPowerKw() * 1000;

            // Current.Offered (pour AC) = MIN(setpoint_A, physLim_A)
            double offeredCurrentA = session.getOfferedCurrentA();
            boolean isDC = session.getChargerType() != null && session.getChargerType().isDC();

            // Calculer la tension phase-neutre si nécessaire
            double phaseVoltage = voltageV > 350 ? voltageV / Math.sqrt(3) : voltageV;

            // Calculer le courant réel depuis la puissance actuelle
            // P = √3 × V × I (triphasé) ou P = V × I (monophasé)
            double currentA;
            if (phases >= 3) {
                // Triphasé: I = P / (√3 × V)
                currentA = powerActiveW / (Math.sqrt(3) * phaseVoltage * phases / 3);
            } else {
                // Monophasé: I = P / V
                currentA = powerActiveW / (phaseVoltage * phases);
            }

            // 2. Courant par phase (L1, L2, L3) - calculé depuis puissance réelle
            //    AC: currentImport = MIN(setpoint, CNL, physLim)
            String[] phaseNames = {"L1", "L2", "L3"};
            for (int i = 0; i < Math.min(phases, 3); i++) {
                sampledValues.add(createSampledValue(
                    String.format("%.2f", currentA),
                    "Current.Import",
                    "A",
                    LOCATION_INLET,
                    phaseNames[i],
                    readingContext
                ));
            }

            // 2b. Current.Offered par phase (AC uniquement)
            //     AC: currentOffered = MIN(setpoint, physLim) - sans CNL véhicule
            if (!isDC && offeredCurrentA > 0) {
                for (int i = 0; i < Math.min(phases, 3); i++) {
                    sampledValues.add(createSampledValue(
                        String.format("%.2f", offeredCurrentA),
                        "Current.Offered",
                        "A",
                        LOCATION_INLET,
                        phaseNames[i],
                        readingContext
                    ));
                }
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
                    phaseNames[i],
                    readingContext
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
                null,
                readingContext
            ));

            // 5. Power.Active.Import (puissance actuelle consommée)
            sampledValues.add(createSampledValue(
                String.valueOf((int) powerActiveW),
                "Power.Active.Import",
                "W",
                null,
                null,
                readingContext
            ));

            // 6. Power.Offered (puissance maximale offerte)
            sampledValues.add(createSampledValue(
                String.valueOf((int) powerOfferedW),
                "Power.Offered",
                "W",
                null,
                null,
                readingContext
            ));
        }

        return createPayload(
            "timestamp", formatTimestamp(),
            "sampledValue", sampledValues
        );
    }

    /**
     * Crée une valeur échantillonnée avec le contexte spécifié.
     *
     * @param value valeur mesurée
     * @param measurand type de mesure
     * @param unit unité de mesure
     * @param location emplacement (Inlet, Body, etc.)
     * @param phase phase (L1, L2, L3) ou null
     * @param context contexte de lecture (Sample.Periodic ou Sample.Clock)
     * @return SampledValue au format OCPP
     */
    private Map<String, Object> createSampledValue(String value, String measurand, String unit,
                                                    String location, String phase, String context) {
        Map<String, Object> sv = createPayload(
            "value", value,
            "context", context,
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
