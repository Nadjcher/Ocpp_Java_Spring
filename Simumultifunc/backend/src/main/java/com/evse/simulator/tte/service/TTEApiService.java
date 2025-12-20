package com.evse.simulator.tte.service;

import com.evse.simulator.tte.config.TTEProperties;
import com.evse.simulator.tte.model.ChargingProfileRequest;
import com.evse.simulator.tte.model.ChargingProfileResponse;
import com.evse.simulator.tte.model.PricingData;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Service pour les appels API TTE.
 * Gère automatiquement l'authentification via le token Cognito.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TTEApiService {

    private final TTEProperties properties;
    private final CognitoTokenService tokenService;
    private final RestTemplate restTemplate;

    // =========================================================================
    // Pricing API
    // =========================================================================

    /**
     * Récupère les informations de tarification pour une transaction.
     * Utilise l'endpoint /evportal/api/tx pour lister les transactions et filtre par transactionId.
     * L'URL TTE est sélectionnée automatiquement selon l'URL CSMS de la session (test ou pp).
     *
     * @param chargePointId OCPP ID du Charge Point
     * @param transactionId ID de la transaction OCPP
     * @param csmsUrl       URL WebSocket OCPP de la session (pour déterminer l'environnement)
     * @return Données de tarification
     */
    public PricingData getTransactionPricing(String chargePointId, Integer transactionId, String csmsUrl) {
        // Sélectionner l'URL TTE selon l'environnement de la session
        String baseUrl = properties.getApi().getUrlForEnvironment(csmsUrl);
        String url = buildUrlWithBase(baseUrl, "/evportal/api/tx?limit=50&skip=0");

        log.info("Getting transactions from TTE API (env detected from: {}) -> {}",
                 csmsUrl != null ? csmsUrl.substring(0, Math.min(50, csmsUrl.length())) + "..." : "null",
                 baseUrl);
        log.debug("TTE API URL: {}", url);

        try {
            ResponseEntity<JsonNode> response = executeWithRetry(
                    url,
                    HttpMethod.GET,
                    null,
                    JsonNode.class
            );

            JsonNode body = response.getBody();
            if (body == null) {
                log.warn("Empty response from TTE API");
                return null;
            }

            // Le response peut être un array ou un objet avec un champ "data" ou "transactions"
            JsonNode transactions = body;
            if (body.has("data")) {
                transactions = body.get("data");
            } else if (body.has("transactions")) {
                transactions = body.get("transactions");
            }

            if (!transactions.isArray()) {
                log.warn("Unexpected TTE API response format: {}", body.toString().substring(0, Math.min(200, body.toString().length())));
                return null;
            }

            // Chercher la transaction correspondante
            for (JsonNode tx : transactions) {
                Integer txId = tx.has("transactionId") ? tx.get("transactionId").asInt() : null;
                String cpId = tx.has("chargePointId") ? tx.get("chargePointId").asText() :
                              (tx.has("cpId") ? tx.get("cpId").asText() : null);

                // Match par transactionId (et optionnellement cpId)
                if (txId != null && txId.equals(transactionId)) {
                    log.info("Found transaction {} in TTE API response", transactionId);
                    return mapToPricingData(tx, chargePointId, transactionId);
                }
            }

            log.warn("Transaction {} not found in TTE API response ({} transactions checked)",
                     transactionId, transactions.size());
            return null;

        } catch (Exception e) {
            log.error("Failed to get pricing for CP {}, transactionId {}: {}",
                    chargePointId, transactionId, e.getMessage());
            throw new RuntimeException("Failed to get transaction pricing", e);
        }
    }

    /**
     * Map une transaction JSON de l'API TTE vers PricingData
     */
    private PricingData mapToPricingData(JsonNode tx, String chargePointId, Integer transactionId) {
        PricingData pricing = new PricingData();
        pricing.setChargePointId(chargePointId);
        pricing.setTransactionId(transactionId);

        // Map les champs communs (avec différents noms possibles)
        if (tx.has("totalPrice")) {
            pricing.setTotalPrice(new java.math.BigDecimal(tx.get("totalPrice").asText("0")));
        } else if (tx.has("price")) {
            pricing.setTotalPrice(new java.math.BigDecimal(tx.get("price").asText("0")));
        } else if (tx.has("amount")) {
            pricing.setTotalPrice(new java.math.BigDecimal(tx.get("amount").asText("0")));
        }

        if (tx.has("currency")) {
            pricing.setCurrency(tx.get("currency").asText());
        }

        if (tx.has("energyDelivered")) {
            pricing.setEnergyDelivered(new java.math.BigDecimal(tx.get("energyDelivered").asText("0")));
        } else if (tx.has("energy")) {
            pricing.setEnergyDelivered(new java.math.BigDecimal(tx.get("energy").asText("0")));
        } else if (tx.has("kWh")) {
            pricing.setEnergyDelivered(new java.math.BigDecimal(tx.get("kWh").asText("0")));
        }

        if (tx.has("pricePerKwh")) {
            pricing.setPricePerKwh(new java.math.BigDecimal(tx.get("pricePerKwh").asText("0")));
        } else if (tx.has("unitPrice")) {
            pricing.setPricePerKwh(new java.math.BigDecimal(tx.get("unitPrice").asText("0")));
        }

        if (tx.has("duration")) {
            pricing.setDurationSeconds(tx.get("duration").asLong());
        } else if (tx.has("durationSeconds")) {
            pricing.setDurationSeconds(tx.get("durationSeconds").asLong());
        }

        log.debug("Mapped transaction {} to PricingData: totalPrice={}, energy={}",
                  transactionId, pricing.getTotalPrice(), pricing.getEnergyDelivered());

        return pricing;
    }

    /**
     * Récupère les informations de tarification pour une session (par sessionId interne).
     *
     * @param sessionId ID de la session (interne)
     * @return Données de tarification
     */
    public PricingData getSessionPricing(String sessionId) {
        String url = buildUrl("/v1/sessions/" + sessionId + "/pricing");
        log.debug("Getting pricing for session: {}", sessionId);

        try {
            ResponseEntity<PricingData> response = executeWithRetry(
                    url,
                    HttpMethod.GET,
                    null,
                    PricingData.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get pricing for session {}: {}", sessionId, e.getMessage());
            throw new RuntimeException("Failed to get session pricing", e);
        }
    }

    // =========================================================================
    // Smart Charging API
    // =========================================================================

    /**
     * Envoie un profil de charge au CSMS.
     *
     * @param cpId    ID du Charge Point
     * @param profile Profil de charge
     * @return Réponse du CSMS
     */
    public ChargingProfileResponse sendChargingProfile(String cpId, ChargingProfileRequest profile) {
        String url = buildUrl("/v1/smart-charging/profiles");
        log.debug("Sending charging profile for CP: {}", cpId);

        profile.setChargePointId(cpId);

        try {
            ResponseEntity<ChargingProfileResponse> response = executeWithRetry(
                    url,
                    HttpMethod.POST,
                    profile,
                    ChargingProfileResponse.class
            );
            log.info("Charging profile sent successfully for CP: {}", cpId);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to send charging profile for CP {}: {}", cpId, e.getMessage());
            throw new RuntimeException("Failed to send charging profile", e);
        }
    }

    /**
     * Récupère les profils de charge actifs pour un Charge Point.
     *
     * @param cpId ID du Charge Point
     * @return Liste des profils actifs
     */
    public List<ChargingProfileResponse> getActiveProfiles(String cpId) {
        String url = buildUrl("/v1/smart-charging/profiles?chargePointId=" + cpId);
        log.debug("Getting active profiles for CP: {}", cpId);

        try {
            ResponseEntity<ChargingProfileResponse[]> response = executeWithRetry(
                    url,
                    HttpMethod.GET,
                    null,
                    ChargingProfileResponse[].class
            );

            ChargingProfileResponse[] profiles = response.getBody();
            return profiles != null ? List.of(profiles) : List.of();
        } catch (Exception e) {
            log.error("Failed to get active profiles for CP {}: {}", cpId, e.getMessage());
            throw new RuntimeException("Failed to get active profiles", e);
        }
    }

    /**
     * Supprime un profil de charge.
     *
     * @param cpId      ID du Charge Point
     * @param profileId ID du profil à supprimer
     */
    public void clearChargingProfile(String cpId, String profileId) {
        String url = buildUrl("/v1/smart-charging/profiles/" + profileId + "?chargePointId=" + cpId);
        log.debug("Clearing charging profile {} for CP: {}", profileId, cpId);

        try {
            executeWithRetry(url, HttpMethod.DELETE, null, Void.class);
            log.info("Charging profile {} cleared for CP: {}", profileId, cpId);
        } catch (Exception e) {
            log.error("Failed to clear charging profile {} for CP {}: {}", profileId, cpId, e.getMessage());
            throw new RuntimeException("Failed to clear charging profile", e);
        }
    }

    // =========================================================================
    // Generic API call
    // =========================================================================

    /**
     * Effectue un appel API générique avec le token.
     *
     * @param endpoint Endpoint relatif (ex: /v1/sessions)
     * @param method   Méthode HTTP
     * @param body     Corps de la requête (peut être null)
     * @return Réponse JSON brute
     */
    public JsonNode callApi(String endpoint, HttpMethod method, Object body) {
        String url = buildUrl(endpoint);

        try {
            ResponseEntity<JsonNode> response = executeWithRetry(url, method, body, JsonNode.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("API call failed for {}: {}", endpoint, e.getMessage());
            throw new RuntimeException("API call failed", e);
        }
    }

    // =========================================================================
    // Internal methods
    // =========================================================================

    /**
     * Exécute une requête avec retry automatique sur erreur 401.
     */
    private <T> ResponseEntity<T> executeWithRetry(String url, HttpMethod method, Object body, Class<T> responseType) {
        try {
            return execute(url, method, body, responseType);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Received 401, refreshing token and retrying...");
            tokenService.refreshToken();
            return execute(url, method, body, responseType);
        }
    }

    /**
     * Exécute une requête HTTP avec le token Bearer.
     */
    private <T> ResponseEntity<T> execute(String url, HttpMethod method, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.getValidToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);

        log.debug("TTE API {} {}", method, url);

        try {
            return restTemplate.exchange(url, method, entity, responseType);
        } catch (RestClientException e) {
            log.error("TTE API error: {} {} - {}", method, url, e.getMessage());
            throw e;
        }
    }

    /**
     * Construit l'URL complète avec la base URL par défaut.
     */
    private String buildUrl(String endpoint) {
        return buildUrlWithBase(properties.getApi().getBaseUrl(), endpoint);
    }

    /**
     * Construit l'URL complète avec une base URL spécifique.
     */
    private String buildUrlWithBase(String baseUrl, String endpoint) {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = properties.getApi().getTestUrl(); // Default to test
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        return baseUrl + endpoint;
    }

    /**
     * Vérifie si le service est disponible.
     */
    public boolean isAvailable() {
        return tokenService.isConfigured();
    }

    /**
     * Retourne les informations de santé du service.
     */
    public Map<String, Object> getHealthInfo() {
        return Map.of(
                "available", isAvailable(),
                "tokenInfo", tokenService.getTokenInfo(),
                "apiBaseUrl", properties.getApi().getBaseUrl()
        );
    }
}
