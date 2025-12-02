package com.evse.simulator.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Réponse pour les opérations de connexion/déconnexion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectionResponse {

    /**
     * ID de la session.
     */
    private String sessionId;

    /**
     * Statut de la connexion.
     */
    private String status;

    /**
     * Connecté ou non.
     */
    private boolean connected;

    /**
     * Message descriptif.
     */
    private String message;

    /**
     * Horodatage.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Crée une réponse de connexion réussie.
     */
    public static ConnectionResponse connected(String sessionId) {
        return ConnectionResponse.builder()
                .sessionId(sessionId)
                .status("connected")
                .connected(true)
                .message("Successfully connected to CSMS")
                .build();
    }

    /**
     * Crée une réponse de déconnexion.
     */
    public static ConnectionResponse disconnected(String sessionId) {
        return ConnectionResponse.builder()
                .sessionId(sessionId)
                .status("disconnected")
                .connected(false)
                .message("Disconnected from CSMS")
                .build();
    }

    /**
     * Crée une réponse d'échec de connexion.
     */
    public static ConnectionResponse failed(String sessionId, String reason) {
        return ConnectionResponse.builder()
                .sessionId(sessionId)
                .status("failed")
                .connected(false)
                .message(reason)
                .build();
    }
}