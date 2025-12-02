package com.evse.simulator.websocket;

import com.evse.simulator.domain.service.OCPPService;
import com.evse.simulator.model.ChargingProfile;
import com.evse.simulator.model.ChargingProfile.*;
import com.evse.simulator.model.LogEntry;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;
import com.evse.simulator.service.ChargingProfileManager;
import com.evse.simulator.service.ChargingProfileManager.EffectiveLimit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client WebSocket OCPP pour la communication avec le CSMS.
 * <p>
 * Gère la connexion WebSocket, la réception et l'envoi de messages OCPP-J.
 * </p>
 */
@Slf4j
public class OCPPWebSocketClient extends WebSocketClient {

    private final Session session;
    private final OCPPService ocppService;
    private final ObjectMapper objectMapper;
    private final ChargingProfileManager chargingProfileManager;

    public OCPPWebSocketClient(URI serverUri, Session session, OCPPService ocppService,
                               ChargingProfileManager chargingProfileManager) {
        super(serverUri);
        this.session = session;
        this.ocppService = ocppService;
        this.objectMapper = new ObjectMapper();
        this.chargingProfileManager = chargingProfileManager;

        // Configurer SSL pour les connexions wss://
        if ("wss".equalsIgnoreCase(serverUri.getScheme())) {
            try {
                SSLSocketFactory factory = createTrustAllSSLFactory();
                setSocketFactory(factory);
                log.info("SSL configured for wss:// connection to {}", serverUri.getHost());
            } catch (Exception e) {
                log.error("Failed to configure SSL for WebSocket: {}", e.getMessage());
            }
        }
    }

