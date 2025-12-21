package com.evse.simulator.gpm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Résultat d'un tick de simulation GPM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GPMTickResult {

    private int tick;
    private Instant timestamp;
    private Instant simulatedTime;
    private String tickId;

    @Builder.Default
    private List<GPMVehicleTickResult> vehicleResults = new ArrayList<>();

    // Totaux
    private double totalPowerW;
    private double totalEnergyWh;
}
