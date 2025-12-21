package com.evse.simulator.gpm.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response de l'API TTE pour les setpoints.
 * GET /qa/setpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetpointResponse {

    private String rootNodeId;
    private String tickId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private List<Setpoint> setpoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Setpoint {
        private String evseId;
        private double maxPowerW;
        private double maxCurrentA;
        private String status; // ACTIVE, SUSPENDED, etc.
    }
}