    /**
     * Crée une SSLSocketFactory qui accepte tous les certificats.
     * Note: À utiliser uniquement en développement/test.
     */
    private static SSLSocketFactory createTrustAllSSLFactory() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        // Trust all clients
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        // Trust all servers
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("WebSocket connected for session {} to {}",
                session.getId(), getURI());
        session.setConnected(true);
        session.setLastConnected(java.time.LocalDateTime.now());
    }

    @Override
    public void onMessage(String message) {
        log.debug("Session {} received: {}", session.getId(), message);

        try {
            // Parser le message OCPP-J [messageType, messageId, ...]
            List<Object> ocppMessage = objectMapper.readValue(message,
                    new TypeReference<List<Object>>() {});

            if (ocppMessage.isEmpty()) {
                log.warn("Empty OCPP message received");
                return;
            }

            int messageType = ((Number) ocppMessage.get(0)).intValue();
            String messageId = (String) ocppMessage.get(1);

            switch (messageType) {
                case 2 -> handleCall(messageId, ocppMessage);
                case 3 -> handleCallResult(messageId, ocppMessage);
                case 4 -> handleCallError(messageId, ocppMessage);
                default -> log.warn("Unknown message type: {}", messageType);
            }

        } catch (Exception e) {
            log.error("Failed to parse OCPP message: {}", message, e);
        }
    }

    /**
     * Traite un message CALL entrant (du CSMS vers le CP).
     */
    private void handleCall(String messageId, List<Object> ocppMessage) {
        String action = (String) ocppMessage.get(2);
        Map<String, Object> payload = ocppMessage.size() > 3 ?
                (Map<String, Object>) ocppMessage.get(3) : new HashMap<>();

        log.info("Session {} received CALL: {} [{}]",
                session.getId(), action, messageId);

        // Ajouter un log visible pour le CALL reçu
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            session.addLog(LogEntry.info("OCPP", "<< CALL " + action + " " + payloadJson));
        } catch (Exception e) {
            session.addLog(LogEntry.info("OCPP", "<< CALL " + action));
        }

        // Traiter selon l'action
        Map<String, Object> response = processIncomingCall(action, payload);

        // Envoyer la réponse
        sendCallResult(messageId, response);
    }

    /**
     * Traite les actions entrantes du CSMS.
     */
    private Map<String, Object> processIncomingCall(String action, Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        switch (action) {
            case "RemoteStartTransaction" -> {
                // Accepter le démarrage distant
                String idTag = (String) payload.get("idTag");
                session.setIdTag(idTag);
                response.put("status", "Accepted");
                // Déclencher le démarrage de transaction
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        ocppService.sendStartTransaction(session.getId());
                    } catch (Exception e) {
                        log.error("Failed to start transaction", e);
                    }
                }).start();
            }

            case "RemoteStopTransaction" -> {
                Integer transactionId = (Integer) payload.get("transactionId");
                if (session.getTransactionId() != null &&
                        session.getTransactionId().equals(String.valueOf(transactionId))) {
                    response.put("status", "Accepted");
                    // Déclencher l'arrêt de transaction
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            ocppService.sendStopTransaction(session.getId());
                        } catch (Exception e) {
                            log.error("Failed to stop transaction", e);
                        }
                    }).start();
                } else {
                    response.put("status", "Rejected");
                }
            }

            case "SetChargingProfile" -> {
                response = handleSetChargingProfile(payload);
            }

            case "ClearChargingProfile" -> {
                response = handleClearChargingProfile(payload);
            }

            case "GetCompositeSchedule" -> {
                response = handleGetCompositeSchedule(payload);
            }

            case "GetConfiguration" -> {
                List<String> keys = (List<String>) payload.get("key");
                response.put("configurationKey", getConfiguration(keys));
                response.put("unknownKey", new java.util.ArrayList<>());
            }

            case "ChangeConfiguration" -> {
                String key = (String) payload.get("key");
                String value = (String) payload.get("value");
                response.put("status", setConfiguration(key, value));
            }

            case "ChangeAvailability" -> {
                Integer connectorId = (Integer) payload.get("connectorId");
                String type = (String) payload.get("type");

                if ("Inoperative".equals(type)) {
                    session.setState(SessionState.UNAVAILABLE);
                } else {
                    session.setState(SessionState.AVAILABLE);
                }
                response.put("status", "Accepted");
            }

            case "Reset" -> {
                String type = (String) payload.get("type");
                response.put("status", "Accepted");

                // Simuler le reset
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        if ("Hard".equals(type)) {
                            ocppService.disconnect(session.getId());
                            Thread.sleep(2000);
                            ocppService.connect(session.getId());
                        } else {
                            ocppService.sendBootNotification(session.getId());
                        }
                    } catch (Exception e) {
                        log.error("Reset failed", e);
                    }
                }).start();
            }

            case "UnlockConnector" -> {
                response.put("status", "Unlocked");
            }

            case "TriggerMessage" -> {
                String requestedMessage = (String) payload.get("requestedMessage");
                response.put("status", handleTriggerMessage(requestedMessage));
            }

            case "DataTransfer" -> {
                response.put("status", "Accepted");
                response.put("data", "{}");
            }

            default -> {
                log.warn("Unhandled action: {}", action);
                response.put("status", "NotImplemented");
            }
        }

        return response;
    }

    /**
     * Récupère la configuration demandée.
     */
    private List<Map<String, Object>> getConfiguration(List<String> keys) {
        List<Map<String, Object>> config = new java.util.ArrayList<>();

        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("key", "HeartbeatInterval");
        heartbeat.put("readonly", false);
        heartbeat.put("value", String.valueOf(session.getHeartbeatInterval()));
        config.add(heartbeat);

        Map<String, Object> meterValues = new HashMap<>();
        meterValues.put("key", "MeterValueSampleInterval");
        meterValues.put("readonly", false);
        meterValues.put("value", String.valueOf(session.getMeterValuesInterval()));
        config.add(meterValues);

        Map<String, Object> connectors = new HashMap<>();
        connectors.put("key", "NumberOfConnectors");
        connectors.put("readonly", true);
        connectors.put("value", "1");
        config.add(connectors);

        return config;
    }

    /**
     * Modifie une configuration.
     */
    private String setConfiguration(String key, String value) {
        switch (key) {
            case "HeartbeatInterval" -> {
                session.setHeartbeatInterval(Integer.parseInt(value));
                return "Accepted";
            }
            case "MeterValueSampleInterval" -> {
                session.setMeterValuesInterval(Integer.parseInt(value));
                return "Accepted";
            }
            default -> {
                return "NotSupported";
            }
        }
    }

    /**
     * Traite un TriggerMessage.
     */
    private String handleTriggerMessage(String requestedMessage) {
        switch (requestedMessage) {
            case "BootNotification" -> {
                new Thread(() -> ocppService.sendBootNotification(session.getId())).start();
                return "Accepted";
            }
            case "Heartbeat" -> {
                new Thread(() -> ocppService.sendHeartbeat(session.getId())).start();
                return "Accepted";
            }
            case "MeterValues" -> {
                new Thread(() -> ocppService.sendMeterValues(session.getId())).start();
                return "Accepted";
            }
            case "StatusNotification" -> {
                new Thread(() -> ocppService.sendStatusNotification(session.getId(),
                        com.evse.simulator.model.enums.ConnectorStatus.fromSessionState(session.getState()))).start();
                return "Accepted";
            }
            default -> {
                return "NotImplemented";
            }
        }
    }

    /**
     * Traite un message CALLRESULT entrant.
     */
    private void handleCallResult(String messageId, List<Object> ocppMessage) {
        Map<String, Object> payload = ocppMessage.size() > 2 ?
                (Map<String, Object>) ocppMessage.get(2) : new HashMap<>();

        log.debug("Session {} received CALLRESULT [{}]", session.getId(), messageId);

        ocppService.handleCallResult(session.getId(), messageId, payload);
    }

    /**
     * Traite un message CALLERROR entrant.
     */
    private void handleCallError(String messageId, List<Object> ocppMessage) {
        String errorCode = (String) ocppMessage.get(2);
        String errorDescription = ocppMessage.size() > 3 ? (String) ocppMessage.get(3) : "";

        log.warn("Session {} received CALLERROR [{}]: {} - {}",
                session.getId(), messageId, errorCode, errorDescription);

        ocppService.handleCallError(session.getId(), messageId, errorCode, errorDescription);
    }

    /**
     * Envoie un CALLRESULT.
     */
    private void sendCallResult(String messageId, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String response = String.format("[3,\"%s\",%s]",
                    messageId,
                    payloadJson);

            send(response);
            log.debug("Session {} sent CALLRESULT [{}]", session.getId(), messageId);

            // Ajouter un log visible pour la réponse envoyée
            session.addLog(LogEntry.info("OCPP", ">> RESULT " + payloadJson));
        } catch (Exception e) {
            log.error("Failed to send CALLRESULT", e);
            session.addLog(LogEntry.error("OCPP", "!! Failed to send CALLRESULT: " + e.getMessage()));
        }
    }

    // =========================================================================
    // Smart Charging Handlers
    // =========================================================================

    /**
     * Traite SetChargingProfile selon OCPP 1.6.
     */
    private Map<String, Object> handleSetChargingProfile(Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        try {
            Integer connectorId = (Integer) payload.get("connectorId");
            Map<String, Object> csChargingProfiles = (Map<String, Object>) payload.get("csChargingProfiles");

            if (csChargingProfiles == null) {
                log.warn("[SCP] SetChargingProfile rejected: missing csChargingProfiles");
                response.put("status", "Rejected");
                return response;
            }

            // Parser le profil
            ChargingProfile profile = parseChargingProfile(csChargingProfiles);

            if (profile == null) {
                log.warn("[SCP] SetChargingProfile rejected: failed to parse profile");
                response.put("status", "Rejected");
                return response;
            }

            // Stocker le profil
            chargingProfileManager.setChargingProfile(session.getId(), connectorId, profile);

            // Calculer et appliquer la nouvelle limite effective
            EffectiveLimit effectiveLimit = chargingProfileManager.getEffectiveLimit(
                    session.getId(),
                    connectorId,
                    session.getPhaseType(),
                    session.getVoltage()
            );

            if (effectiveLimit.hasLimit()) {
                session.setScpLimitKw(effectiveLimit.getLimitKw());
                session.setScpLimitA(effectiveLimit.limitRaw());
                session.setScpProfileId(effectiveLimit.profileId());
                session.setScpPurpose(effectiveLimit.source() != null ? effectiveLimit.source().getValue() : null);
                session.setScpStackLevel(effectiveLimit.stackLevel());

                if (effectiveLimit.nextPeriod() != null) {
                    session.setScpNextPeriodSeconds(effectiveLimit.nextPeriod().secondsUntilStart());
                    session.setScpNextLimitKw(effectiveLimit.nextPeriod().limit() / 1000.0);
                }

                session.addLog(LogEntry.info("SCP",
                        String.format("Limite appliquée: %.1f kW (%.1f A) - Source: %s #%d",
                                effectiveLimit.getLimitKw(),
                                effectiveLimit.limitRaw(),
                                effectiveLimit.source() != null ? effectiveLimit.source().getValue() : "N/A",
                                effectiveLimit.profileId())));
            }

            log.info("[SCP] SetChargingProfile accepted: session={}, connector={}, profile={}, effectiveLimit={} kW",
                    session.getId(), connectorId, profile.getChargingProfileId(),
                    effectiveLimit.hasLimit() ? effectiveLimit.getLimitKw() : "unlimited");

            response.put("status", "Accepted");

        } catch (Exception e) {
            log.error("[SCP] SetChargingProfile failed", e);
            session.addLog(LogEntry.error("SCP", "SetChargingProfile failed: " + e.getMessage()));
            response.put("status", "Rejected");
        }

        return response;
    }

    /**
     * Traite ClearChargingProfile selon OCPP 1.6.
     */
    private Map<String, Object> handleClearChargingProfile(Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        try {
            Integer id = (Integer) payload.get("id");
            Integer connectorId = (Integer) payload.get("connectorId");
            String chargingProfilePurpose = (String) payload.get("chargingProfilePurpose");
            Integer stackLevel = (Integer) payload.get("stackLevel");

            boolean removed = chargingProfileManager.clearChargingProfile(
                    session.getId(), id, connectorId, chargingProfilePurpose, stackLevel);

            if (removed) {
                // Recalculer la limite effective
                int conn = connectorId != null ? connectorId : session.getConnectorId();
                EffectiveLimit effectiveLimit = chargingProfileManager.getEffectiveLimit(
                        session.getId(), conn, session.getPhaseType(), session.getVoltage());

                if (effectiveLimit.hasLimit()) {
                    session.setScpLimitKw(effectiveLimit.getLimitKw());
                    session.setScpProfileId(effectiveLimit.profileId());
                    session.setScpPurpose(effectiveLimit.source() != null ? effectiveLimit.source().getValue() : null);
                } else {
                    session.setScpLimitKw(0);
                    session.setScpProfileId(null);
                    session.setScpPurpose(null);
                }

                session.addLog(LogEntry.info("SCP", "Profil(s) supprimé(s)"));
                response.put("status", "Accepted");
            } else {
                response.put("status", "Unknown");
            }

            log.info("[SCP] ClearChargingProfile: session={}, id={}, connector={}, removed={}",
                    session.getId(), id, connectorId, removed);

        } catch (Exception e) {
            log.error("[SCP] ClearChargingProfile failed", e);
            response.put("status", "Unknown");
        }

        return response;
    }

    /**
     * Traite GetCompositeSchedule selon OCPP 1.6.
     */
    private Map<String, Object> handleGetCompositeSchedule(Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();

        try {
            Integer connectorId = (Integer) payload.get("connectorId");
            Integer duration = (Integer) payload.get("duration");
            String chargingRateUnit = (String) payload.get("chargingRateUnit");

            if (connectorId == null || duration == null) {
                response.put("status", "Rejected");
                return response;
            }

            if (chargingRateUnit == null) {
                chargingRateUnit = "W";
            }

            ChargingProfileManager.CompositeSchedule composite = chargingProfileManager.getCompositeSchedule(
                    session.getId(),
                    connectorId,
                    duration,
                    chargingRateUnit,
                    session.getPhaseType(),
                    session.getVoltage()
            );

            if (composite != null && composite.chargingSchedulePeriod() != null && !composite.chargingSchedulePeriod().isEmpty()) {
                response.put("status", "Accepted");
                response.put("connectorId", connectorId);
                response.put("scheduleStart", composite.scheduleStart().toString());

                Map<String, Object> schedule = new HashMap<>();
                schedule.put("duration", composite.duration());
                schedule.put("chargingRateUnit", composite.chargingRateUnit().getValue());

                List<Map<String, Object>> periods = new ArrayList<>();
                for (ChargingProfileManager.CompositeSchedulePeriod period : composite.chargingSchedulePeriod()) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("startPeriod", period.startPeriod());
                    p.put("limit", period.limit());
                    periods.add(p);
                }
                schedule.put("chargingSchedulePeriod", periods);

                response.put("chargingSchedule", schedule);

                log.info("[SCP] GetCompositeSchedule: session={}, connector={}, periods={}",
                        session.getId(), connectorId, periods.size());
            } else {
                response.put("status", "Accepted");
                response.put("connectorId", connectorId);
                log.info("[SCP] GetCompositeSchedule: session={}, connector={}, no active profiles",
                        session.getId(), connectorId);
            }

        } catch (Exception e) {
            log.error("[SCP] GetCompositeSchedule failed", e);
            response.put("status", "Rejected");
        }

        return response;
    }

    /**
     * Parse un ChargingProfile depuis la structure OCPP.
     */
    private ChargingProfile parseChargingProfile(Map<String, Object> data) {
        try {
            ChargingProfile.ChargingProfileBuilder builder = ChargingProfile.builder();

            // Champs obligatoires
            builder.chargingProfileId(((Number) data.get("chargingProfileId")).intValue());
            builder.stackLevel(((Number) data.get("stackLevel")).intValue());

            // Purpose
            String purpose = (String) data.get("chargingProfilePurpose");
            builder.chargingProfilePurpose(ChargingProfilePurpose.fromValue(purpose));

            // Kind
            String kind = (String) data.get("chargingProfileKind");
            builder.chargingProfileKind(ChargingProfileKind.fromValue(kind));

            // TransactionId (optionnel)
            if (data.get("transactionId") != null) {
                builder.transactionId(((Number) data.get("transactionId")).intValue());
            }

            // RecurrencyKind (optionnel)
            if (data.get("recurrencyKind") != null) {
                String recurrency = (String) data.get("recurrencyKind");
                if ("Daily".equalsIgnoreCase(recurrency)) {
                    builder.recurrencyKind(RecurrencyKind.DAILY);
                } else if ("Weekly".equalsIgnoreCase(recurrency)) {
                    builder.recurrencyKind(RecurrencyKind.WEEKLY);
                }
            }

            // ValidFrom/ValidTo (optionnel)
            if (data.get("validFrom") != null) {
                builder.validFrom(java.time.LocalDateTime.parse((String) data.get("validFrom")));
            }
            if (data.get("validTo") != null) {
                builder.validTo(java.time.LocalDateTime.parse((String) data.get("validTo")));
            }

            // ChargingSchedule
            Map<String, Object> scheduleData = (Map<String, Object>) data.get("chargingSchedule");
            if (scheduleData != null) {
                ChargingSchedule.ChargingScheduleBuilder scheduleBuilder = ChargingSchedule.builder();

                // Duration (optionnel)
                if (scheduleData.get("duration") != null) {
                    scheduleBuilder.duration(((Number) scheduleData.get("duration")).intValue());
                }

                // StartSchedule (optionnel)
                if (scheduleData.get("startSchedule") != null) {
                    scheduleBuilder.startSchedule(java.time.LocalDateTime.parse((String) scheduleData.get("startSchedule")));
                }

                // ChargingRateUnit
                String rateUnit = (String) scheduleData.get("chargingRateUnit");
                scheduleBuilder.chargingRateUnit(ChargingRateUnit.fromValue(rateUnit));

                // MinChargingRate (optionnel)
                if (scheduleData.get("minChargingRate") != null) {
                    scheduleBuilder.minChargingRate(((Number) scheduleData.get("minChargingRate")).doubleValue());
                }

                // ChargingSchedulePeriod
                List<Map<String, Object>> periodsData = (List<Map<String, Object>>) scheduleData.get("chargingSchedulePeriod");
                if (periodsData != null) {
                    List<ChargingSchedulePeriod> periods = new ArrayList<>();
                    for (Map<String, Object> periodData : periodsData) {
                        ChargingSchedulePeriod.ChargingSchedulePeriodBuilder periodBuilder =
                                ChargingSchedulePeriod.builder();

                        periodBuilder.startPeriod(((Number) periodData.get("startPeriod")).intValue());
                        periodBuilder.limit(((Number) periodData.get("limit")).doubleValue());

                        if (periodData.get("numberPhases") != null) {
                            periodBuilder.numberPhases(((Number) periodData.get("numberPhases")).intValue());
                        }

                        periods.add(periodBuilder.build());
                    }
                    scheduleBuilder.chargingSchedulePeriod(periods);
                }

                builder.chargingSchedule(scheduleBuilder.build());
            }

            return builder.build();

        } catch (Exception e) {
            log.error("[SCP] Failed to parse ChargingProfile: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("WebSocket closed for session {}: {} (code: {}, remote: {})",
                session.getId(), reason, code, remote);

        session.setConnected(false);
        session.setState(SessionState.DISCONNECTED);
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket error for session {}: {}", session.getId(), ex.getMessage());
        session.addLog(LogEntry.error("WebSocket error: " + ex.getMessage()));
    }
}