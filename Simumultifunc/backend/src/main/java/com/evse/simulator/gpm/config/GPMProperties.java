package com.evse.simulator.gpm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties pour le simulateur GPM.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gpm.dryrun")
public class GPMProperties {

    /**
     * Active/désactive le mode Dry-Run.
     */
    private boolean enabled = true;

    /**
     * URL de base de l'API TTE Energy Service Manager.
     */
    private String baseUrl;

    /**
     * URL pour obtenir le token OAuth2 Cognito.
     */
    private String tokenUrl;

    /**
     * Client ID pour l'authentification OAuth2 Cognito.
     */
    private String clientId;

    /**
     * Client Secret pour l'authentification OAuth2 Cognito.
     */
    private String clientSecret;

    /**
     * Intervalle par défaut entre les ticks (minutes).
     */
    private int defaultTickInterval = 15;

    /**
     * Nombre de ticks par défaut (96 = 24h avec intervalle 15min).
     */
    private int defaultNumberOfTicks = 96;

    /**
     * Timeout pour les requêtes API (millisecondes).
     */
    private int apiTimeout = 30000;

    /**
     * Nombre de tentatives en cas d'échec.
     */
    private int retryCount = 3;

    /**
     * Délai entre les tentatives (millisecondes).
     */
    private int retryDelay = 1000;
}
