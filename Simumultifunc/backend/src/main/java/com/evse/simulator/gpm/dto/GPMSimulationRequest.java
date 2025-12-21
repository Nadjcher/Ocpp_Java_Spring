package com.evse.simulator.gpm.dto;

import com.evse.simulator.gpm.model.enums.GPMSimulationMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request pour créer une simulation GPM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GPMSimulationRequest {

    @NotBlank(message = "Le nom de la simulation est requis")
    private String name;

    @NotBlank(message = "Le rootNodeId est requis")
    private String rootNodeId;

    @Min(value = 1, message = "L'intervalle doit être au moins 1 minute")
    @Builder.Default
    private int tickIntervalMinutes = 15;

    @Min(value = 1, message = "Le nombre de ticks doit être au moins 1")
    @Builder.Default
    private int numberOfTicks = 96; // 24h par défaut

    @Builder.Default
    private double timeScale = 1.0; // 1.0 = temps réel

    @Builder.Default
    private GPMSimulationMode mode = GPMSimulationMode.DRY_RUN;
}
