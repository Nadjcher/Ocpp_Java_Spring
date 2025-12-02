package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.enums.OCPPAction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message StartTransaction.
 * Démarre une transaction de charge.
 */
@Component
public class StartTransactionHandler extends AbstractOcppHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.START_TRANSACTION;
    }

    @Override
    public Map<String, Object> buildPayload(OcppMessageContext context) {
        int connectorId = context.getConnectorId() > 0 ? context.getConnectorId() : 1;
        String idTag = context.getIdTag() != null ? context.getIdTag() : "DEFAULT";
        long meterStart = context.getMeterValue() != null ? context.getMeterValue() : 0L;

        return createPayload(
            "connectorId", connectorId,
            "idTag", idTag,
            "meterStart", (int) meterStart,
            "timestamp", formatTimestamp()
        );
    }

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        Integer transactionId = (Integer) response.get("transactionId");
        @SuppressWarnings("unchecked")
        Map<String, Object> idTagInfo = (Map<String, Object>) response.get("idTagInfo");

        String status = "Unknown";
        if (idTagInfo != null) {
            status = (String) idTagInfo.get("status");
        }

        log.info("[{}] StartTransaction response: transactionId={}, status={}",
                sessionId, transactionId, status);

        if ("Accepted".equals(status) && transactionId != null) {
            log.info("[{}] Transaction {} démarrée avec succès", sessionId, transactionId);
        } else {
            log.warn("[{}] Transaction refusée: {}", sessionId, status);
        }

        return CompletableFuture.completedFuture(response);
    }

    @Override
    public boolean validateContext(OcppMessageContext context) {
        return super.validateContext(context) &&
               context.getIdTag() != null;
    }
}
