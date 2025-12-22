package com.evse.simulator.ocpp.v16;

import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler pour RemoteStopTransaction (OCPP 1.6).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RemoteStopTransactionHandler implements Ocpp16IncomingHandler {

    private final com.evse.simulator.domain.service.OCPPService ocppService;

    @Override
    public String getAction() {
        return "RemoteStopTransaction";
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        try {
            Integer transactionId = payload.get("transactionId") != null ?
                    ((Number) payload.get("transactionId")).intValue() : null;

            log.info("[RemoteStop] Session={}, transactionId={}", session.getId(), transactionId);

            if (transactionId == null) {
                response.put("status", "Rejected");
                session.addLog(LogEntry.warn("RemoteStopTransaction rejected: missing transactionId"));
                return response;
            }

            // Vérifier que la transaction correspond
            String currentTxId = session.getTransactionId();
            if (currentTxId == null || !currentTxId.equals(String.valueOf(transactionId))) {
                response.put("status", "Rejected");
                session.addLog(LogEntry.warn("RemoteStopTransaction rejected: transaction " +
                        transactionId + " introuvable"));
                return response;
            }

            // Vérifier qu'on peut arrêter
            if (!session.getState().canStopTransaction()) {
                response.put("status", "Rejected");
                session.addLog(LogEntry.warn("RemoteStopTransaction rejected: pas de charge active"));
                return response;
            }

            response.put("status", "Accepted");
            session.addLog(LogEntry.success("RemoteStopTransaction accepted pour tx=" + transactionId));

            // Arrêter la transaction en asynchrone
            new Thread(() -> {
                try {
                    ocppService.sendStopTransaction(session.getId());
                } catch (Exception e) {
                    log.error("[RemoteStop] Failed to stop transaction", e);
                }
            }).start();

        } catch (Exception e) {
            log.error("[RemoteStop] Error", e);
            response.put("status", "Rejected");
        }

        return response;
    }
}
