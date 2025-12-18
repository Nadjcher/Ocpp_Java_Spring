package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message BootNotification.
 * Envoyé lors de la connexion pour enregistrer la borne auprès du CSMS.
 */
@Component
public class BootNotificationHandler extends AbstractOcppHandler {

    private static final String DEFAULT_VENDOR = "EVSE Simulator";
    private static final String DEFAULT_MODEL = "Virtual CP";
    private static final String DEFAULT_SERIAL = "SIM-001";
    private static final String DEFAULT_FIRMWARE = "1.0.0";

    @Override
    public OCPPAction getAction() {
        return OCPPAction.BOOT_NOTIFICATION;
    }

    @Override
    public Map<String, Object> buildPayload(OcppMessageContext context) {
        Session session = context.getSession();

        String vendor = DEFAULT_VENDOR;
        String model = DEFAULT_MODEL;
        String serial = DEFAULT_SERIAL;
        String firmware = DEFAULT_FIRMWARE;

        if (session != null) {
            if (session.getVendor() != null) vendor = session.getVendor();
            if (session.getModel() != null) model = session.getModel();
            if (session.getSerialNumber() != null) serial = session.getSerialNumber();
            if (session.getFirmwareVersion() != null) firmware = session.getFirmwareVersion();
        }

        return createPayload(
            "chargePointVendor", vendor,
            "chargePointModel", model,
            "chargePointSerialNumber", serial,
            "firmwareVersion", firmware,
            "meterType", "Virtual",
            "meterSerialNumber", serial + "-M"
        );
    }

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        String status = (String) response.get("status");
        Integer interval = (Integer) response.get("interval");

        log.info("[{}] BootNotification response: status={}, interval={}s",
                sessionId, status, interval);

        if ("Accepted".equals(status)) {
            log.info("[{}] Borne acceptée par le CSMS", sessionId);
        } else if ("Pending".equals(status)) {
            log.warn("[{}] Borne en attente d'acceptation", sessionId);
        } else {
            log.error("[{}] Borne rejetée par le CSMS", sessionId);
        }

        return CompletableFuture.completedFuture(response);
    }
}
