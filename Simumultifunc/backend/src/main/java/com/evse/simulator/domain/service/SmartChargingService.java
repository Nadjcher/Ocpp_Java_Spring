package com.evse.simulator.domain.service;

import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.ChargingProfile.ChargingProfilePurpose;
import com.evse.simulator.model.ChargingProfile.ChargingRateUnit;
import com.evse.simulator.model.ChargingProfile.ChargingSchedule;

import java.util.List;

/**
 * Interface de gestion du Smart Charging (OCPP 1.6).
 */
public interface SmartChargingService {

    // Profile Management
    String setChargingProfile(String sessionId, ChargingProfile profile);

    /**
     * Applique un profil de charge avec connectorId explicite.
     */
    String setChargingProfile(int connectorId, String sessionId, ChargingProfile profile);

    String clearChargingProfile(String sessionId, Integer chargingProfileId,
                               Integer stackLevel, ChargingProfilePurpose purpose);

    // Composite Schedule
    ChargingSchedule getCompositeSchedule(String sessionId, int duration, ChargingRateUnit chargingRateUnit);

    // Queries
    List<ChargingProfile> getActiveProfiles(String sessionId);
    ChargingProfile getChargePointMaxProfile();
    double getCurrentLimit(String sessionId);
}