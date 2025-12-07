package com.evse.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration OCPP.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ocpp")
public class OcppProperties {

    /**
     * Version OCPP supportée.
     */
    @NotBlank
    private String version = "1.6";

    /**
     * Intervalle de heartbeat en millisecondes.
     */
    @Positive
    private int heartbeatInterval = 30000;

    /**
     * Intervalle d'envoi des MeterValues en millisecondes.
     */
    @Positive
    private int meterValuesInterval = 10000;

    /**
     * URL par défaut du CSMS.
     */
    private String defaultUrl = "ws://localhost:8887/ocpp";

    /**
     * Timeout de connexion en millisecondes.
     */
    @Positive
    private int connectionTimeout = 10000;

    /**
     * Délai de reconnexion en millisecondes.
     */
    @Positive
    private int reconnectDelay = 5000;

    /**
     * Nombre maximum de tentatives de reconnexion.
     */
    @Min(0)
    @Max(100)
    private int maxReconnectAttempts = 5;

    /**
     * Configuration des messages.
     */
    private MessageConfig message = new MessageConfig();

    /**
     * Environnements CSMS disponibles.
     */
    private Map<String, EnvironmentConfig> environments = new HashMap<>();

    /**
     * Environnement par défaut.
     */
    private String defaultEnvironment = "test";

    @Data
    public static class MessageConfig {
        /**
         * Taille maximale des messages en octets.
         */
        @Positive
        private int maxSize = 65536;
    }

    @Data
    public static class EnvironmentConfig {
        /**
         * Nom affiché de l'environnement.
         */
        private String name;

        /**
         * URL WebSocket OCPP.
         */
        private String url;
    }

    /**
     * Récupère l'URL pour un environnement donné.
     */
    public String getEnvironmentUrl(String envKey) {
        EnvironmentConfig env = environments.get(envKey);
        return env != null ? env.getUrl() : defaultUrl;
    }

    /**
     * Récupère le nom d'un environnement.
     */
    public String getEnvironmentName(String envKey) {
        EnvironmentConfig env = environments.get(envKey);
        return env != null ? env.getName() : envKey;
    }
}
