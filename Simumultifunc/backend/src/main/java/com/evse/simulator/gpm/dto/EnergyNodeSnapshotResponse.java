package com.evse.simulator.gpm.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response de l'API TTE pour les energy node snapshots.
 * GET /qa/energy-node-snapshots
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnergyNodeSnapshotResponse {

    private String rootNodeId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    private List<NodeSnapshot> nodes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeSnapshot {
        private String nodeId;
        private String type; // ROOT, EVSE, BUILDING, etc.
        private double activePowerW;
        private double energyWhTotal;
        private double maxPowerW;
        private int connectedEvses;
        private int chargingEvses;
    }
}
