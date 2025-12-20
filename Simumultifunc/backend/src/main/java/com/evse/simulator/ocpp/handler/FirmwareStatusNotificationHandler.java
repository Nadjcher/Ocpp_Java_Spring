package com.evse.simulator.ocpp.handler;

import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.model.types.FirmwareStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler pour le message FirmwareStatusNotification (CP → CS).
 * <p>
 * Notifie le CSMS de l'état de la mise à jour du firmware.
 * </p>
 *
 * <h3>Flux OCPP 1.6:</h3>
 * <pre>
 * 1. CS envoie UpdateFirmware.req
 * 2. CP répond avec UpdateFirmware.conf (vide)
 * 3. CP envoie FirmwareStatusNotification pour indiquer la progression:
 *    - Downloading
 *    - Downloaded
 *    - Installing
 *    - Installed
 *    - DownloadFailed
 *    - InstallationFailed
 *    - Idle
 * 4. CS répond avec FirmwareStatusNotification.conf (vide)
 * </pre>
 */
@Component
public class FirmwareStatusNotificationHandler extends AbstractOcppHandler {

    @Override
    public OCPPAction getAction() {
        return OCPPAction.FIRMWARE_STATUS_NOTIFICATION;
    }

    @Override
    public Map<String, Object> buildPayload(OcppMessageContext context) {
        // Récupérer le statut depuis additionalData ou utiliser Idle par défaut
        String statusValue = FirmwareStatus.IDLE.getValue();

        if (context.getAdditionalData() != null) {
            Object statusObj = context.getAdditionalData().get("firmwareStatus");
            if (statusObj instanceof FirmwareStatus fs) {
                statusValue = fs.getValue();
            } else if (statusObj instanceof String s) {
                statusValue = s;
            }
        }

        return createPayload("status", statusValue);
    }

    @Override
    public CompletableFuture<Map<String, Object>> handleResponse(String sessionId, Map<String, Object> response) {
        // FirmwareStatusNotification.conf est vide selon OCPP 1.6
        log.debug("[{}] FirmwareStatusNotification acknowledged", sessionId);
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Construit un payload pour un statut spécifique.
     *
     * @param status le statut firmware
     * @return le payload construit
     */
    public Map<String, Object> buildPayloadForStatus(FirmwareStatus status) {
        return createPayload("status", status.getValue());
    }

    /**
     * Construit un payload pour un statut sous forme de chaîne.
     *
     * @param status le statut sous forme de chaîne
     * @return le payload construit
     */
    public Map<String, Object> buildPayloadForStatus(String status) {
        return createPayload("status", status);
    }
}
