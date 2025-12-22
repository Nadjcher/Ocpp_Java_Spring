package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler pour GetConfiguration (OCPP 1.6).
 */
@Component
@Slf4j
public class GetConfigurationHandler implements Ocpp16IncomingHandler {

    @Override
    public String getAction() {
        return "GetConfiguration";
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) payload.get("key");

        List<Map<String, Object>> configurationKey = new ArrayList<>();
        List<String> unknownKey = new ArrayList<>();

        // Toutes les clés configurables
        Map<String, ConfigEntry> allConfig = buildConfiguration(session);

        if (keys == null || keys.isEmpty()) {
            // Retourner toutes les clés
            for (Map.Entry<String, ConfigEntry> entry : allConfig.entrySet()) {
                configurationKey.add(buildConfigKeyEntry(entry.getKey(), entry.getValue()));
            }
        } else {
            // Retourner uniquement les clés demandées
            for (String key : keys) {
                ConfigEntry entry = allConfig.get(key);
                if (entry != null) {
                    configurationKey.add(buildConfigKeyEntry(key, entry));
                } else {
                    unknownKey.add(key);
                }
            }
        }

        response.put("configurationKey", configurationKey);
        if (!unknownKey.isEmpty()) {
            response.put("unknownKey", unknownKey);
        }

        log.debug("[GetConfiguration] Returned {} keys, {} unknown", configurationKey.size(), unknownKey.size());

        return response;
    }

    private Map<String, ConfigEntry> buildConfiguration(Session session) {
        Map<String, ConfigEntry> config = new LinkedHashMap<>();

        config.put("HeartbeatInterval", new ConfigEntry(String.valueOf(session.getHeartbeatInterval()), false));
        config.put("MeterValueSampleInterval", new ConfigEntry(String.valueOf(session.getMeterValuesInterval()), false));
        config.put("NumberOfConnectors", new ConfigEntry("1", true));
        config.put("ChargePointVendor", new ConfigEntry(session.getVendor(), true));
        config.put("ChargePointModel", new ConfigEntry(session.getModel(), true));
        config.put("FirmwareVersion", new ConfigEntry(session.getFirmwareVersion(), true));
        config.put("SupportedFeatureProfiles", new ConfigEntry("Core,SmartCharging,Reservation", true));
        config.put("AuthorizeRemoteTxRequests", new ConfigEntry("true", false));
        config.put("LocalPreAuthorize", new ConfigEntry("false", false));
        config.put("ReserveConnectorZeroSupported", new ConfigEntry("true", true));

        return config;
    }

    private Map<String, Object> buildConfigKeyEntry(String key, ConfigEntry entry) {
        Map<String, Object> keyEntry = new HashMap<>();
        keyEntry.put("key", key);
        keyEntry.put("readonly", entry.readonly);
        if (entry.value != null) {
            keyEntry.put("value", entry.value);
        }
        return keyEntry;
    }

    private record ConfigEntry(String value, boolean readonly) {}
}
