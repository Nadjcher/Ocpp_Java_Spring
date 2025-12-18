package com.evse.simulator.controller;

import com.evse.simulator.config.OcppProperties;
import com.evse.simulator.config.SessionDefaults;
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
}
