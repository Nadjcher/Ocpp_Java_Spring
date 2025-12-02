package com.evse.simulator.dto.response;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.SessionState;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse allégée pour les listes de sessions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionSummaryResponse {

    private String id;
    private String title;
    private String cpId;
    private SessionState state;
    private ChargerType chargerType;
    private double soc;
    private double currentPowerKw;
    private double energyDeliveredKwh;
    private boolean connected;
    private boolean charging;

    /**
     * Convertit un modèle Session en SessionSummaryResponse.
     */
    public static SessionSummaryResponse fromSession(Session session) {
        return SessionSummaryResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .cpId(session.getCpId())
                .state(session.getState())
                .chargerType(session.getChargerType())
                .soc(session.getSoc())
                .currentPowerKw(session.getCurrentPowerKw())
                .energyDeliveredKwh(session.getEnergyDeliveredKwh())
                .connected(session.isConnected())
                .charging(session.isCharging())
                .build();
    }
}