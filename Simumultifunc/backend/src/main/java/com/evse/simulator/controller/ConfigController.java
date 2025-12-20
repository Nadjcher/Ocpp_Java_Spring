package com.evse.simulator.controller;

import com.evse.simulator.config.OcppProperties;
import com.evse.simulator.config.SessionDefaults;
import com.evse.simulator.tte.config.TTEProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Contrôleur REST pour les configurations globales.
 * Expose la configuration non-sensible au frontend.
 */
@RestController
@RequestMapping("/api/config")
@Tag(name = "Config", description = "Configuration globale de l'application")
@Slf4j
@CrossOrigin
@RequiredArgsConstructor
public class ConfigController {

    private final OcppProperties ocppProperties;
    private final SessionDefaults sessionDefaults;
    private final TTEProperties tteProperties;
    private final com.evse.simulator.tte.service.CognitoTokenService cognitoTokenService;

    private final AtomicReference<String> priceToken = new AtomicReference<>("");
    private final AtomicReference<String> priceUrl = new AtomicReference<>("");

    @GetMapping("/price-token")
    @Operation(summary = "Récupère la configuration du token de prix")
    public ResponseEntity<Map<String, Object>> getPriceToken() {
        String token = priceToken.get();
        return ResponseEntity.ok(Map.of(
            "hasToken", token != null && !token.isEmpty(),
            "url", priceUrl.get()
        ));
    }

