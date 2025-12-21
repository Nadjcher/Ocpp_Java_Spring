package com.evse.simulator.gpm.model;

import com.evse.simulator.gpm.model.enums.GPMSimulationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente une simulation GPM complète.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GPMSimulation {

    private String id;
    private GPMSimulationConfig config;
    private String dryRunId;

    @Builder.Default
    private GPMSimulationStatus status = GPMSimulationStatus.CREATED;

    @Builder.Default
    private List<GPMVehicleState> vehicles = new ArrayList<>();

    // Progression
    @Builder.Default
    private int currentTick = 0;

    private int totalTicks;

    private Instant startedAt;
    private Instant completedAt;

    // Résultats par tick
    @Builder.Default
    private List<GPMTickResult> tickResults = new ArrayList<>();

    // Erreurs API
    @Builder.Default
    private List<GPMApiError> apiErrors = new ArrayList<>();
}
