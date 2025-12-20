package com.evse.simulator.ocpp;

import com.evse.simulator.ocpp.v201.OCPP201Handler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Router OCPP qui délègue les messages au handler approprié selon la version.
 * Pour OCPP 1.6, les messages sont traités par le système existant (OcppHandlerRegistry).
 * Pour OCPP 2.0.1, les messages sont traités par OCPP201Handler.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OCPPRouter {

    private final OCPP201Handler ocpp201Handler;

    /**
     * Route un message OCPP vers le handler approprié.
     * Note: Pour OCPP 1.6, les messages passent par le système existant WebSocketHandler.
     * Cette méthode est utilisée pour les messages OCPP 2.0.1.
     *
     * @param sessionId ID de la session
     * @param version version OCPP
     * @param message message JSON brut
     * @return réponse JSON ou null
     */
    public String routeMessage(String sessionId, OCPPVersion version, String message) {
        log.debug("[OCPPRouter] Routing message for session {} (version {})", sessionId, version);

        return switch (version) {
            case OCPP_1_6 -> {
                // OCPP 1.6 is handled by the existing WebSocket handler system
                log.debug("[OCPPRouter] OCPP 1.6 messages are handled by existing handler system");
                yield null;
            }
            case OCPP_2_0, OCPP_2_0_1 -> ocpp201Handler.handleMessage(sessionId, message);
        };
    }

    /**
     * Route un message vers le handler approprié en détectant la version depuis la session.
     *
     * @param sessionId ID de la session
     * @param ocppVersionString version OCPP en string (ex: "1.6", "2.0.1")
     * @param message message JSON brut
     * @return réponse JSON ou null
     */
    public String routeMessage(String sessionId, String ocppVersionString, String message) {
        OCPPVersion version = OCPPVersion.fromString(ocppVersionString);
        return routeMessage(sessionId, version, message);
    }

    /**
     * Construit un message BootNotification pour la version spécifiée.
     */
    public String buildBootNotification(OCPPVersion version, String vendor, String model, String serial) {
        return switch (version) {
            case OCPP_1_6 -> buildOcpp16BootNotification(vendor, model, serial);
            case OCPP_2_0, OCPP_2_0_1 -> ocpp201Handler.buildBootNotificationRequest(vendor, model, serial);
        };
    }

    /**
     * Construit un message Heartbeat pour la version spécifiée.
     */
    public String buildHeartbeat(OCPPVersion version) {
        return switch (version) {
            case OCPP_1_6 -> buildOcpp16Heartbeat();
            case OCPP_2_0, OCPP_2_0_1 -> ocpp201Handler.buildHeartbeatRequest();
        };
    }

    /**
     * Construit un message StatusNotification pour la version spécifiée.
     */
    public String buildStatusNotification(OCPPVersion version, int connectorId, String status, String errorCode) {
        return switch (version) {
            case OCPP_1_6 -> buildOcpp16StatusNotification(connectorId, status, errorCode);
            case OCPP_2_0, OCPP_2_0_1 -> ocpp201Handler.buildStatusNotificationRequest(1, connectorId, status);
        };
    }

    /**
     * Construit un message Authorize pour la version spécifiée.
     */
    public String buildAuthorize(OCPPVersion version, String idTag) {
        return switch (version) {
            case OCPP_1_6 -> buildOcpp16Authorize(idTag);
            case OCPP_2_0, OCPP_2_0_1 -> ocpp201Handler.buildAuthorizeRequest(idTag, "ISO14443");
        };
    }

    /**
     * Construit un message StartTransaction / TransactionEvent pour la version spécifiée.
     */
    public String buildStartTransaction(OCPPVersion version, int connectorId, String idTag, int meterStart) {
        return switch (version) {
            case OCPP_1_6 -> buildOcpp16StartTransaction(connectorId, idTag, meterStart);
            case OCPP_2_0, OCPP_2_0_1 -> ocpp201Handler.buildTransactionEventRequest(
                    "Started", "Authorized", 0, UUID.randomUUID().toString(), 1);
        };
    }

    // =========================================================================
    // OCPP 1.6 Message Builders
    // =========================================================================

    private String buildOcpp16BootNotification(String vendor, String model, String serial) {
        String messageId = UUID.randomUUID().toString();
        return String.format(
            "[2,\"%s\",\"BootNotification\",{\"chargePointVendor\":\"%s\",\"chargePointModel\":\"%s\",\"chargePointSerialNumber\":\"%s\"}]",
            messageId, vendor, model, serial != null ? serial : ""
        );
    }

    private String buildOcpp16Heartbeat() {
        String messageId = UUID.randomUUID().toString();
        return String.format("[2,\"%s\",\"Heartbeat\",{}]", messageId);
    }

    private String buildOcpp16StatusNotification(int connectorId, String status, String errorCode) {
        String messageId = UUID.randomUUID().toString();
        return String.format(
            "[2,\"%s\",\"StatusNotification\",{\"connectorId\":%d,\"errorCode\":\"%s\",\"status\":\"%s\",\"timestamp\":\"%s\"}]",
            messageId, connectorId, errorCode != null ? errorCode : "NoError", status, Instant.now().toString()
        );
    }

    private String buildOcpp16Authorize(String idTag) {
        String messageId = UUID.randomUUID().toString();
        return String.format(
            "[2,\"%s\",\"Authorize\",{\"idTag\":\"%s\"}]",
            messageId, idTag
        );
    }

    private String buildOcpp16StartTransaction(int connectorId, String idTag, int meterStart) {
        String messageId = UUID.randomUUID().toString();
        return String.format(
            "[2,\"%s\",\"StartTransaction\",{\"connectorId\":%d,\"idTag\":\"%s\",\"meterStart\":%d,\"timestamp\":\"%s\"}]",
            messageId, connectorId, idTag, meterStart, Instant.now().toString()
        );
    }
}
