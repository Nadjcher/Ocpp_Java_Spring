package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.enums.OCPPAction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message StopTransaction.
 * Arrête une transaction de charge.
 */
@Component
public class StopTransactionHandler extends AbstractOcppHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.STOP_TRANSACTION;
    }

    @Override
    public Map<String, Object> buildPayload(OcppMessageContext context) {
        Integer transactionId = context.getTransactionId();
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID requis pour StopTransaction");
        }

        long meterStop = context.getMeterValue() != null ? context.getMeterValue() : 0L;
        String reason = context.getStopReason() != null ? context.getStopReason() : "Local";

        Map<String, Object> payload = createPayload(
            "transactionId", transactionId,
            "meterStop", (int) meterStop,
            "timestamp", formatTimestamp(),
            "reason", reason
        );

        // Ajouter idTag si présent
        if (context.getIdTag() != null) {
            payload.put("idTag", context.getIdTag());
        }

        return payload;
    }

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> idTagInfo = (Map<String, Object>) response.get("idTagInfo");

        String status = "Unknown";
        if (idTagInfo != null) {
            status = (String) idTagInfo.get("status");
        }

        log.info("[{}] StopTransaction response: status={}", sessionId, status);

        return CompletableFuture.completedFuture(response);
    }

    @Override
    public boolean validateContext(OcppMessageContext context) {
        return super.validateContext(context) &&
               context.getTransactionId() != null;
    }
}
