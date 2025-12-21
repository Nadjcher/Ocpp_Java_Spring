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
    private String baseUrl = "https://evplatform.evcharge-test.totalenergies.com/apigw/energy-service-manager";

    /**
     * URL pour obtenir le token OAuth2 Cognito.
     */
    private String tokenUrl = "https://tte-pool-prod.auth.eu-central-1.amazoncognito.com/oauth2/token";

    /**
     * Client ID pour l'authentification OAuth2 Cognito.
     */
    private String clientId = "3fuql0avnrhgkooe7s7404mjsb";

    /**
     * Client Secret pour l'authentification OAuth2 Cognito.
     */
    private String clientSecret = "o5edbcc02b6ugvc39qcbcsaq14g9qkqmetp2q3agqon0t7f536t";

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
