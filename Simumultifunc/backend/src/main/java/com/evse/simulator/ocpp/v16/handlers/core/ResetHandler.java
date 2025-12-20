package com.evse.simulator.ocpp.v16.handlers.core;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.ResetStatus;
import com.evse.simulator.ocpp.v16.model.types.ResetType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour Reset (CS → CP).
 */
@Slf4j
@Component
public class ResetHandler extends AbstractOcpp16IncomingHandler {

    private final OCPPService ocppService;

    public ResetHandler(@Lazy OCPPService ocppService) {
        this.ocppService = ocppService;
    }

    @Override
    public OCPPAction getAction() {
        return OCPPAction.RESET;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "type");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        String typeStr = getString(payload, "type", true);
        ResetType type = ResetType.fromValue(typeStr);

        if (type == null) {
            logToSession(session, "Reset REJECTED - Invalid type: " + typeStr);
            return createResponse(ResetStatus.REJECTED);
        }

        // Toujours accepter le reset
        ResetStatus status = ResetStatus.ACCEPTED;

        logToSession(session, String.format("Reset ACCEPTED - type: %s", type.getValue()));
        log.info("[{}] Reset: type={}", session.getId(), type);

        // Exécuter le reset de manière asynchrone
        executeReset(session, type);

        Map<String, Object> response = createResponse(status);
        logExit(session, response);
        return response;
    }

    private void executeReset(Session session, ResetType type) {
        CompletableFuture.runAsync(() -> {
            try {
                // Attendre un peu pour permettre l'envoi de la réponse
                Thread.sleep(500);

                if (type == ResetType.HARD) {
                    log.info("[{}] Hard Reset: Disconnecting...", session.getId());
                    session.addLog(LogEntry.info("OCPP", "Hard Reset: Disconnecting..."));

                    // Déconnecter
                    ocppService.disconnect(session.getId());

                    // Attendre
                    Thread.sleep(2000);

                    // Reconnecter
                    log.info("[{}] Hard Reset: Reconnecting...", session.getId());
                    session.addLog(LogEntry.info("OCPP", "Hard Reset: Reconnecting..."));
                    session.setState(SessionState.DISCONNECTED);

                    ocppService.connect(session.getId());

                } else {
                    // Soft Reset
                    log.info("[{}] Soft Reset: Re-sending BootNotification...", session.getId());
                    session.addLog(LogEntry.info("OCPP", "Soft Reset: Re-sending BootNotification..."));

                    // Juste renvoyer BootNotification
                    ocppService.sendBootNotification(session.getId());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[{}] Reset interrupted", session.getId());
            } catch (Exception e) {
                log.error("[{}] Reset failed: {}", session.getId(), e.getMessage(), e);
                session.addLog(LogEntry.error("OCPP", "Reset failed: " + e.getMessage()));
            }
        });
    }
}
