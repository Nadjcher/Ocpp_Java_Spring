package com.evse.simulator.ocpp.v16.handlers.trigger;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ConnectorStatus;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.MessageTrigger;
import com.evse.simulator.ocpp.v16.model.types.TriggerMessageStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour TriggerMessage (CS → CP).
 */
@Slf4j
@Component
public class TriggerMessageHandler extends AbstractOcpp16IncomingHandler {

    private final OCPPService ocppService;

    public TriggerMessageHandler(@Lazy OCPPService ocppService) {
        this.ocppService = ocppService;
    }

    @Override
    public OCPPAction getAction() {
        return OCPPAction.TRIGGER_MESSAGE;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "requestedMessage");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        String requestedMessageStr = getString(payload, "requestedMessage", true);
        Integer connectorId = getInteger(payload, "connectorId", false);

        MessageTrigger requestedMessage = MessageTrigger.fromValue(requestedMessageStr);
        TriggerMessageStatus status;

        if (requestedMessage == null) {
            status = TriggerMessageStatus.NOT_IMPLEMENTED;
            logToSession(session, String.format(
                "TriggerMessage NOT_IMPLEMENTED - unknown message: %s", requestedMessageStr));
        } else {
            status = triggerMessage(session, requestedMessage, connectorId);
            logToSession(session, String.format(
                "TriggerMessage %s - message: %s", status.getValue(), requestedMessageStr));
        }

        Map<String, Object> response = createResponse(status);
        logExit(session, response);
        return response;
    }

    private TriggerMessageStatus triggerMessage(Session session, MessageTrigger message,
                                                 Integer connectorId) {
        CompletableFuture.runAsync(() -> {
            try {
                // Petit délai pour permettre l'envoi de la réponse
                Thread.sleep(100);

                switch (message) {
                    case BOOT_NOTIFICATION -> {
                        log.info("[{}] TriggerMessage: Sending BootNotification", session.getId());
                        ocppService.sendBootNotification(session.getId());
                    }
                    case HEARTBEAT -> {
                        log.info("[{}] TriggerMessage: Sending Heartbeat", session.getId());
                        ocppService.sendHeartbeat(session.getId());
                    }
                    case METER_VALUES -> {
                        log.info("[{}] TriggerMessage: Sending MeterValues", session.getId());
                        ocppService.sendMeterValues(session.getId());
                    }
                    case STATUS_NOTIFICATION -> {
                        log.info("[{}] TriggerMessage: Sending StatusNotification", session.getId());
                        ConnectorStatus connStatus = ConnectorStatus.fromSessionState(session.getState());
                        ocppService.sendStatusNotification(session.getId(), connStatus);
                    }
                    case DIAGNOSTICS_STATUS_NOTIFICATION, FIRMWARE_STATUS_NOTIFICATION -> {
                        // Ces messages nécessitent un état de diagnostic/firmware actif
                        // Pour le simulateur, on ne les envoie pas
                        log.info("[{}] TriggerMessage: {} not applicable", session.getId(), message);
                    }
                }
            } catch (Exception e) {
                log.error("[{}] TriggerMessage error: {}", session.getId(), e.getMessage());
            }
        });

        return TriggerMessageStatus.ACCEPTED;
    }
}
