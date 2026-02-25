package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.model.types.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message MeterValues (OCPP 1.6).
 * Envoie les mesures essentielles pendant une charge :
 * Energy.Active.Import.Register, Power.Active.Import, Current.Import/phase, Voltage/phase, SoC.
 */
@Component
public class MeterValuesHandler extends AbstractOcppHandler {

    private static final Phase[] PHASE_NAMES = {Phase.L1, Phase.L2, Phase.L3};

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
     * Construit un échantillon MeterValue conforme OCPP 1.6.
     * Mesurands envoyés : Energy, Power, Current/phase, Voltage/phase, SoC (si disponible).
     */
    private Map<String, Object> buildMeterValueSample(OcppMessageContext context) {
        List<Map<String, Object>> sampledValues = new ArrayList<>();

        Session session = context.getSession();
        int phases = session != null ? session.getEffectivePhases() : 1;

        ReadingContext readingContext = resolveReadingContext(context);

        // 1. Energy.Active.Import.Register (Wh) - cumulative
        long energyWh = context.getMeterValue() != null ? context.getMeterValue() : 0L;
        sampledValues.add(createSampledValue(
            String.valueOf(energyWh),
            Measurand.ENERGY_ACTIVE_IMPORT_REGISTER,
            UnitOfMeasure.WH,
            Location.OUTLET,
            null,
            readingContext
        ));

        if (session != null) {
            double voltageV = session.getVoltage();
            double powerActiveW = session.getCurrentPowerKw() * 1000;

            // Tension phase-neutre (conversion si tension ligne-ligne > 350V)
            double phaseVoltage = voltageV > 350 ? voltageV / Math.sqrt(3) : voltageV;

            // Courant par phase : I = P / (V_pn × nb_phases)
            double currentA = phaseVoltage > 0 && phases > 0
                ? powerActiveW / (phaseVoltage * phases)
                : 0;

            // 2. Power.Active.Import (W)
            sampledValues.add(createSampledValue(
                String.valueOf((int) Math.round(powerActiveW)),
                Measurand.POWER_ACTIVE_IMPORT,
                UnitOfMeasure.W,
                Location.OUTLET,
                null,
                readingContext
            ));

            // 3. Current.Import par phase (A)
            for (int i = 0; i < Math.min(phases, 3); i++) {
                sampledValues.add(createSampledValue(
                    String.format("%.1f", currentA),
                    Measurand.CURRENT_IMPORT,
                    UnitOfMeasure.A,
                    Location.OUTLET,
                    PHASE_NAMES[i],
                    readingContext
                ));
            }

            // 4. Voltage par phase (V)
            double[] voltageVariations = {0, -2, 1};
            for (int i = 0; i < Math.min(phases, 3); i++) {
                int voltage = (int) Math.round(phaseVoltage + voltageVariations[i]);
                sampledValues.add(createSampledValue(
                    String.valueOf(voltage),
                    Measurand.VOLTAGE,
                    UnitOfMeasure.V,
                    Location.OUTLET,
                    PHASE_NAMES[i],
                    readingContext
                ));
            }

            // 5. SoC (%) - uniquement si disponible (location = EV)
            double soc = session.getSoc();
            if (soc > 0 && soc <= 100) {
                sampledValues.add(createSampledValue(
                    String.valueOf((int) Math.round(soc)),
                    Measurand.SOC,
                    UnitOfMeasure.PERCENT,
                    Location.EV,
                    null,
                    readingContext
                ));
            }
        }

        return createPayload(
            "timestamp", formatTimestamp(),
            "sampledValue", sampledValues
        );
    }

    /**
     * Résout le contexte de lecture depuis le contexte du message.
     */
    private ReadingContext resolveReadingContext(OcppMessageContext context) {
        if (context.getReadingContext() != null) {
            try {
                return ReadingContext.valueOf(context.getReadingContext()
                    .replace(".", "_").toUpperCase());
            } catch (IllegalArgumentException e) {
                // Fallback : essayer de matcher la valeur OCPP directement
                for (ReadingContext rc : ReadingContext.values()) {
                    if (rc.getValue().equals(context.getReadingContext())) {
                        return rc;
                    }
                }
            }
        }
        return ReadingContext.SAMPLE_PERIODIC;
    }

    /**
     * Crée une SampledValue conforme OCPP 1.6.
     */
    private Map<String, Object> createSampledValue(String value, Measurand measurand,
                                                    UnitOfMeasure unit, Location location,
                                                    Phase phase, ReadingContext context) {
        Map<String, Object> sv = createPayload(
            "value", value,
            "context", context.getValue(),
            "format", ValueFormat.RAW.getValue(),
            "measurand", measurand.getValue(),
            "unit", unit.getValue()
        );

        if (location != null) {
            sv.put("location", location.getValue());
        }

        if (phase != null) {
            sv.put("phase", phase.getValue());
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
