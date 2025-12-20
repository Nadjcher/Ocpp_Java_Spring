package com.evse.simulator.scheduler.service;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.scheduler.model.ActionConfig;
import com.evse.simulator.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Exécuteur d'actions pour les tâches planifiées.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaskActionExecutor {

    private final SessionService sessionService;
    private final RestTemplate restTemplate;

    /**
     * Exécute une action selon son type.
     *
     * @param actionType Type d'action
     * @param config Configuration de l'action
     * @return Résultat de l'exécution
     */
    public String execute(String actionType, ActionConfig config) {
        log.info("Executing action: {} with config: {}", actionType, config);

        return switch (actionType) {
            case "session" -> executeSessionAction(config);
            case "tnr" -> executeTnrScenario(config);
            case "ocpi" -> executeOcpiTest(config);
            case "http" -> executeHttpRequest(config);
            default -> throw new IllegalArgumentException("Unknown action type: " + actionType);
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // SESSION ACTIONS
    // ═══════════════════════════════════════════════════════════════════

    private String executeSessionAction(ActionConfig config) {
        String action = config.getSessionAction();
        if (action == null) {
            throw new IllegalArgumentException("sessionAction is required");
        }

        log.info("Executing session action: {}", action);

        return switch (action) {
            case "create" -> {
                // Récupérer l'URL WebSocket effective
                String wsUrl = config.getEffectiveWsUrl();
                if (wsUrl == null || wsUrl.isBlank()) {
                    throw new IllegalArgumentException("wsUrl is required for session creation");
                }

                String cpId = config.getCpId() != null ? config.getCpId() : "SCHEDULED-CP-001";

                Session session = Session.builder()
                    .title(config.getTitle() != null ? config.getTitle() : "Scheduled: " + cpId)
                    .url(wsUrl)
                    .cpId(cpId)
                    .connectorId(config.getConnectorId() != null ? config.getConnectorId() : 1)
                    .vehicleProfile(config.getVehicleId() != null ? config.getVehicleId() : "GENERIC")
                    .chargerType(parseChargerType(config.getChargerType()))
                    .phaseType(config.getPhaseType() != null ? config.getPhaseType() : "AC_TRI")
                    .soc(config.getInitialSoc() != null ? config.getInitialSoc() : 20)
                    .targetSoc(config.getTargetSoc() != null ? config.getTargetSoc() : 80)
                    .idTag(config.getIdTag() != null ? config.getIdTag() : "SCHEDULER")
                    .bearerToken(config.getBearerToken())
                    .maxPowerKw(config.getMaxPowerKw() != null ? config.getMaxPowerKw() : 22.0)
                    .maxCurrentA(config.getMaxCurrentA() != null ? config.getMaxCurrentA() : 32.0)
                    .heartbeatInterval(config.getHeartbeatInterval() != null ? config.getHeartbeatInterval() : 30)
                    .meterValuesInterval(config.getMeterValuesInterval() != null ? config.getMeterValuesInterval() : 10)
                    .vendor(config.getVendor() != null ? config.getVendor() : "EVSE Simulator")
                    .model(config.getModel() != null ? config.getModel() : "SimuCP-1")
                    .serialNumber(config.getSerialNumber())
                    .firmwareVersion(config.getFirmwareVersion() != null ? config.getFirmwareVersion() : "1.0.0")
                    .ocppVersion(config.getOcppVersion() != null ? config.getOcppVersion() : "1.6")
                    .build();

                Session created = sessionService.createSession(session);
                log.info("Session created with wsUrl: {} cpId: {}", wsUrl, cpId);
                yield "Session created: " + created.getId();
            }
            case "start" -> {
                requireSessionId(config);
                Session session = sessionService.getSession(config.getSessionId());
                sessionService.updateState(config.getSessionId(),
                    com.evse.simulator.model.enums.SessionState.CHARGING);
                yield "Charging started: " + session.getId();
            }
            case "stop" -> {
                requireSessionId(config);
                Session session = sessionService.getSession(config.getSessionId());
                sessionService.updateState(config.getSessionId(),
                    com.evse.simulator.model.enums.SessionState.FINISHING);
                yield "Charging stopped: " + session.getId();
            }
            case "plug" -> {
                requireSessionId(config);
                Session session = sessionService.getSession(config.getSessionId());
                sessionService.updateState(config.getSessionId(),
                    com.evse.simulator.model.enums.SessionState.PREPARING);
                yield "Plugged: " + session.getId();
            }
            case "unplug" -> {
                requireSessionId(config);
                Session session = sessionService.getSession(config.getSessionId());
                sessionService.updateState(config.getSessionId(),
                    com.evse.simulator.model.enums.SessionState.AVAILABLE);
                yield "Unplugged: " + session.getId();
            }
            case "delete" -> {
                requireSessionId(config);
                sessionService.deleteSession(config.getSessionId());
                yield "Session deleted: " + config.getSessionId();
            }
            default -> throw new IllegalArgumentException("Unknown session action: " + action);
        };
    }

    private void requireSessionId(ActionConfig config) {
        if (config.getSessionId() == null || config.getSessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId is required for this action");
        }
    }

    private ChargerType parseChargerType(String type) {
        if (type == null) return ChargerType.AC_TRI;
        try {
            return ChargerType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            return ChargerType.AC_TRI;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // TNR SCENARIOS
    // ═══════════════════════════════════════════════════════════════════

    private String executeTnrScenario(ActionConfig config) {
        if (config.getScenarioId() == null) {
            throw new IllegalArgumentException("scenarioId is required");
        }

        log.info("Executing TNR scenario: {} with wsUrl: {}", config.getScenarioId(), config.getTnrWsUrl());

        // Construire la configuration TNR
        Map<String, Object> tnrConfig = new java.util.HashMap<>();
        tnrConfig.put("suiteName", "Scheduled-" + config.getScenarioId());
        tnrConfig.put("scenarioIds", java.util.List.of(config.getScenarioId()));

        // URL WebSocket pour TNR
        if (config.getTnrWsUrl() != null && !config.getTnrWsUrl().isBlank()) {
            tnrConfig.put("wsUrl", config.getTnrWsUrl());
        }

        // Options de configuration
        if (config.getRepeatCount() != null) {
            tnrConfig.put("retryCount", config.getRepeatCount());
        }
        if (config.getContinueOnError() != null) {
            tnrConfig.put("stopOnFailure", !config.getContinueOnError());
        }
        if (config.getGlobalTimeoutMs() != null) {
            tnrConfig.put("timeoutMs", config.getGlobalTimeoutMs());
        }

        // Variables du contexte
        if (config.getTnrVariables() != null && !config.getTnrVariables().isEmpty()) {
            tnrConfig.put("variables", config.getTnrVariables());
        }

        // Paramètres du scénario
        if (config.getScenarioParams() != null && !config.getScenarioParams().isEmpty()) {
            tnrConfig.put("parameters", config.getScenarioParams());
        }

        // Rapports
        if (Boolean.TRUE.equals(config.getGenerateReports())) {
            tnrConfig.put("generateReports", true);
            if (config.getReportFormats() != null) {
                tnrConfig.put("reportFormats", config.getReportFormats());
            }
        }

        // Appeler l'API TNR interne
        try {
            String url = "http://localhost:8877/api/tnr/engine/run/" + config.getScenarioId();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(tnrConfig, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("TNR scenario {} completed successfully", config.getScenarioId());
                return "TNR scenario executed: " + config.getScenarioId() + " - " + response.getBody();
            } else {
                throw new RuntimeException("TNR failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("TNR scenario failed", e);
            throw new RuntimeException("TNR scenario failed: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // OCPI TESTS
    // ═══════════════════════════════════════════════════════════════════

    private String executeOcpiTest(ActionConfig config) {
        if (config.getTestId() == null) {
            throw new IllegalArgumentException("testId is required");
        }

        log.info("Executing OCPI test: {} for partner {}", config.getTestId(), config.getPartnerId());

        // Appeler l'API OCPI interne
        try {
            String url = "http://localhost:8877/api/ocpi/test";
            Map<String, String> body = Map.of(
                "partnerId", config.getPartnerId() != null ? config.getPartnerId() : "",
                "testId", config.getTestId(),
                "environment", config.getEnvironment() != null ? config.getEnvironment() : "test"
            );

            ResponseEntity<String> response = restTemplate.postForEntity(url, body, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return "OCPI test executed: " + config.getTestId();
            } else {
                throw new RuntimeException("OCPI test failed: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("OCPI test failed", e);
            throw new RuntimeException("OCPI test failed: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HTTP REQUESTS
    // ═══════════════════════════════════════════════════════════════════

    private String executeHttpRequest(ActionConfig config) {
        if (config.getHttpUrl() == null || config.getHttpUrl().isBlank()) {
            throw new IllegalArgumentException("httpUrl is required");
        }

        String method = config.getHttpMethod() != null ? config.getHttpMethod() : "GET";
        log.info("Executing HTTP {} {}", method, config.getHttpUrl());

        try {
            HttpHeaders headers = new HttpHeaders();
            if (config.getHttpHeaders() != null) {
                config.getHttpHeaders().forEach(headers::set);
            }

            HttpEntity<String> entity = new HttpEntity<>(config.getHttpBody(), headers);

            ResponseEntity<String> response = switch (method.toUpperCase()) {
                case "GET" -> restTemplate.exchange(
                    config.getHttpUrl(), HttpMethod.GET, entity, String.class);
                case "POST" -> restTemplate.exchange(
                    config.getHttpUrl(), HttpMethod.POST, entity, String.class);
                case "PUT" -> restTemplate.exchange(
                    config.getHttpUrl(), HttpMethod.PUT, entity, String.class);
                case "DELETE" -> restTemplate.exchange(
                    config.getHttpUrl(), HttpMethod.DELETE, entity, String.class);
                default -> throw new IllegalArgumentException("Unknown HTTP method: " + method);
            };

            int status = response.getStatusCode().value();
            String body = response.getBody();
            String preview = body != null && body.length() > 200 ? body.substring(0, 200) + "..." : body;

            return String.format("HTTP %d - %s", status, preview);

        } catch (Exception e) {
            log.error("HTTP request failed", e);
            throw new RuntimeException("HTTP request failed: " + e.getMessage(), e);
        }
    }
}
