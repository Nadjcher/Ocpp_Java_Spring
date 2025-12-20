package com.evse.simulator.tte.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Information sur le statut du token Cognito.
 * NE CONTIENT PAS le token lui-même pour des raisons de sécurité.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo {

    /**
     * Le service TTE est-il configuré (credentials présents)?
     */
    private boolean configured;

    /**
     * Le service TTE est-il activé?
     */
    private boolean enabled;

    /**
     * Un token valide est-il disponible?
     */
    private boolean hasValidToken;

    /**
     * Date/heure d'obtention du token
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant tokenObtainedAt;

    /**
     * Date/heure d'expiration du token
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant tokenExpiresAt;

    /**
     * Secondes restantes avant expiration
     */
    private long secondsRemaining;

    /**
     * Nombre total de renouvellements effectués
     */
    private int refreshCount;

    /**
     * Nombre d'erreurs de renouvellement
     */
    private int errorCount;

    /**
     * Dernière erreur (si applicable)
     */
    private String lastError;

    /**
     * Nom du profil Cognito actif
     */
    private String activeProfile;

    /**
     * Liste des profils disponibles
     */
    private List<String> availableProfiles;

    /**
     * Statut lisible
     */
    public String getStatus() {
        if (!enabled) {
            return "DISABLED";
        }
        if (!configured) {
            return "NOT_CONFIGURED";
        }
        if (!hasValidToken) {
            return "EXPIRED";
        }
        if (secondsRemaining < 300) {
            return "EXPIRING_SOON";
        }
        return "VALID";
    }

    /**
     * Minutes et secondes restantes formatées
     */
    public String getRemainingFormatted() {
        if (secondsRemaining <= 0) {
            return "Expiré";
        }
        long minutes = secondsRemaining / 60;
        long seconds = secondsRemaining % 60;
        return String.format("%dm %ds", minutes, seconds);
    }
}
