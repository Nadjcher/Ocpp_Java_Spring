package com.evse.simulator.document;

import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.SessionState;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Document MongoDB pour les sessions de charge EVSE.
 * <p>
 * Stocke toutes les informations d'une session incluant les logs,
 * messages OCPP et donnees de graphique en tant que documents imbriques.
 * </p>
 */
@Document(collection = "sessions")
@CompoundIndex(name = "cpId_state_idx", def = "{'cpId': 1, 'state': 1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionDocument {

    @Id
    private String id;

    private String title;

    @Indexed
    private String cpId;

    private String url;
    private String wsUrl;
    private String bearerToken;

    // =========================================================================
    // Connection state
    // =========================================================================

    @Indexed
    @Builder.Default
    private SessionState state = SessionState.DISCONNECTED;

    @Indexed
    @Builder.Default
    private boolean connected = false;

    @Builder.Default
    private boolean authorized = false;

    @Builder.Default
    private boolean charging = false;

    @Builder.Default
    private boolean parked = false;

    @Builder.Default
    private boolean plugged = false;

    // =========================================================================
    // Charging metrics
    // =========================================================================

    @Builder.Default
    private double soc = 0;

    @Builder.Default
    private double targetSoc = 80;

    @Builder.Default
    private double currentPowerKw = 0;

    @Builder.Default
    private double maxPowerKw = 22;

    @Builder.Default
    private double energyDeliveredKwh = 0;

    @Builder.Default
    private long meterValue = 0;

    // =========================================================================
    // Electrical parameters
    // =========================================================================

    @Builder.Default
    private double voltage = 230;

    @Builder.Default
    private double currentA = 0;

    @Builder.Default
    private double maxCurrentA = 32;

    @Builder.Default
    private double temperature = 25;

    // =========================================================================
    // Charger configuration
    // =========================================================================

    @Builder.Default
    private ChargerType chargerType = ChargerType.AC_TRI;

    @Builder.Default
    private int connectorId = 1;

    private String phaseType;

    // =========================================================================
    // Smart Charging Profile (SCP)
    // =========================================================================

    private Double scpLimitKw;
    private Double scpLimitA;
    private Integer scpProfileId;
    private String scpPurpose;
    private Integer scpStackLevel;
    private Integer scpNextPeriodSeconds;
    private Double scpNextLimitKw;

    // =========================================================================
    // Transaction
    // =========================================================================

    private String idTag;
    private String transactionId;
    private Integer txId;
    private Integer reservationId;

    // =========================================================================
    // Vehicle reference
    // =========================================================================

    private String vehicleProfile;

    // =========================================================================
    // OCPP configuration
    // =========================================================================

    @Builder.Default
    private int heartbeatInterval = 30;

    @Builder.Default
    private int meterValuesInterval = 10;

    @Builder.Default
    private String vendor = "EVSE Simulator";

    @Builder.Default
    private String model = "SimuCP-1";

    private String serialNumber;

    @Builder.Default
    private String firmwareVersion = "1.0.0";

    @Builder.Default
    private String ocppVersion = "1.6";

    private String bootStatus;

    // =========================================================================
    // Active charging profile
    // =========================================================================

    private ChargingProfile activeChargingProfile;

    // =========================================================================
    // Timestamps
    // =========================================================================

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Indexed
    private LocalDateTime updatedAt;

    private LocalDateTime startTime;
    private LocalDateTime stopTime;
    private LocalDateTime lastStateChange;
    private LocalDateTime lastKeepalive;
    private LocalDateTime lastConnected;
    private LocalDateTime lastHeartbeat;

    // =========================================================================
    // Status tracking
    // =========================================================================

    private String status;

    @Builder.Default
    private int reconnectAttempts = 0;

    @Builder.Default
    private boolean voluntaryStop = false;

    private String disconnectReason;

    @Builder.Default
    private boolean backgrounded = false;

    // =========================================================================
    // Embedded collections (limited to 500 entries)
    // =========================================================================

    @Builder.Default
    private List<LogEntryEmbedded> logs = new ArrayList<>();

    @Builder.Default
    private List<ChartPointEmbedded> socData = new ArrayList<>();

    @Builder.Default
    private List<ChartPointEmbedded> powerData = new ArrayList<>();

    @Builder.Default
    private List<OcppMessageEmbedded> ocppMessages = new ArrayList<>();

    // =========================================================================
    // Embedded documents
    // =========================================================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LogEntryEmbedded {
        private LocalDateTime timestamp;
        private String level;
        private String category;
        private String message;
        private Object data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChartPointEmbedded {
        private LocalDateTime timestamp;
        private double value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OcppMessageEmbedded {
        private LocalDateTime timestamp;
        private String direction;
        private String action;
        private String messageId;
        private Object payload;
        private Long processingTimeMs;
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    public void addLog(LogEntryEmbedded log) {
        if (logs == null) logs = new ArrayList<>();
        logs.add(log);
        if (logs.size() > 500) {
            logs = new ArrayList<>(logs.subList(logs.size() - 500, logs.size()));
        }
    }

    public void addSocDataPoint(ChartPointEmbedded point) {
        if (socData == null) socData = new ArrayList<>();
        socData.add(point);
        if (socData.size() > 500) {
            socData = new ArrayList<>(socData.subList(socData.size() - 500, socData.size()));
        }
    }

    public void addPowerDataPoint(ChartPointEmbedded point) {
        if (powerData == null) powerData = new ArrayList<>();
        powerData.add(point);
        if (powerData.size() > 500) {
            powerData = new ArrayList<>(powerData.subList(powerData.size() - 500, powerData.size()));
        }
    }

    public void addOcppMessage(OcppMessageEmbedded message) {
        if (ocppMessages == null) ocppMessages = new ArrayList<>();
        ocppMessages.add(message);
        if (ocppMessages.size() > 500) {
            ocppMessages = new ArrayList<>(ocppMessages.subList(ocppMessages.size() - 500, ocppMessages.size()));
        }
    }
}
