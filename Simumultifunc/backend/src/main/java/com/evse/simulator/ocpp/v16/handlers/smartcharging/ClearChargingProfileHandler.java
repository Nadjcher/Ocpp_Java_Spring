package com.evse.simulator.ocpp.v16.handlers.smartcharging;

import com.evse.simulator.model.ChargingProfile.ChargingProfilePurpose;
import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.OCPPAction;
import com.evse.simulator.ocpp.v16.AbstractOcpp16IncomingHandler;
import com.evse.simulator.ocpp.v16.model.types.ClearChargingProfileStatus;
import com.evse.simulator.service.SmartChargingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handler pour ClearChargingProfile (CS → CP).
 */
@Slf4j
@Component
public class ClearChargingProfileHandler extends AbstractOcpp16IncomingHandler {

    private final SmartChargingService smartChargingService;

    public ClearChargingProfileHandler(SmartChargingService smartChargingService) {
        this.smartChargingService = smartChargingService;
    }

    @Override
    public OCPPAction getAction() {
        return OCPPAction.CLEAR_CHARGING_PROFILE;
    }

    @Override
    public Map<String, Object> handle(Session session, Map<String, Object> payload) {
        logEntry(session, payload);

        // Tous les paramètres sont optionnels selon OCPP 1.6
        Integer id = getInteger(payload, "id", false);
        Integer connectorId = getInteger(payload, "connectorId", false);
        String chargingProfilePurposeStr = getString(payload, "chargingProfilePurpose", false);
        Integer stackLevel = getInteger(payload, "stackLevel", false);

        // Convertir le purpose
        ChargingProfilePurpose purpose = null;
        if (chargingProfilePurposeStr != null) {
            purpose = ChargingProfilePurpose.fromValue(chargingProfilePurposeStr);
        }

        // Supprimer les profils via SmartChargingService - AVEC connectorId pour filtrage TxDefaultProfile
        String status = smartChargingService.clearChargingProfile(
                session.getId(),
                connectorId,  // Nouveau: passer connectorId pour filtrage correct
                id,
                stackLevel,
                purpose
        );

        ClearChargingProfileStatus responseStatus;
        if ("Accepted".equals(status)) {
            responseStatus = ClearChargingProfileStatus.ACCEPTED;

            // Mettre à jour la session avec la nouvelle limite effective
            double newLimit = smartChargingService.getCurrentLimit(session.getId());
            if (newLimit < session.getMaxPowerKw()) {
                session.setScpLimitKw(newLimit);
                // Recalculer scpLimitA depuis la nouvelle limite kW
                double voltage = session.getVoltage() > 0 ? session.getVoltage() : 230.0;
                int phases = session.getEffectivePhases();
                double newLimitA;
                if (phases > 1 && voltage < 300) {
                    newLimitA = (newLimit * 1000) / (voltage * phases);
                } else if (phases > 1) {
                    newLimitA = (newLimit * 1000) / (voltage * Math.sqrt(3));
                } else {
                    newLimitA = (newLimit * 1000) / voltage;
                }
                session.setScpLimitA(newLimitA);
            } else {
                // Plus de profil actif limitant
                session.setScpLimitKw(0);
                session.setScpLimitA(0);
                session.setScpProfileId(null);
                session.setScpPurpose(null);
                session.setActiveChargingProfile(null);
            }

            session.addLog(LogEntry.info("SCP", "Charging profile(s) cleared"));
            logToSession(session, "ClearChargingProfile ACCEPTED");
        } else {
            responseStatus = ClearChargingProfileStatus.UNKNOWN;
            logToSession(session, "ClearChargingProfile UNKNOWN - no matching profile found");
        }

        log.info("[{}] ClearChargingProfile: id={}, connector={}, purpose={}, stackLevel={}, result={}",
                session.getId(), id, connectorId, chargingProfilePurposeStr, stackLevel, responseStatus);

        Map<String, Object> response = createResponse(responseStatus);
        logExit(session, response);
        return response;
    }
}
