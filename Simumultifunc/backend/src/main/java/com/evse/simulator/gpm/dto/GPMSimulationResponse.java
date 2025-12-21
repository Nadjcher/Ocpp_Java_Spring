package com.evse.simulator.gpm.dto;

import com.evse.simulator.gpm.model.GPMApiError;
import com.evse.simulator.gpm.model.GPMTickResult;
import com.evse.simulator.gpm.model.GPMVehicleState;
import com.evse.simulator.gpm.model.enums.GPMSimulationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO pour une simulation GPM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GPMSimulationResponse {

    private String id;
    private String name;
    private String dryRunId;
    private GPMSimulationStatus status;

    // Configuration
    private String rootNodeId;
    private int tickIntervalMinutes;
    private int numberOfTicks;
    private double timeScale;

    // Progression
    private int currentTick;
    private int totalTicks;
    private double progressPercent;

    // Timing
    private Instant startedAt;
    private Instant completedAt;
    private Instant currentSimulatedTime;

    // Véhicules
    private List<GPMVehicleState> vehicles;

    // Résultats (optionnel, si demandé)
    private List<GPMTickResult> tickResults;

    // Erreurs
    private List<GPMApiError> apiErrors;
    private int errorCount;

    // Statistiques
    private double totalEnergyWh;
    private double averagePowerW;
    private double peakPowerW;
}
