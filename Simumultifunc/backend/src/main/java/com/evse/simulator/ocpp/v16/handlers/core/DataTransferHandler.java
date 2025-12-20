package com.evse.simulator.ocpp.v16.handlers.core;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.Ocpp16Exception;
import com.evse.simulator.ocpp.v16.model.types.DataTransferStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler pour DataTransfer (CS → CP).
 * <p>
 * Gère les transferts de données propriétaires.
 * </p>
 */
@Slf4j
@Component
public class DataTransferHandler extends AbstractOcpp16IncomingHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.DATA_TRANSFER;
    }

    @Override
    public void validate(Map<String, Object> payload) throws Ocpp16Exception {
        super.validate(payload);
        requireField(payload, "vendorId");
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        String vendorId = getString(payload, "vendorId", true);
        String messageId = getString(payload, "messageId", false);
        String data = getString(payload, "data", false);

        Map<String, Object> response = new HashMap<>();

        // Vérifier si le vendorId est connu
        if (isKnownVendor(vendorId)) {
            // Traiter le message
            String responseData = processDataTransfer(session, vendorId, messageId, data);

            response.put("status", DataTransferStatus.ACCEPTED.getValue());
            if (responseData != null) {
                response.put("data", responseData);
            }

            logToSession(session, String.format(
                "DataTransfer ACCEPTED - vendor: %s, messageId: %s",
                vendorId, messageId));
        } else {
            response.put("status", DataTransferStatus.UNKNOWN_VENDOR_ID.getValue());
            logToSession(session, String.format(
                "DataTransfer UNKNOWN_VENDOR_ID - vendor: %s", vendorId));
        }

        logExit(session, response);
        return response;
    }

    private boolean isKnownVendor(String vendorId) {
        // Accepter tous les vendorIds pour le simulateur
        // Ou définir une liste de vendorIds connus
        return vendorId != null && !vendorId.isEmpty();
    }

    private String processDataTransfer(Session session, String vendorId,
                                       String messageId, String data) {
        log.info("[{}] DataTransfer: vendor={}, messageId={}, data={}",
                session.getId(), vendorId, messageId, data);

        // Retourner une réponse vide par défaut
        return "{}";
    }
}
