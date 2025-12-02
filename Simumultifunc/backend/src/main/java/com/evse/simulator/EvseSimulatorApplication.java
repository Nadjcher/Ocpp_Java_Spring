package com.evse.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application principale du simulateur EVSE.
 * <p>
 * Cette application implémente un simulateur de bornes de recharge électrique
 * compatible avec le protocole OCPP 1.6. Elle supporte jusqu'à 25 000 connexions
 * WebSocket simultanées pour des tests de charge intensifs.
 * </p>
 *
 * @author EVSE Simulator Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class EvseSimulatorApplication {

    /**
     * Point d'entrée de l'application.
     *
     * @param args arguments de ligne de commande
     */
    public static void main(String[] args) {
        SpringApplication.run(EvseSimulatorApplication.class, args);
    }
}