package com.evse.simulator.dto.response;

import com.evse.simulator.dto.response.smartcharging.ChargingProfileInfo;

import java.util.List;

/**
 * Response avec les informations de profils de charge.
 */
public record ChargingProfileInfoResponse(
        String sessionId,
        List<ChargingProfileInfo> profiles,
        double currentLimitKw
) {}
