package com.evse.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configuration des tests de charge.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "loadtest")
public class LoadTestProperties {

    /**
     * Nombre maximum de sessions autorisées pour les tests de charge.
     */
    @Min(1)
    @Max(100000)
    private int maxSessions = 25000;

    /**
     * Taille du pool de threads pour les tests de charge.
     */
    @Positive
    private int threadPoolSize = 10;

    /**
     * Délai minimum entre créations de sessions (ms).
     */
    @Min(1)
    private int minDelayMs = 10;
}
