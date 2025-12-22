package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler pour ChangeConfiguration (OCPP 1.6).
 */
@Component
@Slf4j
public class ChangeConfigurationHandler implements Ocpp16IncomingHandler {

    private static final Set<String> READONLY_KEYS = Set.of(
            "NumberOfConnectors",
            "ChargePointVendor",
            "ChargePointModel",
            "FirmwareVersion",
            "SupportedFeatureProfiles",
            "ReserveConnectorZeroSupported"
    );

    @Override
    public String getAction() {
        return "ChangeConfiguration";
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        String key = (String) payload.get("key");
        String value = (String) payload.get("value");

        log.info("[ChangeConfiguration] Session={}, key={}, value={}", session.getId(), key, value);

        if (key == null || value == null) {
            response.put("status", "Rejected");
            return response;
        }

        // Vérifier si la clé est en lecture seule
        if (READONLY_KEYS.contains(key)) {
            response.put("status", "Rejected");
            session.addLog(LogEntry.warn("ChangeConfiguration rejected: " + key + " is readonly"));
            return response;
        }

        // Appliquer le changement
        String status = applyConfiguration(session, key, value);
        response.put("status", status);

        if ("Accepted".equals(status)) {
            session.addLog(LogEntry.info("Configuration", key + " = " + value));
        }

        return response;
    }

    private String applyConfiguration(Session session, String key, String value) {
        try {
            switch (key) {
                case "HeartbeatInterval" -> {
                    int interval = Integer.parseInt(value);
                    if (interval > 0) {
                        session.setHeartbeatInterval(interval);
                        return "Accepted";
                    }
                    return "Rejected";
                }
                case "MeterValueSampleInterval" -> {
                    int interval = Integer.parseInt(value);
                    if (interval > 0) {
                        session.setMeterValuesInterval(interval);
                        return "Accepted";
                    }
                    return "Rejected";
                }
                case "AuthorizeRemoteTxRequests" -> {
                    // Accepter mais pas d'effet réel
                    return "Accepted";
                }
                case "LocalPreAuthorize" -> {
                    // Accepter mais pas d'effet réel
                    return "Accepted";
                }
                default -> {
                    return "NotSupported";
                }
            }
        } catch (NumberFormatException e) {
            return "Rejected";
        }
    }
}
