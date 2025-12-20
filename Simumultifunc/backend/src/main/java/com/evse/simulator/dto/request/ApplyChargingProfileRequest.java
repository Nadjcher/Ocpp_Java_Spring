package com.evse.simulator.dto.request;

import java.util.List;

/**
 * Request pour appliquer un profil de charge OCPP.
 */
public record ApplyChargingProfileRequest(
        String sessionId,
        Integer connectorId,
        Integer chargingProfileId,
        Integer stackLevel,
        String chargingProfilePurpose,
        String chargingProfileKind,
        String recurrencyKind,
        String validFrom,
        String validTo,
        ChargingScheduleRequest chargingSchedule
) {
    public record ChargingScheduleRequest(
            Integer duration,
            String startSchedule,
            String chargingRateUnit,
            Double minChargingRate,
            List<ChargingSchedulePeriodRequest> chargingSchedulePeriod
    ) {}

    public record ChargingSchedulePeriodRequest(
            Integer startPeriod,
            Double limit,
            Integer numberPhases
    ) {}
}
