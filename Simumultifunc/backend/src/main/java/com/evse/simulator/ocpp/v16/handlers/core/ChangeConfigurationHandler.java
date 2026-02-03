package com.evse.simulator.ocpp.v16.handlers.core;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.ConfigurationStatus;
import com.evse.simulator.service.OCPPService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Handler pour ChangeConfiguration (CS → CP).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeConfigurationHandler extends AbstractOcpp16IncomingHandler {

    @Lazy
    private final OCPPService ocppService;

    // Clés de configuration supportées et modifiables
    private static final Set<String> WRITABLE_KEYS = Set.of(
        "HeartbeatInterval",
        "MeterValueSampleInterval",
        "MeterValuesSampledData",
        "ConnectionTimeOut",
        "ClockAlignedDataInterval"
    );

    // Clés en lecture seule
    private static final Set<String> READONLY_KEYS = Set.of(
        "NumberOfConnectors",
        "ChargePointVendor",
        "ChargePointModel",
        "ChargePointSerialNumber",
        "FirmwareVersion",
        "SupportedFeatureProfiles"
    );

    @Override
    public OCPPAction getAction() {
        return OCPPAction.CHANGE_CONFIGURATION;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "key");
        requireField(payload, "value");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        String key = getString(payload, "key", true);
        String value = getString(payload, "value", true);

        ConfigurationStatus status = setConfiguration(session, key, value);

        Map<String, Object> response = createResponse(status);
        logExit(session, response);
        return response;
    }

    private ConfigurationStatus setConfiguration(Session session, String key, String value) {
        // Vérifier si la clé est en lecture seule
        if (READONLY_KEYS.contains(key)) {
            log.warn("[{}] ChangeConfiguration rejected: {} is readonly", session.getId(), key);
            logToSession(session, String.format("ChangeConfiguration REJECTED - %s is readonly", key));
            return ConfigurationStatus.REJECTED;
        }

        // Vérifier si la clé est supportée
        if (!WRITABLE_KEYS.contains(key)) {
            log.warn("[{}] ChangeConfiguration not supported: {}", session.getId(), key);
            logToSession(session, String.format("ChangeConfiguration NOT_SUPPORTED - unknown key: %s", key));
            return ConfigurationStatus.NOT_SUPPORTED;
        }

        try {
            switch (key) {
                case "HeartbeatInterval" -> {
                    int interval = Integer.parseInt(value);
                    session.setHeartbeatInterval(interval);
                    logToSession(session, String.format("HeartbeatInterval set to %d", interval));
                }
                case "MeterValueSampleInterval" -> {
                    int interval = Integer.parseInt(value);
                    session.setMeterValuesInterval(interval);
                    logToSession(session, String.format("MeterValueSampleInterval set to %d", interval));
                }
                case "ClockAlignedDataInterval" -> {
                    int interval = Integer.parseInt(value);
                    session.setClockAlignedDataInterval(interval);
                    if (interval > 0) {
                        // Démarrer ou redémarrer ClockAlignedData avec le nouvel intervalle
                        ocppService.startClockAlignedData(session.getId(), interval);
                        logToSession(session, String.format("ClockAlignedDataInterval set to %d seconds", interval));
                    } else {
                        // Désactiver ClockAlignedData
                        ocppService.stopClockAlignedData(session.getId());
                        logToSession(session, "ClockAlignedDataInterval disabled (interval=0)");
                    }
                }
                case "ConnectionTimeOut", "MeterValuesSampledData" -> {
                    // Accepter mais pas d'effet réel dans le simulateur
                    logToSession(session, String.format("%s set to %s (simulé)", key, value));
                }
                default -> {
                    return ConfigurationStatus.NOT_SUPPORTED;
                }
            }

            log.info("[{}] ChangeConfiguration: {} = {}", session.getId(), key, value);
            return ConfigurationStatus.ACCEPTED;

        } catch (NumberFormatException e) {
            log.warn("[{}] ChangeConfiguration rejected: invalid value for {}", session.getId(), key);
            logToSession(session, String.format("ChangeConfiguration REJECTED - invalid value for %s", key));
            return ConfigurationStatus.REJECTED;
        }
    }
}
