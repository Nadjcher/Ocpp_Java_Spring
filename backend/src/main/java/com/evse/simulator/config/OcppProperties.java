package com.evse.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

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
    private String defaultUrl = "ws://localhost:8080/ocpp";

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

    @Data
    public static class MessageConfig {
        /**
         * Taille maximale des messages en octets.
         */
        @Positive
        private int maxSize = 65536;
    }
}