    @PostMapping("/price-token")
    @Operation(summary = "Configure le token de prix")
    public ResponseEntity<Map<String, Object>> setPriceToken(@RequestBody Map<String, Object> body) {
        String token = (String) body.getOrDefault("token", "");
        String url = (String) body.getOrDefault("url", "");

        priceToken.set(token);
        if (url != null && !url.isEmpty()) {
            priceUrl.set(url);
        }

        log.info("Price token configured: hasToken={}, url={}",
            token != null && !token.isEmpty(), priceUrl.get());

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "hasToken", token != null && !token.isEmpty()
        ));
    }

    /**
     * Retourne la liste des environnements OCPP disponibles.
     * Ne retourne PAS les URLs complètes (information sensible).
     */
    @GetMapping("/environments")
    @Operation(summary = "Liste des environnements OCPP disponibles")
    public ResponseEntity<Map<String, Object>> getEnvironments() {
        Map<String, Object> result = new HashMap<>();

        ocppProperties.getEnvironments().forEach((key, env) -> {
            result.put(key, Map.of(
                "name", env.getName() != null ? env.getName() : key,
                "id", key
            ));
        });

        result.put("default", ocppProperties.getDefaultEnvironment());

        return ResponseEntity.ok(result);
    }

    /**
     * Retourne les URLs OCPP par environnement.
     * Cette information est nécessaire pour le frontend pour établir les connexions.
     */
    @GetMapping("/ocpp-urls")
    @Operation(summary = "URLs OCPP par environnement")
    public ResponseEntity<Map<String, String>> getOcppUrls() {
        Map<String, String> urls = new HashMap<>();

        ocppProperties.getEnvironments().forEach((key, env) -> {
            if (env.getUrl() != null) {
                urls.put(key, env.getUrl());
            }
        });

        return ResponseEntity.ok(urls);
    }

    /**
     * Retourne les valeurs par défaut pour les sessions.
     */
    @GetMapping("/defaults")
    @Operation(summary = "Valeurs par défaut pour les sessions")
    public ResponseEntity<Map<String, Object>> getDefaults() {
        return ResponseEntity.ok(Map.of(
            "cpId", sessionDefaults.getCpId(),
            "idTag", sessionDefaults.getIdTag(),
            "vehicle", sessionDefaults.getVehicle(),
            "connectorId", sessionDefaults.getConnectorId(),
            "maxPowerKw", sessionDefaults.getMaxPowerKw()
        ));
    }

    /**
     * Retourne la configuration TTE (sans les secrets).
     */
    @GetMapping("/tte")
    @Operation(summary = "Configuration TTE")
    public ResponseEntity<Map<String, Object>> getTteConfig() {
        Map<String, Object> tteConfig = new HashMap<>();
        tteConfig.put("enabled", tteProperties.isEnabled());
        tteConfig.put("configured", tteProperties.getCognito().isConfigured());

        // Client ID masqué (premiers caractères seulement)
        String clientId = tteProperties.getCognito().getClientId();
        if (clientId != null && !clientId.isBlank()) {
            tteConfig.put("clientId", clientId.length() > 8 ? clientId.substring(0, 8) + "..." : clientId);
        } else {
            tteConfig.put("clientId", "");
        }

        tteConfig.put("tokenUrl", tteProperties.getCognito().getTokenUrl());

        Map<String, String> apiUrls = new HashMap<>();
        apiUrls.put("test", tteProperties.getApi().getTestUrl());
        apiUrls.put("pp", tteProperties.getApi().getPpUrl());
        tteConfig.put("apiUrls", apiUrls);

        // Ajouter les URLs OCPP
        Map<String, String> ocppUrls = new HashMap<>();
        ocppProperties.getEnvironments().forEach((key, env) -> {
            if (env.getUrl() != null) {
                ocppUrls.put(key, env.getUrl());
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("tte", tteConfig);
        result.put("ocppUrls", ocppUrls);
        result.put("serverPort", 8887);
        result.put("profile", System.getProperty("spring.profiles.active", "default"));

        return ResponseEntity.ok(result);
    }

    /**
     * Configure les identifiants TTE Cognito dynamiquement.
     */
    @PostMapping("/tte")
    @Operation(summary = "Configure les identifiants TTE Cognito")
    public ResponseEntity<Map<String, Object>> setTteCredentials(@RequestBody Map<String, Object> body) {
        String clientId = (String) body.get("clientId");
        String clientSecret = (String) body.get("clientSecret");

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", "clientId et clientSecret sont requis"
            ));
        }

        // Mettre à jour les credentials dans TTEProperties
        tteProperties.getCognito().setClientId(clientId);
        tteProperties.getCognito().setClientSecret(clientSecret);

        log.info("TTE credentials updated: clientId={}...", clientId.substring(0, Math.min(8, clientId.length())));

        // Essayer de rafraîchir le token avec les nouveaux credentials
        try {
            cognitoTokenService.refreshToken();
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "configured", true,
                "message", "Credentials configurés et token obtenu avec succès"
            ));
        } catch (Exception e) {
            log.error("Failed to refresh token with new credentials: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "configured", true,
                "tokenError", e.getMessage(),
                "message", "Credentials sauvegardés mais échec de l'obtention du token"
            ));
        }
    }

    // =========================================================================
    // TTE Cognito Profiles Management
    // =========================================================================

    /**
     * Liste tous les profils Cognito disponibles.
     */
    @GetMapping("/tte/profiles")
    @Operation(summary = "Liste les profils Cognito TTE")
    public ResponseEntity<Map<String, Object>> getTteProfiles() {
        java.util.List<Map<String, Object>> profiles = tteProperties.getProfiles().stream()
            .map(p -> {
                Map<String, Object> m = new HashMap<>();
                m.put("name", p.getName());
                m.put("description", p.getDescription() != null ? p.getDescription() : "");
                m.put("clientId", p.getClientId() != null && p.getClientId().length() > 8
                    ? p.getClientId().substring(0, 8) + "..." : (p.getClientId() != null ? p.getClientId() : ""));
                m.put("configured", p.isConfigured());
                return m;
            })
            .collect(java.util.stream.Collectors.toList());

        // Ajouter le profil "default" basé sur la config principale
        Map<String, Object> defaultProfile = new HashMap<>();
        defaultProfile.put("name", "default");
        defaultProfile.put("description", "Configuration par défaut");
        defaultProfile.put("clientId", tteProperties.getCognito().getClientId() != null && tteProperties.getCognito().getClientId().length() > 8
            ? tteProperties.getCognito().getClientId().substring(0, 8) + "..."
            : (tteProperties.getCognito().getClientId() != null ? tteProperties.getCognito().getClientId() : ""));
        defaultProfile.put("configured", tteProperties.getCognito().isConfigured());

        java.util.List<Map<String, Object>> allProfiles = new java.util.ArrayList<>();
        allProfiles.add(defaultProfile);
        allProfiles.addAll(profiles);

        Map<String, Object> result = new HashMap<>();
        result.put("profiles", allProfiles);
        result.put("activeProfile", tteProperties.getActiveProfile());
        return ResponseEntity.ok(result);
    }

    /**
     * Ajoute ou met à jour un profil Cognito.
     */
    @PostMapping("/tte/profiles")
    @Operation(summary = "Ajoute ou met à jour un profil Cognito")
    public ResponseEntity<Map<String, Object>> addTteProfile(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        String clientId = (String) body.get("clientId");
        String clientSecret = (String) body.get("clientSecret");
        String tokenUrl = (String) body.getOrDefault("tokenUrl",
            "https://tte-pool-prod.auth.eu-central-1.amazoncognito.com/oauth2/token");

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", "Le nom du profil est requis"
            ));
        }

        if ("default".equals(name)) {
            // Mettre à jour le profil par défaut
            tteProperties.getCognito().setClientId(clientId);
            tteProperties.getCognito().setClientSecret(clientSecret);
            if (tokenUrl != null) {
                tteProperties.getCognito().setTokenUrl(tokenUrl);
            }
        } else {
            // Ajouter/mettre à jour un profil nommé
            TTEProperties.CognitoProfile profile = TTEProperties.CognitoProfile.builder()
                .name(name)
                .description(description)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tokenUrl(tokenUrl)
                .build();
            tteProperties.addProfile(profile);
        }

        log.info("TTE profile '{}' saved: clientId={}...", name,
            clientId != null ? clientId.substring(0, Math.min(8, clientId.length())) : "null");

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "profile", name,
            "message", "Profil '" + name + "' sauvegardé avec succès"
        ));
    }

    /**
     * Supprime un profil Cognito.
     */
    @DeleteMapping("/tte/profiles/{name}")
    @Operation(summary = "Supprime un profil Cognito")
    public ResponseEntity<Map<String, Object>> deleteTteProfile(@PathVariable String name) {
        if ("default".equals(name)) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", "Impossible de supprimer le profil 'default'"
            ));
        }

        boolean removed = tteProperties.removeProfile(name);
        if (!removed) {
            return ResponseEntity.ok(Map.of(
                "ok", false,
                "error", "Profil '" + name + "' non trouvé"
            ));
        }

        // Si le profil supprimé était actif, revenir à default
        if (name.equals(tteProperties.getActiveProfile())) {
            tteProperties.setActiveProfile("default");
            cognitoTokenService.invalidateToken();
        }

        log.info("TTE profile '{}' deleted", name);
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "message", "Profil '" + name + "' supprimé"
        ));
    }

    /**
     * Change le profil Cognito actif.
     */
    @PostMapping("/tte/profiles/switch/{name}")
    @Operation(summary = "Change le profil Cognito actif")
    public ResponseEntity<Map<String, Object>> switchTteProfile(@PathVariable String name) {
        // Vérifier que le profil existe
        if (!"default".equals(name) && tteProperties.getProfile(name).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", "Profil '" + name + "' non trouvé"
            ));
        }

        String previousProfile = tteProperties.getActiveProfile();
        tteProperties.setActiveProfile(name);

        // Invalider le token actuel pour forcer un renouvellement avec le nouveau profil
        cognitoTokenService.invalidateToken();

        log.info("Switched TTE profile from '{}' to '{}'", previousProfile, name);

        // Essayer d'obtenir un nouveau token avec le nouveau profil
        try {
            cognitoTokenService.refreshToken();
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "previousProfile", previousProfile,
                "activeProfile", name,
                "tokenRefreshed", true,
                "message", "Profil changé vers '" + name + "' et token obtenu"
            ));
        } catch (Exception e) {
            log.error("Failed to refresh token after profile switch: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "previousProfile", previousProfile,
                "activeProfile", name,
                "tokenRefreshed", false,
                "tokenError", e.getMessage(),
                "message", "Profil changé vers '" + name + "' mais échec de l'obtention du token"
            ));
        }
    }
}
