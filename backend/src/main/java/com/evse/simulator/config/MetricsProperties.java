package com.evse.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configuration des métriques de performance.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "metrics")
public class MetricsProperties {

    /**
     * Intervalle de diffusion des métriques en millisecondes.
     */
    @Positive
    private int broadcastInterval = 1000;

    /**
     * Nombre maximum d'échantillons de latence conservés.
     */
    @Min(100)
    private int latencySamples = 1000;
}
