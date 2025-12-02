package com.evse.simulator.domain.service;

import com.evse.simulator.model.PerformanceMetrics;

/**
 * Service de collecte et gestion des métriques de performance.
 */
public interface MetricsService {

    /**
     * Collecte et diffuse les métriques périodiquement.
     */
    void collectAndBroadcastMetrics();

    /**
     * Collecte les métriques actuelles.
     *
     * @return métriques de performance
     */
    PerformanceMetrics collectMetrics();

    /**
     * Enregistre une latence de message.
     *
     * @param latencyMs latence en millisecondes
     */
    void recordLatency(long latencyMs);

    /**
     * Incrémente le compteur de messages envoyés.
     */
    void incrementMessagesSent();

    /**
     * Incrémente le compteur de messages reçus.
     */
    void incrementMessagesReceived();

    /**
     * Incrémente le compteur d'erreurs.
     */
    void incrementErrors();

    /**
     * Réinitialise les compteurs.
     */
    void resetCounters();

    /**
     * Récupère le nombre de messages envoyés.
     */
    long getMessagesSent();

    /**
     * Récupère le nombre de messages reçus.
     */
    long getMessagesReceived();

    /**
     * Récupère le nombre d'erreurs.
     */
    long getErrorsCount();
}
