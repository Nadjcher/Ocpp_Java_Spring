package com.evse.simulator.ocpp.v16.handlers.core;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.model.payload.common.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler pour GetConfiguration (CS → CP).
 */
@Slf4j
@Component
public class GetConfigurationHandler extends AbstractOcpp16IncomingHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.GET_CONFIGURATION;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        // Récupérer les clés demandées (optionnel)
        List<String> requestedKeys = null;
        Object keysObj = payload.get("key");
        if (keysObj instanceof List) {
            requestedKeys = (List<String>) keysObj;
        }

        // Construire la liste des configurations
        List<Map<String, Object>> configurationKey = new ArrayList<>();
        List<String> unknownKey = new ArrayList<>();

        if (requestedKeys == null || requestedKeys.isEmpty()) {
            // Retourner toutes les configurations
            configurationKey.addAll(getAllConfigurations(session));
        } else {
            // Retourner uniquement les clés demandées
            for (String key : requestedKeys) {
                KeyValue kv = getConfiguration(session, key);
                if (kv != null) {
                    configurationKey.add(kv.toMap());
                } else {
                    unknownKey.add(key);
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("configurationKey", configurationKey);
        if (!unknownKey.isEmpty()) {
            response.put("unknownKey", unknownKey);
        }

        logToSession(session, String.format(
            "GetConfiguration: %d keys returned, %d unknown",
            configurationKey.size(), unknownKey.size()));

        logExit(session, response);
        return response;
    }

    private List<Map<String, Object>> getAllConfigurations(Session session) {
        List<Map<String, Object>> configs = new ArrayList<>();

        // Core Profile
        configs.add(KeyValue.builder()
            .key("HeartbeatInterval")
            .readonly(false)
            .value(String.valueOf(session.getHeartbeatInterval()))
            .build().toMap());

        configs.add(KeyValue.builder()
            .key("MeterValueSampleInterval")
            .readonly(false)
            .value(String.valueOf(session.getMeterValuesInterval()))
            .build().toMap());

        configs.add(KeyValue.builder()
            .key("NumberOfConnectors")
            .readonly(true)
            .value(String.valueOf(session.getConnectorId()))
            .build().toMap());

        configs.add(KeyValue.builder()
            .key("ChargePointVendor")
            .readonly(true)
            .value(session.getVendor())
            .build().toMap());

        configs.add(KeyValue.builder()
            .key("ChargePointModel")
            .readonly(true)
            .value(session.getModel())
            .build().toMap());

        configs.add(KeyValue.builder()
            .key("ChargePointSerialNumber")
            .readonly(true)
            .value(session.getSerialNumber() != null ? session.getSerialNumber() : session.getCpId())
            .build().toMap());

        configs.add(KeyValue.builder()
            .key("FirmwareVersion")
            .readonly(true)
            .value(session.getFirmwareVersion())
            .build().toMap());

        configs.add(KeyValue.builder()
            .key("SupportedFeatureProfiles")
            .readonly(true)
            .value("Core,SmartCharging,RemoteTrigger")
            .build().toMap());

        configs.add(KeyValue.builder()
            .key("ConnectionTimeOut")
            .readonly(false)
            .value("60")
            .build().toMap());

        configs.add(KeyValue.builder()
            .key("MeterValuesSampledData")
            .readonly(false)
            .value("Energy.Active.Import.Register,Power.Active.Import,SoC,Current.Import,Voltage")
            .build().toMap());

        configs.add(KeyValue.builder()
            .key("ClockAlignedDataInterval")
            .readonly(false)
            .value(String.valueOf(session.getClockAlignedDataInterval()))
            .build().toMap());

        return configs;
    }

    private KeyValue getConfiguration(Session session, String key) {
        return switch (key) {
            case "HeartbeatInterval" -> KeyValue.builder()
                .key(key).readonly(false)
                .value(String.valueOf(session.getHeartbeatInterval()))
                .build();
            case "MeterValueSampleInterval" -> KeyValue.builder()
                .key(key).readonly(false)
                .value(String.valueOf(session.getMeterValuesInterval()))
                .build();
            case "NumberOfConnectors" -> KeyValue.builder()
                .key(key).readonly(true)
                .value(String.valueOf(session.getConnectorId()))
                .build();
            case "ChargePointVendor" -> KeyValue.builder()
                .key(key).readonly(true)
                .value(session.getVendor())
                .build();
            case "ChargePointModel" -> KeyValue.builder()
                .key(key).readonly(true)
                .value(session.getModel())
                .build();
            case "ChargePointSerialNumber" -> KeyValue.builder()
                .key(key).readonly(true)
                .value(session.getSerialNumber() != null ? session.getSerialNumber() : session.getCpId())
                .build();
            case "FirmwareVersion" -> KeyValue.builder()
                .key(key).readonly(true)
                .value(session.getFirmwareVersion())
                .build();
            case "SupportedFeatureProfiles" -> KeyValue.builder()
                .key(key).readonly(true)
                .value("Core,SmartCharging,RemoteTrigger")
                .build();
            case "ClockAlignedDataInterval" -> KeyValue.builder()
                .key(key).readonly(false)
                .value(String.valueOf(session.getClockAlignedDataInterval()))
                .build();
            default -> null;
        };
    }
}
