package com.evse.simulator.ocpi.model;

import com.evse.simulator.ocpi.OCPIModule;
import com.evse.simulator.ocpi.OCPIRole;
import com.evse.simulator.ocpi.OCPIVersion;
import lombok.*;

import java.time.Instant;
import java.util.*;

/**
 * Configuration d'un partenaire OCPI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Partner {

    private String id;
    private String name;
    private String countryCode;        // FR, DE, BE, NL
    private String partyId;            // TCB, V75, SHR
    private OCPIRole role;             // CPO, MSP, HUB
    private OCPIVersion version;       // V2_1_1, V2_2_1

    // Environnement actif
    private String activeEnvironment;
    private Map<String, EnvironmentConfig> environments;

    // Endpoints découverts
    private String versionsUrl;
    private Map<String, String> endpoints; // module -> url

    // Modules supportés
    private List<OCPIModule> modules;

    // Métadonnées
    private BusinessDetails businessDetails;
    private boolean active;
    private Instant lastSync;
    private Instant credentialsExpiry;

    // Pour les tests
    private List<String> cpoGroups;
    private String evseSimulatorUrl;

    /**
     * Configuration d'un environnement (PP, TEST, PROD).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvironmentConfig {
        private String baseUrl;
        private String versionsUrl;
        private String tokenA;             // Token reçu du partenaire
        private String tokenB;             // Token envoyé au partenaire
        private String tokenC;             // Token pour tests (optionnel)
        private CognitoConfig cognito;     // AWS Cognito si applicable
    }

    /**
     * Configuration AWS Cognito pour l'authentification.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CognitoConfig {
        private String tokenUrl;
        private String clientId;
        private String clientSecret;
    }

    /**
     * Informations business du partenaire.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessDetails {
        private String name;
        private String website;
        private String logo;
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Récupère la configuration de l'environnement actif.
     */
    public EnvironmentConfig getActiveConfig() {
        if (environments == null || activeEnvironment == null) {
            return null;
        }
        return environments.get(activeEnvironment);
    }

    /**
     * Récupère l'URL de base de l'environnement actif.
     */
    public String getBaseUrl() {
        EnvironmentConfig config = getActiveConfig();
        return config != null ? config.getBaseUrl() : null;
    }

    /**
     * Récupère le token B (envoyé au partenaire) de l'environnement actif.
     */
    public String getTokenB() {
        EnvironmentConfig config = getActiveConfig();
        return config != null ? config.getTokenB() : null;
    }

    /**
     * Récupère le token A (reçu du partenaire) de l'environnement actif.
     */
    public String getTokenA() {
        EnvironmentConfig config = getActiveConfig();
        return config != null ? config.getTokenA() : null;
    }

    /**
     * Récupère la config Cognito de l'environnement actif.
     */
    public CognitoConfig getCognito() {
        EnvironmentConfig config = getActiveConfig();
        return config != null ? config.getCognito() : null;
    }

    /**
     * Vérifie si le partenaire supporte un module.
     */
    public boolean supportsModule(OCPIModule module) {
        return modules != null && modules.contains(module);
    }

    /**
     * Récupère l'URL d'un endpoint.
     */
    public String getEndpoint(OCPIModule module) {
        if (endpoints == null) return null;
        return endpoints.get(module.getValue());
    }

    /**
     * Récupère l'URL d'un endpoint par nom.
     */
    public String getEndpoint(String moduleName) {
        if (endpoints == null) return null;
        return endpoints.get(moduleName);
    }
}
