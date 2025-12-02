package com.evse.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Point dans un planning de charge.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChargingSchedulePoint {
    /**
     * Offset en secondes depuis le début du planning.
     */
    private Integer startOffset;

    /**
     * Limite de puissance en kW.
     */
    private Double limit;
}