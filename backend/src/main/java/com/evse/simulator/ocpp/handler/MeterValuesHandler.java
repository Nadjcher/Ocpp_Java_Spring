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
 */
@Component
public class MeterValuesHandler extends AbstractOcppHandler {

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
     * Construit un échantillon de valeurs de compteur.
     */
    private Map<String, Object> buildMeterValueSample(OcppMessageContext context) {
        List<Map<String, Object>> sampledValues = new ArrayList<>();

        Session session = context.getSession();

        // Énergie active importée (Wh)
        long energyWh = context.getMeterValue() != null ? context.getMeterValue() : 0L;
        sampledValues.add(createSampledValue(
            String.valueOf(energyWh),
            "Energy.Active.Import.Register",
            "Wh",
            "Outlet"
        ));

        // Puissance active instantanée (W)
        if (session != null) {
            double powerW = session.getCurrentPowerKw() * 1000;
            sampledValues.add(createSampledValue(
                String.valueOf((int) powerW),
                "Power.Active.Import",
                "W",
                "Outlet"
            ));

            // SoC si disponible
            if (session.getSoc() > 0) {
                sampledValues.add(createSampledValue(
                    String.valueOf((int) session.getSoc()),
                    "SoC",
                    "Percent",
                    null
                ));
            }

            // Courant
            double currentA = session.getCurrentA();
            if (currentA > 0) {
                sampledValues.add(createSampledValue(
                    String.format("%.1f", currentA),
                    "Current.Import",
                    "A",
                    "Outlet"
                ));
            }

            // Tension
            double voltageV = session.getVoltage();
            if (voltageV > 0) {
                sampledValues.add(createSampledValue(
                    String.format("%.1f", voltageV),
                    "Voltage",
                    "V",
                    "Outlet"
                ));
            }
        }

        return createPayload(
            "timestamp", formatTimestamp(),
            "sampledValue", sampledValues
        );
    }

    /**
     * Crée une valeur échantillonnée.
     */
    private Map<String, Object> createSampledValue(String value, String measurand, String unit, String location) {
        Map<String, Object> sv = createPayload(
            "value", value,
            "measurand", measurand,
            "unit", unit
        );

        if (location != null) {
            sv.put("location", location);
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
