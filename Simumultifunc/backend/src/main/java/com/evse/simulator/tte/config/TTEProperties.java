package com.evse.simulator.tte.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Configuration properties for TTE API integration.
 * Loaded from application.yml under the 'tte' prefix.
 * Supports multiple Cognito profiles (e.g., GPM, Other tests).
 */
@Data
@Component
@ConfigurationProperties(prefix = "tte")
public class TTEProperties {

    /**
     * Enable/disable TTE integration
     */
    private boolean enabled = true;

    /**
     * Default Cognito OAuth2 configuration (legacy, for backward compatibility)
     */
    private Cognito cognito = new Cognito();

    /**
     * Multiple Cognito profiles for different use cases
     */
    private List<CognitoProfile> profiles = new ArrayList<>();

    /**
     * Currently active profile name
     */
    private String activeProfile = "default";

    /**
     * TTE API configuration
     */
    private Api api = new Api();

    /**
     * Get the active Cognito configuration.
     * Returns the selected profile or falls back to default cognito config.
     */
    public Cognito getActiveCognito() {
        if (profiles.isEmpty()) {
            return cognito;
        }

        return profiles.stream()
                .filter(p -> p.getName().equals(activeProfile))
                .findFirst()
                .map(CognitoProfile::toCognito)
                .orElse(cognito);
    }

    /**
     * Add or update a profile
     */
    public void addProfile(CognitoProfile profile) {
        profiles.removeIf(p -> p.getName().equals(profile.getName()));
        profiles.add(profile);
    }

    /**
     * Get a profile by name
     */
    public Optional<CognitoProfile> getProfile(String name) {
        return profiles.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst();
    }

    /**
     * Remove a profile by name
     */
    public boolean removeProfile(String name) {
        return profiles.removeIf(p -> p.getName().equals(name));
    }

    /**
     * A named Cognito profile for different use cases (GPM, Other tests, etc.)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CognitoProfile {
        private String name;
        private String description;
        private String clientId;
        private String clientSecret;
        private String tokenUrl = "https://tte-pool-prod.auth.eu-central-1.amazoncognito.com/oauth2/token";

        public Cognito toCognito() {
            Cognito c = new Cognito();
            c.setClientId(clientId);
            c.setClientSecret(clientSecret);
            c.setTokenUrl(tokenUrl);
            return c;
        }

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }

    @Data
    public static class Cognito {
        /**
         * Cognito token endpoint URL
         */
        private String tokenUrl = "https://tte-pool-prod.auth.eu-central-1.amazoncognito.com/oauth2/token";

        /**
         * OAuth2 client ID
         */
        private String clientId;

        /**
         * OAuth2 client secret
         */
        private String clientSecret;

        /**
         * Seconds before expiration to refresh token
         */
        private int tokenExpiryBufferSeconds = 300;

        /**
         * Check if credentials are configured
         */
        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank()
                    && clientSecret != null && !clientSecret.isBlank();
        }
    }

    @Data
    public static class Api {
        /**
         * TTE API base URL (deprecated - use testUrl/ppUrl)
         */
        private String baseUrl = "https://api.total-ev-charge.com";

        /**
         * TTE API URL pour l'environnement Test
         */
        private String testUrl = "https://evplatform.evcharge-test.totalenergies.com";

        /**
         * TTE API URL pour l'environnement PP (Pre-Production)
         */
        private String ppUrl = "https://evplatform.evcharge-pp.totalenergies.com";

        /**
         * API call timeout in seconds
         */
        private int timeoutSeconds = 30;

        /**
         * Force l'utilisation de l'URL PP pour TTE API.
         * Le token Cognito ne fonctionne qu'en PP.
         * Par défaut: true (toujours utiliser PP)
         */
        private boolean forcePp = true;

        /**
         * Retourne l'URL TTE correspondant à l'environnement OCPP de la session.
         * Si forcePp=true, retourne toujours l'URL PP (le token Cognito ne marche qu'en PP).
         *
         * @param csmsUrl URL WebSocket OCPP de la session
         * @return URL de base TTE pour l'environnement correspondant
         */
        public String getUrlForEnvironment(String csmsUrl) {
            // Le token Cognito ne fonctionne qu'en PP - forcer PP
            if (forcePp) {
                return ppUrl;
            }

            if (csmsUrl == null || csmsUrl.isBlank()) {
                return testUrl; // Default to test
            }

            String lowerUrl = csmsUrl.toLowerCase();

            // Détection environnement PP
            if (lowerUrl.contains("evse.total-ev-charge.com") ||
                lowerUrl.contains("evcharge-pp") ||
                lowerUrl.contains("-pp.")) {
                return ppUrl;
            }

            // Détection environnement Test
            if (lowerUrl.contains("evse-test") ||
                lowerUrl.contains("evcharge-test") ||
                lowerUrl.contains("-test.")) {
                return testUrl;
            }

            // Par défaut: Test
            return testUrl;
        }
    }
}
