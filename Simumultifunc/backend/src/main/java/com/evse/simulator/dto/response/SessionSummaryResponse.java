package com.evse.simulator.dto.response;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.model.enums.SessionState;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reponse allegee pour les listes de sessions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Resume d'une session (pour liste)")
public class SessionSummaryResponse {

    @Schema(description = "ID unique de la session", example = "sess-12345")
    private String id;

    @Schema(description = "Titre de la session", example = "Test CP001")
    private String title;

    @Schema(description = "ID du Charge Point", example = "CP001")
    private String cpId;

    @Schema(description = "Etat de la session", example = "CHARGING",
            allowableValues = {"IDLE", "CONNECTING", "CONNECTED", "PREPARING",
                    "CHARGING", "SUSPENDED_EV", "SUSPENDED_EVSE", "FINISHING",
                    "RESERVED", "UNAVAILABLE", "FAULTED", "DISCONNECTED"})
    private SessionState state;

    @Schema(description = "Type de chargeur", example = "AC_TRI")
    private ChargerType chargerType;

    @Schema(description = "SoC actuel (%)", example = "45")
    private double soc;

    @Schema(description = "Puissance actuelle (kW)", example = "11.0")
    private double currentPowerKw;

    @Schema(description = "Energie delivree (kWh)", example = "12.5")
    private double energyDeliveredKwh;

    @Schema(description = "Connecte au CSMS", example = "true")
    private boolean connected;

    @Schema(description = "En cours de charge", example = "true")
    private boolean charging;

    /**
     * Convertit un modele Session en SessionSummaryResponse.
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
