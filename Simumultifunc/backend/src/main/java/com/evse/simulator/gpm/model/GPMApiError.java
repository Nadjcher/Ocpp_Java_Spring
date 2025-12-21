package com.evse.simulator.gpm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Erreur API lors d'une simulation GPM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GPMApiError {

    private int tick;
    private String type;  // MeterValue, RegulationTick, Snapshot, Setpoint
    private String evseId;
    private String message;
    private Instant timestamp;
}
