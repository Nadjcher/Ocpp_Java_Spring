package com.evse.simulator.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Réponse du statut d'un test de charge.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoadTestResponse {

    /**
     * ID du test.
     */
    private String testId;

    /**
     * Test en cours.
     */
    private boolean running;

    /**
     * Nombre de sessions cibles.
     */
    private int targetSessions;

    /**
     * Nombre de sessions actuelles.
     */
    private int currentSessions;

    /**
     * Nombre de connexions réussies.
     */
    private int connectedSessions;

    /**
     * Nombre d'erreurs.
     */
    private long errors;

    /**
     * Messages envoyés.
     */
    private long messagesSent;

    /**
     * Messages reçus.
     */
    private long messagesReceived;

    /**
     * Progression (%).
     */
    private double progress;

    /**
     * Durée du test en ms.
     */
    private long durationMs;

    /**
     * Temps de démarrage.
     */
    private LocalDateTime startTime;

    /**
     * Horodatage.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Crée une réponse de démarrage.
     */
    public static LoadTestResponse started(String testId, int targetSessions) {
        return LoadTestResponse.builder()
                .testId(testId)
                .running(true)
                .targetSessions(targetSessions)
                .currentSessions(0)
                .progress(0)
                .startTime(LocalDateTime.now())
                .build();
    }

    /**
     * Crée une réponse d'arrêt.
     */
    public static LoadTestResponse stopped() {
        return LoadTestResponse.builder()
                .running(false)
                .build();
    }
}
