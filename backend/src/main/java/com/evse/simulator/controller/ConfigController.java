package com.evse.simulator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Contrôleur REST pour les configurations globales.
 */
@RestController
@RequestMapping("/api/config")
@Tag(name = "Config", description = "Configuration globale de l'application")
@Slf4j
@CrossOrigin
public class ConfigController {

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
}
