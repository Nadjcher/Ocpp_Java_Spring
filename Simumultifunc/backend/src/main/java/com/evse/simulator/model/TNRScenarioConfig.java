package com.evse.simulator.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Configuration d'un scénario TNR.
 */
@Data
public class TNRScenarioConfig {
    /**
     * Nom du scénario.
     */
    private String name;

    /**
     * Description du scénario.
     */
    private String description;

    /**
     * Paramètres de configuration.
     */
    private Map<String, Object> parameters;

    /**
     * Tags pour filtrage.
     */
    private List<String> tags;
}