package com.evse.simulator.dto.response.smartcharging;

/**
 * Informations simplifiées sur un profil de charge actif.
 */
public record ChargingProfileInfo(
        int chargingProfileId,
        double limitKw,
        int numberPhases,
        String purpose,
        boolean active
) {}
