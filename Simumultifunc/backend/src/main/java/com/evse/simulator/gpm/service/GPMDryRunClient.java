package com.evse.simulator.gpm.service;

import com.evse.simulator.gpm.config.GPMProperties;
import com.evse.simulator.gpm.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Client pour l'API TTE Dry-Run GPM.
 * Utilise l'authentification OAuth2 Cognito (client_credentials).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GPMDryRunClient {

    private final GPMProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Token cache
    private String cachedToken;
    private Instant tokenExpiry;
    private final ReentrantLock tokenLock = new ReentrantLock();

    // ══════════════════════════════════════════════════════════════
    // AUTHENTIFICATION OAUTH2 COGNITO
    // ══════════════════════════════════════════════════════════════

    /**
     * Obtient un token OAuth2 Cognito, utilisant le cache si valide.
     */
    public String getAccessToken() {
        tokenLock.lock();
        try {
            // Vérifier si le token en cache est encore valide (avec 5min de marge)
            if (cachedToken != null && tokenExpiry != null &&
                Instant.now().plusSeconds(300).isBefore(tokenExpiry)) {
                return cachedToken;
            }

            // Sinon, obtenir un nouveau token
            return refreshToken();
        } finally {
            tokenLock.unlock();
        }
    }

    private String refreshToken() {
        log.info("Requesting new OAuth2 token from Cognito: {}", properties.getTokenUrl());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                properties.getTokenUrl(), request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                cachedToken = json.get("access_token").asText();

                // Calculer l'expiration (expires_in en secondes)
                int expiresIn = json.has("expires_in") ? json.get("expires_in").asInt() : 3600;
                tokenExpiry = Instant.now().plusSeconds(expiresIn);

                log.info("OAuth2 Cognito token obtained, expires in {} seconds", expiresIn);
                return cachedToken;
            } else {
                throw new RuntimeException("Failed to obtain OAuth2 token: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error obtaining OAuth2 Cognito token: {}", e.getMessage());
            throw new RuntimeException("OAuth2 Cognito authentication failed", e);
        }
    }

    /**
     * Invalide le token en cache.
     */
    public void invalidateToken() {
        tokenLock.lock();
        try {
            cachedToken = null;
            tokenExpiry = null;
        } finally {
            tokenLock.unlock();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MÉTHODES API
    // ══════════════════════════════════════════════════════════════

    /**
     * Envoie un meter value au dry-run.
     * POST /qa/dry-run/meter-values
     *
     * Un appel par véhicule (format cdpDto)
     */
    public boolean sendMeterValues(DryRunMeterValueRequest request) {
        String url = properties.getBaseUrl() + "/qa/dry-run/meter-values";
        return executeWithRetry(() -> {
            HttpEntity<DryRunMeterValueRequest> entity = createRequestEntity(request);
            log.debug("Sending meter value: evseId={}, txId={}",
                request.getCdpDto().getEvseId(), request.getCdpDto().getTransactionId());
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.debug("MeterValues response: status={}", response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();
        }, "sendMeterValues");
    }

    /**
     * Envoie un tick de régulation.
     * POST /qa/dry-run/regulation-ticks
     */
    public boolean sendRegulationTick(DryRunRegulationTickRequest request) {
        String url = properties.getBaseUrl() + "/qa/dry-run/regulation-ticks";
        return executeWithRetry(() -> {
            HttpEntity<DryRunRegulationTickRequest> entity = createRequestEntity(request);
            log.debug("Sending regulation tick: rootId={}, dryRunId={}",
                request.getRootId(), request.getDryRunContext().getId());
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.debug("RegulationTick response: status={}", response.getStatusCode());
            return response.getStatusCode().is2xxSuccessful();
        }, "sendRegulationTick");
    }

    /**
     * Récupère les setpoints pour un node.
     * GET /qa/setpoints?nodeId={nodeId}&page=0&size=100&sort=creationDate,DESC
     */
    public SetpointResponse getSetpoints(String nodeId, String tickId) {
        String url = properties.getBaseUrl() + "/qa/setpoints?nodeId=" + nodeId +
            "&page=0&size=100&sort=creationDate,DESC";

        String finalUrl = url;
        return executeWithRetry(() -> {
            HttpEntity<?> entity = createRequestEntity(null);
            ResponseEntity<SetpointResponse> response = restTemplate.exchange(
                finalUrl, HttpMethod.GET, entity, SetpointResponse.class);
            SetpointResponse body = response.getBody();
            int count = (body != null && body.getSetpoints() != null) ? body.getSetpoints().size() : 0;
            log.debug("Setpoints retrieved for node {}: {} setpoints", nodeId, count);
            return body;
        }, "getSetpoints");
    }

    /**
     * Récupère le snapshot des energy nodes.
     * GET /qa/energy-node-snapshots?rootNodeId={rootNodeId}
     */
    public EnergyNodeSnapshotResponse getEnergyNodeSnapshots(String rootNodeId) {
        String url = properties.getBaseUrl() + "/qa/energy-node-snapshots?rootNodeId=" + rootNodeId;
        return executeWithRetry(() -> {
            HttpEntity<?> entity = createRequestEntity(null);
            ResponseEntity<EnergyNodeSnapshotResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, EnergyNodeSnapshotResponse.class);
            log.debug("EnergyNodeSnapshots retrieved for node {}", rootNodeId);
            return response.getBody();
        }, "getEnergyNodeSnapshots");
    }

    /**
     * Génère un ID de dry-run local.
     * L'API TTE n'a pas d'endpoint pour créer un dry-run,
     * le dryRunContext.id est juste passé avec chaque requête.
     */
    public String generateDryRunId(String prefix) {
        String id = prefix + "-" + System.currentTimeMillis();
        log.info("Generated local dry-run ID: {}", id);
        return id;
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private <T> HttpEntity<T> createRequestEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(getAccessToken());
        return new HttpEntity<>(body, headers);
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> action, String operationName) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < properties.getRetryCount()) {
            attempts++;
            try {
                return action.get();
            } catch (HttpClientErrorException.Unauthorized e) {
                log.warn("{}: Token expired (401), refreshing...", operationName);
                invalidateToken();
                // Continue to retry with new token
            } catch (HttpClientErrorException.Forbidden e) {
                log.error("{}: Access denied (403) - {}", operationName, e.getMessage());
                log.error("{}: Response body: {}", operationName, e.getResponseBodyAsString());
                throw e;
            } catch (HttpClientErrorException e) {
                lastException = e;
                log.error("{}: HTTP error {} - {}", operationName, e.getStatusCode(), e.getMessage());
                log.error("{}: Response body: {}", operationName, e.getResponseBodyAsString());

                if (attempts < properties.getRetryCount()) {
                    try {
                        Thread.sleep(properties.getRetryDelay());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("{}: Attempt {}/{} failed: {} - {}",
                    operationName, attempts, properties.getRetryCount(),
                    e.getClass().getSimpleName(), e.getMessage());

                if (attempts < properties.getRetryCount()) {
                    try {
                        Thread.sleep(properties.getRetryDelay());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry", ie);
                    }
                }
            }
        }

        String errorDetail = lastException != null ?
            lastException.getClass().getSimpleName() + ": " + lastException.getMessage() : "unknown";
        log.error("{}: All {} attempts failed. Last error: {}", operationName, properties.getRetryCount(), errorDetail);
        throw new RuntimeException(operationName + " failed after " + properties.getRetryCount() +
            " attempts: " + errorDetail, lastException);
    }

    /**
     * Vérifie si le client est configuré et activé.
     */
    public boolean isEnabled() {
        return properties.isEnabled() &&
               properties.getClientId() != null && !properties.getClientId().isEmpty() &&
               properties.getClientSecret() != null && !properties.getClientSecret().isEmpty();
    }

    /**
     * Teste la connexion à l'API (obtient un token).
     */
    public boolean testConnection() {
        if (!isEnabled()) {
            log.warn("GPM Dry-Run client not configured");
            return false;
        }
        try {
            getAccessToken();
            log.info("GPM API connection test successful");
            return true;
        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());
            return false;
        }
    }
}
