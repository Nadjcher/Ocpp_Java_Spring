package com.evse.simulator.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Métadonnées d'une exécution TNR.
 */
@Data
public class ExecutionMeta {
    private String id;
    private String scenarioName;
    private LocalDateTime executedAt;
    private int eventCount;
    private String signature;
}
