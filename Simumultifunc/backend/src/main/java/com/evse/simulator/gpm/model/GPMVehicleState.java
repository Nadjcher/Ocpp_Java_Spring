package com.evse.simulator.gpm.model;

import com.evse.simulator.gpm.model.enums.GPMChargeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * État d'un véhicule dans une simulation GPM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GPMVehicleState {

    private String evseId;
    private String evTypeId;
    private GPMChargeType chargeType;
    private String transactionId;

    // Capacité et puissance
    private double capacityWh;
    private double maxPowerW;

    // SOC
    private double initialSoc;
    private double currentSoc;
    private double targetSoc;

    // État courant
    @Builder.Default
    private double currentPowerW = 0;

    @Builder.Default
    private double energyRegisterWh = 0;

    @Builder.Default
    private boolean charging = true;

    // Régulation
    private Double lastSetpointW;

    // Historique
    @Builder.Default
    private List<HistoryEntry> history = new ArrayList<>();

    /**
     * Entrée d'historique.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryEntry {
        private int tick;
        private double soc;
        private double powerW;
        private double energyWh;
        private Double setpointW;
        private Instant timestamp;
    }
}
