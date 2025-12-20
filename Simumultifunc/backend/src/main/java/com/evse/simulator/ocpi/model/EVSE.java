package com.evse.simulator.ocpi.model;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EVSE {
    private String uid;
    private String evseId;
    private EVSEStatus status;
    private List<Connector> connectors;
    private Instant lastUpdated;

    public enum EVSEStatus {
        AVAILABLE, BLOCKED, CHARGING, INOPERATIVE, OUTOFORDER, PLANNED, REMOVED, RESERVED, UNKNOWN
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Connector {
        private String id;
        private ConnectorType standard;
        private ConnectorFormat format;
        private PowerType powerType;
        private int maxVoltage;
        private int maxAmperage;
        private Integer maxElectricPower;
        private Instant lastUpdated;
    }

    public enum ConnectorType {
        CHADEMO, IEC_62196_T1, IEC_62196_T1_COMBO, IEC_62196_T2, IEC_62196_T2_COMBO, IEC_62196_T3A, IEC_62196_T3C, TESLA_R, TESLA_S
    }

    public enum ConnectorFormat {
        SOCKET, CABLE
    }

    public enum PowerType {
        AC_1_PHASE, AC_2_PHASE, AC_3_PHASE, DC
    }
}
