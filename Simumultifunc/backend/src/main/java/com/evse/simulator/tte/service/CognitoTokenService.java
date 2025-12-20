package com.evse.simulator.tte.service;

import com.evse.simulator.tte.config.TTEProperties;
import com.evse.simulator.tte.model.TokenInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service de gestion du token Cognito AWS pour l'API TTE.
 * <p>
 * Fonctionnalités:
 * - Cache du token avec gestion d'expiration
 * - Renouvellement automatique avant expiration
 * - Thread-safe avec ReentrantLock
 * - Jamais d'exposition du token ou des secrets dans les logs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CognitoTokenService {

    private final TTEProperties properties;
    private final RestTemplate restTemplate;

    // Token cache
    private String cachedToken;
    private Instant tokenExpiresAt;
    private Instant tokenObtainedAt;

    // Thread safety
    private final ReentrantLock lock = new ReentrantLock();

    // Stats
    private int refreshCount = 0;
    private int errorCount = 0;
    private String lastError;

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("TTE integration is disabled");
            return;
        }

        if (!properties.getCognito().isConfigured()) {
            log.warn("TTE Cognito credentials not configured. Set TTE_CLIENT_ID and TTE_CLIENT_SECRET environment variables.");
            return;
        }

        log.info("TTE Cognito service initialized. Token URL: {}",
                properties.getCognito().getTokenUrl());

        // Obtain initial token
        try {
            refreshToken();
            log.info("Initial TTE token obtained successfully");
        } catch (Exception e) {
            log.error("Failed to obtain initial TTE token: {}", e.getMessage());
        }
    }

    /**
     * Retourne un token valide.
     * Renouvelle automatiquement si expiré ou proche de l'expiration.
     *
     * @return Token d'accès valide
     * @throws IllegalStateException si le service n'est pas configuré
     * @throws RuntimeException si le renouvellement échoue
     */
    public String getValidToken() {
        if (!isConfigured()) {
            throw new IllegalStateException("TTE Cognito is not configured");
        }

        lock.lock();
        try {
            if (isTokenValid()) {
                return cachedToken;
            }
            return refreshToken();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Invalide le token actuel pour forcer un renouvellement.
     * Utilisé lors du changement de profil Cognito.
     */
    public void invalidateToken() {
        lock.lock();
        try {
            cachedToken = null;
            tokenExpiresAt = null;
            tokenObtainedAt = null;
            log.info("Token invalidated - will refresh on next request");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retourne le nom du profil actif.
     */
    public String getActiveProfileName() {
        return properties.getActiveProfile();
    }

    /**
     * Force le renouvellement du token.
     *
     * @return Nouveau token
     * @throws RuntimeException si le renouvellement échoue
     */
    public String refreshToken() {
        lock.lock();
        try {
            TTEProperties.Cognito activeCognito = properties.getActiveCognito();
            log.debug("Refreshing TTE Cognito token using profile: {}", properties.getActiveProfile());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", activeCognito.getClientId());
            body.add("client_secret", activeCognito.getClientSecret());

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    activeCognito.getTokenUrl(),
                    request,
                    TokenResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                TokenResponse tokenResponse = response.getBody();

                cachedToken = tokenResponse.getAccessToken();
                tokenObtainedAt = Instant.now();

                // Cognito peut retourner une durée longue (6h = 21600s), mais l'API TTE
                // peut limiter à 1h. On utilise le min entre la valeur Cognito et 3600s (1h)
                // pour éviter les problèmes de token invalide côté API.
                int cognitoExpiresIn = tokenResponse.getExpiresIn();
                int effectiveExpiresIn = Math.min(cognitoExpiresIn, 3600); // Max 1 heure
                tokenExpiresAt = tokenObtainedAt.plusSeconds(effectiveExpiresIn);
                refreshCount++;
                lastError = null;

                log.info("TTE token refreshed successfully. Cognito says {}s, using {}s (refresh #{})",
                        cognitoExpiresIn, effectiveExpiresIn, refreshCount);

                return cachedToken;
            } else {
                throw new RuntimeException("Invalid response from Cognito: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            errorCount++;
            lastError = e.getMessage();
            log.error("Failed to refresh TTE token: {}", e.getMessage());
            throw new RuntimeException("Failed to refresh TTE token", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retourne les informations sur le token SANS exposer le token lui-même.
     */
    public TokenInfo getTokenInfo() {
        return TokenInfo.builder()
                .configured(isConfigured())
                .enabled(properties.isEnabled())
                .hasValidToken(isTokenValid())
                .tokenObtainedAt(tokenObtainedAt)
                .tokenExpiresAt(tokenExpiresAt)
                .secondsRemaining(getSecondsRemaining())
                .refreshCount(refreshCount)
                .errorCount(errorCount)
                .lastError(lastError)
                .activeProfile(properties.getActiveProfile())
                .availableProfiles(getAvailableProfileNames())
                .build();
    }

    /**
     * Retourne la liste des noms de profils disponibles.
     */
    public java.util.List<String> getAvailableProfileNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add("default");
        properties.getProfiles().forEach(p -> {
            if (!names.contains(p.getName())) {
                names.add(p.getName());
            }
        });
        return names;
    }

    /**
     * Vérifie si le service est configuré.
     */
    public boolean isConfigured() {
        return properties.isEnabled() && properties.getActiveCognito().isConfigured();
    }

    /**
     * Vérifie si le token actuel est valide.
     * Un token est considéré invalide s'il expire dans moins de X secondes (buffer).
     */
    public boolean isTokenValid() {
        if (cachedToken == null || tokenExpiresAt == null) {
            return false;
        }

        Instant expiryWithBuffer = tokenExpiresAt.minusSeconds(
                properties.getCognito().getTokenExpiryBufferSeconds()
        );

        return Instant.now().isBefore(expiryWithBuffer);
    }

    /**
     * Retourne le nombre de secondes avant expiration.
     */
    public long getSecondsRemaining() {
        if (tokenExpiresAt == null) {
            return 0;
        }
        long remaining = tokenExpiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    /**
     * DTO pour la réponse Cognito.
     */
    @Data
    private static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("expires_in")
        private int expiresIn;

        @JsonProperty("token_type")
        private String tokenType;
    }
}
