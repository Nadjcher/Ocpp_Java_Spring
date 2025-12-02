package com.evse.simulator.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Détail complet d'une exécution TNR.
 */
@Data
public class ExecutionDetail {
    public String id;
    public String scenarioName;
    public LocalDateTime executedAt;
    public List<TNREvent> events;
    public String signature;
}
