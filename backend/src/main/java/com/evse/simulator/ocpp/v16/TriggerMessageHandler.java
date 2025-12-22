package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ConnectorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler pour TriggerMessage (OCPP 1.6).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TriggerMessageHandler implements Ocpp16IncomingHandler {

    private final com.evse.simulator.domain.service.OCPPService ocppService;

    private static final Set<String> SUPPORTED_MESSAGES = Set.of(
            "BootNotification",
            "Heartbeat",
            "MeterValues",
            "StatusNotification",
            "DiagnosticsStatusNotification",
            "FirmwareStatusNotification"
    );

    @Override
    public String getAction() {
        return "TriggerMessage";
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        String requestedMessage = (String) payload.get("requestedMessage");
        Integer connectorId = payload.get("connectorId") != null ?
                ((Number) payload.get("connectorId")).intValue() : null;

        log.info("[TriggerMessage] Session={}, message={}, connectorId={}",
                session.getId(), requestedMessage, connectorId);

        if (requestedMessage == null || !SUPPORTED_MESSAGES.contains(requestedMessage)) {
            response.put("status", "NotImplemented");
            return response;
        }

        response.put("status", "Accepted");
        session.addLog(LogEntry.info("TriggerMessage", "Déclenchement de " + requestedMessage));

        // Envoyer le message en asynchrone
        new Thread(() -> {
            try {
                switch (requestedMessage) {
                    case "BootNotification" -> ocppService.sendBootNotification(session.getId());
                    case "Heartbeat" -> ocppService.sendHeartbeat(session.getId());
                    case "MeterValues" -> ocppService.sendMeterValues(session.getId());
                    case "StatusNotification" -> {
                        ConnectorStatus status = ConnectorStatus.fromSessionState(session.getState());
                        ocppService.sendStatusNotification(session.getId(), status);
                    }
                    case "DiagnosticsStatusNotification" -> {
                        // Simuler un statut idle
                        log.info("[TriggerMessage] DiagnosticsStatusNotification triggered (no-op)");
                    }
                    case "FirmwareStatusNotification" -> {
                        // Simuler un statut idle
                        log.info("[TriggerMessage] FirmwareStatusNotification triggered (no-op)");
                    }
                }
            } catch (Exception e) {
                log.error("[TriggerMessage] Error triggering {}", requestedMessage, e);
            }
        }).start();

        return response;
    }
}
