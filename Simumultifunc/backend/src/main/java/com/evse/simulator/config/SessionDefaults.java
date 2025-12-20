package com.evse.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration des valeurs par défaut pour les sessions.
 */
@Data
@Component
@ConfigurationProperties(prefix = "session.defaults")
public class SessionDefaults {

    /**
     * CP ID par défaut.
     */
    private String cpId = "SIMU-CP-001";

    /**
     * ID Tag par défaut.
     */
    private String idTag = "TAG-001";

    /**
     * Type de véhicule par défaut.
     */
    private String vehicle = "GENERIC";

    /**
     * Connector ID par défaut.
     */
    private int connectorId = 1;

    /**
     * Puissance max par défaut (kW).
     */
    private double maxPowerKw = 22.0;
}
