package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.enums.OCPPAction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message Authorize.
 * Vérifie si un idTag est autorisé à démarrer une charge.
 */
@Component
public class AuthorizeHandler extends AbstractOcppHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.AUTHORIZE;
    }

    @Override
    public Map<String, Object> buildPayload(OcppMessageContext context) {
        String idTag = context.getIdTag();
        if (idTag == null || idTag.isEmpty()) {
            idTag = "DEFAULT";
        }
        return createPayload("idTag", idTag);
    }

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> idTagInfo = (Map<String, Object>) response.get("idTagInfo");

        if (idTagInfo != null) {
            String status = (String) idTagInfo.get("status");
            log.info("[{}] Authorize response: status={}", sessionId, status);

            if ("Accepted".equals(status)) {
                log.info("[{}] IdTag autorisé", sessionId);
            } else {
                log.warn("[{}] IdTag non autorisé: {}", sessionId, status);
            }
        }

        return CompletableFuture.completedFuture(response);
    }

    @Override
    public boolean validateContext(OcppMessageContext context) {
        return super.validateContext(context) &&
               context.getIdTag() != null &&
               !context.getIdTag().isEmpty();
    }
}
