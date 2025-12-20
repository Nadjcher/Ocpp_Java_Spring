package com.evse.simulator.dto.response;

import com.evse.simulator.model.ChartPoint;
import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.OCPPMessage;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.SessionState;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Réponse contenant les détails d'une session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionResponse {

    private String id;
    private String title;
    private String url;
    private String cpId;
    private SessionState state;
    private ChargerType chargerType;
    private int connectorId;
    private String idTag;
    private String transactionId;

    // État de charge
    private double soc;
    private double targetSoc;
    private double currentPowerKw;
    private double maxPowerKw;
    private double energyDeliveredKwh;
    private double voltage;
    private double currentA;

    // État de connexion
    private boolean connected;
    private boolean authorized;
    private boolean charging;
    private boolean heartbeatActive;
    private boolean meterValuesActive;

    // Configuration
    private int heartbeatInterval;
    private int meterValuesInterval;

    // Infos borne
    private String vendor;
    private String model;
    private String serialNumber;
    private String firmwareVersion;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startTime;
    private LocalDateTime stopTime;
    private LocalDateTime lastConnected;
    private LocalDateTime lastHeartbeat;

    // Données de graphique (optionnel - inclus uniquement si demandé)
    private List<ChartPoint> socData;
    private List<ChartPoint> powerData;

    // Messages OCPP (optionnel - inclus uniquement si demandé)
    private List<OCPPMessage> ocppMessages;

    // Logs (optionnel - inclus uniquement si demandé)
    private List<LogEntry> logs;

    /**
     * Convertit un modèle Session en SessionResponse.
     */
    public static SessionResponse fromSession(Session session) {
        return fromSession(session, false, false, false);
    }

    /**
     * Convertit un modèle Session en SessionResponse avec options.
     */
    public static SessionResponse fromSession(Session session,
                                               boolean includeChartData,
                                               boolean includeOcppMessages,
                                               boolean includeLogs) {
        SessionResponseBuilder builder = SessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .url(session.getUrl())
                .cpId(session.getCpId())
                .state(session.getState())
                .chargerType(session.getChargerType())
                .connectorId(session.getConnectorId())
                .idTag(session.getIdTag())
                .transactionId(session.getTransactionId())
                .soc(session.getSoc())
                .targetSoc(session.getTargetSoc())
                .currentPowerKw(session.getCurrentPowerKw())
                .maxPowerKw(session.getMaxPowerKw())
                .energyDeliveredKwh(session.getEnergyDeliveredKwh())
                .voltage(session.getVoltage())
                .currentA(session.getCurrentA())
                .connected(session.isConnected())
                .authorized(session.isAuthorized())
                .charging(session.isCharging())
                .heartbeatActive(session.isHeartbeatActive())
                .meterValuesActive(session.isMeterValuesActive())
                .heartbeatInterval(session.getHeartbeatInterval())
                .meterValuesInterval(session.getMeterValuesInterval())
                .vendor(session.getVendor())
                .model(session.getModel())
                .serialNumber(session.getSerialNumber())
                .firmwareVersion(session.getFirmwareVersion())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .startTime(session.getStartTime())
                .stopTime(session.getStopTime())
                .lastConnected(session.getLastConnected())
                .lastHeartbeat(session.getLastHeartbeat());

        if (includeChartData) {
            builder.socData(session.getSocData())
                   .powerData(session.getPowerData());
        }

        if (includeOcppMessages) {
            builder.ocppMessages(session.getOcppMessages());
        }

        if (includeLogs) {
            builder.logs(session.getLogs());
        }

        return builder.build();
    }
}
