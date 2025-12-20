package com.evse.simulator.performance.model;

import lombok.*;

import java.util.List;

/**
 * Scénario de test de performance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerfScenario {
    private String id;
    private String name;
    private String description;
    private PerfConfig config;
    private List<String> actions;
    private List<String> tags;
}
