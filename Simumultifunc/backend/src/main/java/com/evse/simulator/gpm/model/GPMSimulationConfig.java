package com.evse.simulator.gpm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration d'une simulation GPM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GPMSimulationConfig {

    private String name;
    private String rootNodeId;

    @Builder.Default
    private int tickIntervalMinutes = 5;

    @Builder.Default
    private int numberOfTicks = 12;

    private Integer powerLimitW;

    @Builder.Default
    private double timeScale = 10.0;  // 1 = temps réel, 10 = 10x plus rapide

    @Builder.Default
    private String mode = "DRY_RUN";  // DRY_RUN ou LOCAL

    public boolean isDryRunMode() {
        return "DRY_RUN".equalsIgnoreCase(mode);
    }
}
