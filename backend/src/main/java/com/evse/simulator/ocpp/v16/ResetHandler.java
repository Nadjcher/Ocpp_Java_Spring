package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler pour Reset (OCPP 1.6).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ResetHandler implements Ocpp16IncomingHandler {

    private final com.evse.simulator.domain.service.OCPPService ocppService;

    @Override
    public String getAction() {
        return "Reset";
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        String type = (String) payload.get("type");

        log.info("[Reset] Session={}, type={}", session.getId(), type);

        if (!"Hard".equals(type) && !"Soft".equals(type)) {
            response.put("status", "Rejected");
            return response;
        }

        response.put("status", "Accepted");
        session.addLog(LogEntry.warn("Reset", type + " reset demandé"));

        // Exécuter le reset en asynchrone
        new Thread(() -> {
            try {
                // Si Hard reset ou si une transaction est en cours, l'arrêter
                if ("Hard".equals(type) && session.getState().hasActiveTransaction()) {
                    ocppService.sendStopTransaction(session.getId()).get();
                }

                // Simuler un délai de reboot
                Thread.sleep("Hard".equals(type) ? 5000 : 2000);

                // Réinitialiser l'état
                session.setState(SessionState.CONNECTED);
                session.setLastStateChange(LocalDateTime.now());
                session.setReservationId(null);
                session.setReservationExpiry(null);
                session.setTransactionId(null);

                // Renvoyer BootNotification
                ocppService.sendBootNotification(session.getId());

                session.addLog(LogEntry.success("Reset", type + " reset terminé"));

            } catch (Exception e) {
                log.error("[Reset] Error during reset", e);
            }
        }).start();

        return response;
    }
}
