package com.evse.simulator.gpm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Résultat d'un tick pour un véhicule spécifique.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GPMVehicleTickResult {

    private String evseId;
    private String transactionId;

    // Puissance
    private double requestedPowerW;
    private double actualPowerW;
    private Double setpointAppliedW;

    // Énergie
    private double energyChargedWh;

    // SOC
    private double socBefore;
    private double socAfter;

    // Courants par phase (pour TRI/MONO)
    private Double currentL1A;
    private Double currentL2A;
    private Double currentL3A;
}
