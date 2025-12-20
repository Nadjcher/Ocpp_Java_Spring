package com.evse.simulator.ocpi.model;

import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * OCPI Connector object.
 * Represents a connector within an EVSE.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Connector {

    private String id;
    private ConnectorType standard;
    private ConnectorFormat format;
    private PowerType powerType;
    private int maxVoltage;
    private int maxAmperage;
    private Integer maxElectricPower;
    private List<String> tariffIds;
    private String termsAndConditions;
    private Instant lastUpdated;

    /**
     * Connector types according to IEC 62196.
     */
    public enum ConnectorType {
        CHADEMO,
        DOMESTIC_A, DOMESTIC_B, DOMESTIC_C, DOMESTIC_D, DOMESTIC_E, DOMESTIC_F,
        DOMESTIC_G, DOMESTIC_H, DOMESTIC_I, DOMESTIC_J, DOMESTIC_K, DOMESTIC_L,
        IEC_60309_2_single_16, IEC_60309_2_three_16, IEC_60309_2_three_32, IEC_60309_2_three_64,
        IEC_62196_T1, IEC_62196_T1_COMBO, IEC_62196_T2, IEC_62196_T2_COMBO, IEC_62196_T3A, IEC_62196_T3C,
        PANTOGRAPH_BOTTOM_UP, PANTOGRAPH_TOP_DOWN,
        TESLA_R, TESLA_S,
        GBT_AC, GBT_DC, NEMA_5_20, NEMA_6_30, NEMA_6_50, NEMA_10_30, NEMA_10_50, NEMA_14_30, NEMA_14_50
    }

    /**
     * Connector format (socket or cable).
     */
    public enum ConnectorFormat {
        SOCKET, CABLE
    }

    /**
     * Power type.
     */
    public enum PowerType {
        AC_1_PHASE, AC_3_PHASE, DC
    }

    // Java import needed
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TariffElement {
        private String tariffId;
    }
}
