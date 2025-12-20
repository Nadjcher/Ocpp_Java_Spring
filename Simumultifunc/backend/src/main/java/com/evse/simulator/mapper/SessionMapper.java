package com.evse.simulator.mapper;

import com.evse.simulator.dto.response.session.SessionDetail;
import com.evse.simulator.dto.response.session.SessionSummary;
import com.evse.simulator.dto.response.smartcharging.ChargingProfileInfo;
import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.Session;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Mapper pour convertir les entites Session en DTOs.
 */
@Component
public class SessionMapper {

    /**
     * Convertit une Session en resume (pour liste).
     */
    public SessionSummary toSummary(Session s) {
        return new SessionSummary(
                s.getId(),
                s.getCpId(),
                s.getConnectorId(),
                resolveStatus(s),
                (int) s.getSoc(),
                s.getCurrentPowerKw(),
                s.getEnergyDeliveredKwh(),
                s.isConnected()
        );
    }

    /**
     * Convertit une Session en details complets.
     */
    public SessionDetail toDetail(Session s) {
        return new SessionDetail(
                s.getId(),
                s.getCpId(),
                s.getConnectorId(),
                resolveStatus(s),
                (int) s.getSoc(),
                (int) s.getTargetSoc(),
                s.getEnergyDeliveredKwh(),
                s.getCurrentPowerKw(),
                s.getMaxPowerKw(),
                calculateDuration(s),
                s.getVehicleProfile(),
                s.getChargerType() != null ? s.getChargerType().name() : null,
                s.getIdTag(),
                s.getTransactionId(),
                s.isConnected(),
                s.isAuthorized(),
                s.isCharging(),
                mapProfile(s.getActiveChargingProfile())
        );
    }

    /**
     * Determine le statut simplifie de la session.
     */
    private String resolveStatus(Session s) {
        if (s.getState() != null) {
            return s.getState().name();
        }
        if (s.isCharging()) return "CHARGING";
        if (s.isAuthorized()) return "AUTHORIZED";
        if (s.isConnected()) return "AVAILABLE";
        return "DISCONNECTED";
    }

    /**
     * Calcule la duree de charge en secondes.
     */
    private long calculateDuration(Session s) {
        if (s.getStartTime() == null) return 0;
        LocalDateTime end = s.getStopTime() != null ? s.getStopTime() : LocalDateTime.now();
        return Duration.between(s.getStartTime(), end).getSeconds();
    }

    /**
     * Convertit un ChargingProfile en info simplifiee.
     */
    private ChargingProfileInfo mapProfile(ChargingProfile p) {
        if (p == null) return null;

        double limit = 0;
        if (p.getChargingSchedule() != null &&
                p.getChargingSchedule().getChargingSchedulePeriod() != null &&
                !p.getChargingSchedule().getChargingSchedulePeriod().isEmpty()) {
            limit = p.getChargingSchedule().getChargingSchedulePeriod().get(0).getLimit();
        }

        return new ChargingProfileInfo(
                p.getChargingProfileId(),
                limit,
                3, // phases par defaut
                p.getChargingProfilePurpose() != null ? p.getChargingProfilePurpose().name() : "Unknown",
                true
        );
    }
}
