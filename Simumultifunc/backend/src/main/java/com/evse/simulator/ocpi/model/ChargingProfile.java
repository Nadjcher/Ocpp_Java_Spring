package com.evse.simulator.ocpi.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * OCPI ChargingProfile object.
 * Used for smart charging control.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingProfile {

    private Instant startDateTime;
    private Integer duration;
    private ChargingRateUnit chargingRateUnit;
    private BigDecimal minChargingRate;
    private List<ChargingProfilePeriod> chargingProfilePeriod;

    /**
     * Charging rate unit.
     */
    public enum ChargingRateUnit {
        W,  // Watts
        A   // Amperes
    }

    /**
     * Period within a charging profile.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargingProfilePeriod {
        private int startPeriod;       // Seconds from start
        private BigDecimal limit;      // Power or current limit
    }

    /**
     * Active charging profile response.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveChargingProfile {
        private Instant startDateTime;
        private ChargingProfile chargingProfile;
    }

    /**
     * Charging profile result for async response.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargingProfileResult {
        private ChargingProfileResultType result;
        private ChargingProfile profile;
    }

    /**
     * Result types for charging profile operations.
     */
    public enum ChargingProfileResultType {
        ACCEPTED, REJECTED, UNKNOWN
    }

    /**
     * Clear profile result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClearProfileResult {
        private ClearProfileResultType result;
    }

    public enum ClearProfileResultType {
        ACCEPTED, UNKNOWN
    }
}
