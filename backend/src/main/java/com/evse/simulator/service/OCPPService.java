package com.evse.simulator.service;

import com.evse.simulator.domain.service.BroadcastService;
import com.evse.simulator.exception.OCPPException;
import com.evse.simulator.exception.SessionNotFoundException;
import com.evse.simulator.model.*;
import com.evse.simulator.model.enums.*;
import com.evse.simulator.ocpp.handler.*;
import com.evse.simulator.ocpp.v16.Ocpp16MessageRouter;
import com.evse.simulator.websocket.OCPPWebSocketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service OCPP 1.6 pour la communication avec le CSMS.
 * <p>
 * Gère les connexions WebSocket, l'envoi de messages OCPP,
 * et le traitement des réponses.
 * </p>
 */
@Service
@Slf4j
public class OCPPService implements com.evse.simulator.domain.service.OCPPService {

    private final SessionService sessionService;
    private final BroadcastService broadcaster;
    private final OcppHandlerRegistry handlerRegistry;
    private final ChargingProfileManager chargingProfileManager;
    private final Ocpp16MessageRouter messageRouter;

    public OCPPService(SessionService sessionService,
                       BroadcastService broadcaster,
                       OcppHandlerRegistry handlerRegistry,
                       ChargingProfileManager chargingProfileManager,
                       Ocpp16MessageRouter messageRouter) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.handlerRegistry = handlerRegistry;
        this.chargingProfileManager = chargingProfileManager;
        this.messageRouter = messageRouter;
    }

    @Value("${ocpp.heartbeat-interval:30000}")
    private int heartbeatInterval;

    @Value("${ocpp.meter-values-interval:10000}")
    private int meterValuesInterval;

    @Value("${ocpp.connection-timeout:10000}")
    private int connectionTimeout;

    // Clients WebSocket par session
    private final Map<String, OCPPWebSocketClient> clients = new ConcurrentHashMap<>();

    // Pending requests (messageId -> CompletableFuture)
    private final Map<String, CompletableFuture<Map<String, Object>>> pendingRequests =
            new ConcurrentHashMap<>();

    // Schedulers pour heartbeat et meter values
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> meterValuesTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    private static final DateTimeFormatter OCPP_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    // =========================================================================
    // Connection Management
    // =========================================================================

    /**
     * Connecte une session au CSMS via WebSocket.
     *
     * @param sessionId ID de la session
     * @return CompletableFuture avec le résultat de la connexion
     */
    @Async("ocppExecutor")
    public CompletableFuture<Boolean> connect(String sessionId) {
        Session session = sessionService.getSession(sessionId);

        if (session.isConnected()) {
            log.warn("Session {} already connected", sessionId);
            return CompletableFuture.completedFuture(true);
        }

        try {
            // Construire l'URL WebSocket avec le CP ID
            // Logique identique à Node.js: ne pas doubler le cpId si déjà présent
            String wsUrl = session.getUrl();
            String cpId = session.getCpId();

            // Vérifier si l'URL se termine déjà par le cpId
            if (!wsUrl.endsWith("/" + cpId) && !wsUrl.endsWith(cpId)) {
                if (!wsUrl.endsWith("/")) {
                    wsUrl += "/";
                }
                wsUrl += cpId;
            }

            URI uri = new URI(wsUrl);

            // Créer le client WebSocket avec le gestionnaire de profils de charge et le routeur OCPP 1.6
            OCPPWebSocketClient client = new OCPPWebSocketClient(uri, session, this, chargingProfileManager, messageRouter);

            // Ajouter le token d'authentification si présent
            if (session.getBearerToken() != null && !session.getBearerToken().isBlank()) {
                client.addHeader("Authorization", "Bearer " + session.getBearerToken());
            }

            // Note: Le sous-protocole OCPP est maintenant configuré via Draft_6455
            // dans OCPPWebSocketClient (méthode correcte pour java-websocket)

            clients.put(sessionId, client);

            // Connexion avec timeout
            boolean connected = client.connectBlocking(connectionTimeout, TimeUnit.MILLISECONDS);

            if (connected) {
                sessionService.updateState(sessionId, SessionState.CONNECTED);
                sessionService.addLog(sessionId, LogEntry.success("Connected to " + wsUrl));
                log.info("Session {} connected to {}", sessionId, wsUrl);
                return CompletableFuture.completedFuture(true);
            } else {
                sessionService.addLog(sessionId, LogEntry.error("Connection failed to " + wsUrl));
                clients.remove(sessionId);
                return CompletableFuture.completedFuture(false);
            }

        } catch (Exception e) {
            log.error("Failed to connect session {}: {}", sessionId, e.getMessage());
            sessionService.addLog(sessionId, LogEntry.error("Connection error: " + e.getMessage()));
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Déconnecte une session du CSMS.
     *
     * @param sessionId ID de la session
     */
    public void disconnect(String sessionId) {
        // Arrêter les tâches planifiées
        stopHeartbeat(sessionId);
        stopMeterValues(sessionId);

        // Fermer le client WebSocket
        OCPPWebSocketClient client = clients.remove(sessionId);
        if (client != null) {
            client.close();
        }

        sessionService.updateState(sessionId, SessionState.DISCONNECTED);
        sessionService.addLog(sessionId, LogEntry.info("Disconnected"));
        log.info("Session {} disconnected", sessionId);
    }

    /**
     * Vérifie si une session est connectée.
     *
     * @param sessionId ID de la session
     * @return true si connectée
     */
    public boolean isConnected(String sessionId) {
        OCPPWebSocketClient client = clients.get(sessionId);
        return client != null && client.isOpen();
    }

    // =========================================================================
    // OCPP Messages - Charge Point → CSMS
    // =========================================================================

    /**
     * Envoie BootNotification.
     *
     * @param sessionId ID de la session
     * @return CompletableFuture avec la réponse
     */
    public CompletableFuture<Map<String, Object>> sendBootNotification(String sessionId) {
        Session session = sessionService.getSession(sessionId);

        // Utiliser le handler pour construire le payload
        OcppMessageContext context = OcppMessageContext.builder()
                .sessionId(sessionId)
                .session(session)
                .build();

        Map<String, Object> payload = handlerRegistry
                .getHandlerOrThrow(OCPPAction.BOOT_NOTIFICATION)
                .buildPayload(context);

        return sendCall(sessionId, OCPPAction.BOOT_NOTIFICATION, payload)
                .thenApply(response -> {
                    String status = (String) response.get("status");
                    // Stocker le statut du boot dans la session
                    session.setBootStatus(status);

                    if ("Accepted".equals(status)) {
                        Integer interval = (Integer) response.get("interval");
                        if (interval != null && interval > 0) {
                            session.setHeartbeatInterval(interval);
                        }
                        // État: BOOT_ACCEPTED (booted)
                        sessionService.updateState(sessionId, SessionState.BOOT_ACCEPTED);
                        startHeartbeat(sessionId);
                        sessionService.addLog(sessionId, LogEntry.success("BootNotification accepted"));
                    } else if ("Rejected".equals(status)) {
                        // Boot rejeté - l'OCPP ID n'est pas enregistré sur le CSMS
                        sessionService.updateState(sessionId, SessionState.FAULTED);
                        sessionService.addLog(sessionId, LogEntry.error("BootNotification REJECTED - OCPP ID not registered on CSMS"));
                    } else {
                        // Pending ou autre status
                        sessionService.addLog(sessionId, LogEntry.warn("BootNotification " + status));
                    }
                    return response;
                });
    }

    /**
     * Envoie Authorize.
     *
     * @param sessionId ID de la session
     * @return CompletableFuture avec la réponse
     */
    public CompletableFuture<Map<String, Object>> sendAuthorize(String sessionId) {
        Session session = sessionService.getSession(sessionId);

        // État transitoire: AUTHORIZING
        sessionService.updateState(sessionId, SessionState.AUTHORIZING);

        // Utiliser le handler pour construire le payload
        OcppMessageContext context = OcppMessageContext.builder()
                .sessionId(sessionId)
                .session(session)
                .idTag(session.getIdTag())
                .build();

        Map<String, Object> payload = handlerRegistry
                .getHandlerOrThrow(OCPPAction.AUTHORIZE)
                .buildPayload(context);

        return sendCall(sessionId, OCPPAction.AUTHORIZE, payload)
                .thenApply(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> idTagInfo = (Map<String, Object>) response.get("idTagInfo");
                    if (idTagInfo != null) {
                        String status = (String) idTagInfo.get("status");
                        if ("Accepted".equals(status)) {
                            sessionService.setAuthorized(sessionId, true);
                            // État: AUTHORIZED (prêt à démarrer)
                            sessionService.updateState(sessionId, SessionState.AUTHORIZED);
                            sessionService.addLog(sessionId, LogEntry.success("Authorize accepted"));
                        } else {
                            sessionService.setAuthorized(sessionId, false);
                            // Retour à PLUGGED en cas de refus
                            sessionService.updateState(sessionId, SessionState.PLUGGED);
                            sessionService.addLog(sessionId, LogEntry.warn("Authorize " + status));
                        }
                    }
                    return response;
                });
    }

    /**
     * Envoie StartTransaction.
     *
     * @param sessionId ID de la session
     * @return CompletableFuture avec la réponse
     */
    public CompletableFuture<Map<String, Object>> sendStartTransaction(String sessionId) {
        Session session = sessionService.getSession(sessionId);

        // État transitoire: STARTING
        sessionService.updateState(sessionId, SessionState.STARTING);

        // Utiliser le handler pour construire le payload
        OcppMessageContext context = OcppMessageContext.builder()
                .sessionId(sessionId)
                .session(session)
                .connectorId(session.getConnectorId())
                .idTag(session.getIdTag())
                .meterValue((long) session.getMeterValue())
                .build();

        Map<String, Object> payload = handlerRegistry
                .getHandlerOrThrow(OCPPAction.START_TRANSACTION)
                .buildPayload(context);

        return sendCall(sessionId, OCPPAction.START_TRANSACTION, payload)
                .thenApply(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> idTagInfo = (Map<String, Object>) response.get("idTagInfo");
                    Integer transactionId = (Integer) response.get("transactionId");

                    if (idTagInfo != null && "Accepted".equals(idTagInfo.get("status"))) {
                        session.setTransactionId(String.valueOf(transactionId));
                        session.setStartTime(LocalDateTime.now());
                        // État: CHARGING (started)
                        sessionService.updateState(sessionId, SessionState.CHARGING);
                        startMeterValues(sessionId);
                        sessionService.addLog(sessionId,
                                LogEntry.success("StartTransaction accepted, txId=" + transactionId));
                    } else {
                        // Retour à AUTHORIZED en cas de refus
                        sessionService.updateState(sessionId, SessionState.AUTHORIZED);
                        sessionService.addLog(sessionId, LogEntry.warn("StartTransaction rejected"));
                    }
                    return response;
                });
    }

    /**
     * Envoie StopTransaction.
     *
     * @param sessionId ID de la session
     * @return CompletableFuture avec la réponse
     */
    public CompletableFuture<Map<String, Object>> sendStopTransaction(String sessionId) {
        Session session = sessionService.getSession(sessionId);

        if (session.getTransactionId() == null) {
            return CompletableFuture.failedFuture(
                    new OCPPException("No active transaction"));
        }

        // État transitoire: STOPPING
        sessionService.updateState(sessionId, SessionState.STOPPING);
        stopMeterValues(sessionId);

        // Utiliser le handler pour construire le payload
        OcppMessageContext context = OcppMessageContext.builder()
                .sessionId(sessionId)
                .session(session)
                .transactionId(Integer.parseInt(session.getTransactionId()))
                .meterValue((long) session.getMeterValue())
                .stopReason("Local")
                .idTag(session.getIdTag())
                .build();

        Map<String, Object> payload = handlerRegistry
                .getHandlerOrThrow(OCPPAction.STOP_TRANSACTION)
                .buildPayload(context);

        return sendCall(sessionId, OCPPAction.STOP_TRANSACTION, payload)
                .thenApply(response -> {
                    // NE PAS effacer le transactionId - on en a besoin pour récupérer le prix du CSMS
                    // session.setTransactionId(null);
                    session.setStopTime(LocalDateTime.now());
                    // État: FINISHING (stopped)
                    sessionService.updateState(sessionId, SessionState.FINISHING);
                    sessionService.addLog(sessionId, LogEntry.success("StopTransaction accepted"));

                    // Retour à BOOT_ACCEPTED après un délai (prêt pour nouvelle charge)
                    scheduler.schedule(() ->
                                    sessionService.updateState(sessionId, SessionState.BOOT_ACCEPTED),
                            2, TimeUnit.SECONDS);

                    return response;
                });
    }

    /**
     * Envoie StatusNotification.
     *
     * @param sessionId ID de la session
     * @param status statut du connecteur
     * @return CompletableFuture avec la réponse
     */
    public CompletableFuture<Map<String, Object>> sendStatusNotification(String sessionId,
                                                                          ConnectorStatus status) {
        Session session = sessionService.getSession(sessionId);

        // Utiliser le handler pour construire le payload
        OcppMessageContext context = OcppMessageContext.builder()
                .sessionId(sessionId)
                .session(session)
                .connectorId(session.getConnectorId())
                .status(status.getValue())
                .errorCode("NoError")
                .build();

        Map<String, Object> payload = handlerRegistry
                .getHandlerOrThrow(OCPPAction.STATUS_NOTIFICATION)
                .buildPayload(context);

        return sendCall(sessionId, OCPPAction.STATUS_NOTIFICATION, payload);
    }

    /**
     * Envoie MeterValues.
     *
     * @param sessionId ID de la session
     * @return CompletableFuture avec la réponse
     */
    public CompletableFuture<Map<String, Object>> sendMeterValues(String sessionId) {
        Session session = sessionService.getSession(sessionId);

        // Utiliser le handler pour construire le payload
        Integer transactionId = session.getTransactionId() != null ?
                Integer.parseInt(session.getTransactionId()) : null;

        OcppMessageContext context = OcppMessageContext.builder()
                .sessionId(sessionId)
                .session(session)
                .connectorId(session.getConnectorId())
                .transactionId(transactionId)
                .meterValue((long) session.getMeterValue())
                .build();

        Map<String, Object> payload = handlerRegistry
                .getHandlerOrThrow(OCPPAction.METER_VALUES)
                .buildPayload(context);

        return sendCall(sessionId, OCPPAction.METER_VALUES, payload);
    }

    /**
     * Envoie Heartbeat.
     *
     * @param sessionId ID de la session
     * @return CompletableFuture avec la réponse
     */
    public CompletableFuture<Map<String, Object>> sendHeartbeat(String sessionId) {
        // Utiliser le handler pour construire le payload
        OcppMessageContext context = OcppMessageContext.builder()
                .sessionId(sessionId)
                .build();

        Map<String, Object> payload = handlerRegistry
                .getHandlerOrThrow(OCPPAction.HEARTBEAT)
                .buildPayload(context);

        return sendCall(sessionId, OCPPAction.HEARTBEAT, payload)
                .thenApply(response -> {
                    Session session = sessionService.getSession(sessionId);
                    session.setLastHeartbeat(LocalDateTime.now());
                    return response;
                });
    }

    // =========================================================================
    // Core Send Method
    // =========================================================================

    /**
     * Envoie un message CALL OCPP.
     *
     * @param sessionId ID de la session
     * @param action action OCPP
     * @param payload payload du message
     * @return CompletableFuture avec la réponse
     */
    public CompletableFuture<Map<String, Object>> sendCall(String sessionId,
                                                            OCPPAction action,
                                                            Map<String, Object> payload) {
        OCPPWebSocketClient client = clients.get(sessionId);
        if (client == null || !client.isOpen()) {
            return CompletableFuture.failedFuture(
                    new OCPPException("Session not connected: " + sessionId));
        }

        String messageId = UUID.randomUUID().toString();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();

        // Construire le message OCPP-J
        OCPPMessage message = OCPPMessage.createCall(action, payload);
        message.setMessageId(messageId);

        String json = String.format("[2,\"%s\",\"%s\",%s]",
                messageId,
                action.getValue(),
                toJson(payload));

        // Enregistrer le message
        sessionService.addOcppMessage(sessionId, message);

        // Ajouter un log visible pour le message envoyé
        sessionService.addLog(sessionId, LogEntry.info("OCPP", ">> Sent " + action.getValue() + " " + toJson(payload)));

        // Stocker le future pour la réponse
        pendingRequests.put(messageId, future);

        // Timeout
        scheduler.schedule(() -> {
            CompletableFuture<Map<String, Object>> pending = pendingRequests.remove(messageId);
            if (pending != null && !pending.isDone()) {
                pending.completeExceptionally(new TimeoutException("OCPP timeout for " + action));
                sessionService.addLog(sessionId, LogEntry.warn("OCPP", "!! Timeout waiting for " + action.getValue() + " response"));
            }
        }, 30, TimeUnit.SECONDS);

        // Envoyer
        client.send(json);
        log.debug("Sent {} to session {}: {}", action, sessionId, json);

        return future;
    }

    /**
     * Traite une réponse OCPP reçue.
     *
     * @param sessionId ID de la session
     * @param messageId ID du message
     * @param payload payload de la réponse
     */
    public void handleCallResult(String sessionId, String messageId, Map<String, Object> payload) {
        CompletableFuture<Map<String, Object>> future = pendingRequests.remove(messageId);
        if (future != null) {
            future.complete(payload);

            // Enregistrer la réponse
            OCPPMessage response = OCPPMessage.builder()
                    .messageId(messageId)
                    .messageType(OCPPMessageType.CALL_RESULT)
                    .payload(payload)
                    .direction(OCPPMessage.Direction.INCOMING)
                    .responseStatus(OCPPMessage.ResponseStatus.ACCEPTED)
                    .build();
            sessionService.addOcppMessage(sessionId, response);

            // Ajouter un log visible pour la réponse
            sessionService.addLog(sessionId, LogEntry.success("OCPP", "<< RESULT " + toJson(payload)));
        }
    }

    /**
     * Traite une erreur OCPP reçue.
     *
     * @param sessionId ID de la session
     * @param messageId ID du message
     * @param errorCode code d'erreur
     * @param errorDescription description
     */
    public void handleCallError(String sessionId, String messageId,
                                 String errorCode, String errorDescription) {
        CompletableFuture<Map<String, Object>> future = pendingRequests.remove(messageId);
        if (future != null) {
            future.completeExceptionally(new OCPPException(errorCode, errorDescription, sessionId));

            // Enregistrer l'erreur
            OCPPMessage error = OCPPMessage.createCallError(messageId, errorCode, errorDescription, null);
            error.setDirection(OCPPMessage.Direction.INCOMING);
            sessionService.addOcppMessage(sessionId, error);

            // Ajouter un log visible pour l'erreur
            sessionService.addLog(sessionId, LogEntry.error("OCPP", "<< ERROR [" + errorCode + "] " + errorDescription));
        }
    }

    // =========================================================================
    // Scheduled Tasks
    // =========================================================================

    /**
     * Démarre le heartbeat périodique.
     */
    private void startHeartbeat(String sessionId) {
        Session session = sessionService.getSession(sessionId);
        int interval = session.getHeartbeatInterval() > 0 ?
                session.getHeartbeatInterval() : heartbeatInterval / 1000;

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(sessionId),
                interval, interval, TimeUnit.SECONDS);

        heartbeatTasks.put(sessionId, task);
        session.setHeartbeatActive(true);
        log.debug("Started heartbeat for session {} every {}s", sessionId, interval);
    }

    /**
     * Arrête le heartbeat.
     */
    private void stopHeartbeat(String sessionId) {
        ScheduledFuture<?> task = heartbeatTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
        sessionService.findSession(sessionId).ifPresent(s -> s.setHeartbeatActive(false));
    }

    /**
     * Démarre l'envoi périodique des MeterValues (interne).
     */
    private void startMeterValues(String sessionId) {
        startMeterValuesWithInterval(sessionId, -1);
    }

    /**
     * Démarre l'envoi périodique des MeterValues avec un intervalle spécifié.
     * Cette méthode est publique pour être appelée depuis SimuCompatController.
     *
     * @param sessionId ID de la session
     * @param intervalSec Intervalle en secondes (-1 pour utiliser la valeur par défaut)
     */
    public void startMeterValuesWithInterval(String sessionId, int intervalSec) {
        // D'abord arrêter toute tâche existante
        stopMeterValuesPublic(sessionId);

        Session session = sessionService.getSession(sessionId);
        int interval = intervalSec > 0 ? intervalSec :
                (session.getMeterValuesInterval() > 0 ? session.getMeterValuesInterval() : meterValuesInterval / 1000);

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> {
                    // D'abord simuler la charge pour mettre à jour l'énergie
                    simulateCharging(sessionId);
                    // Ensuite envoyer les MeterValues avec les données à jour
                    sendMeterValues(sessionId);
                },
                interval, interval, TimeUnit.SECONDS);

        meterValuesTasks.put(sessionId, task);
        session.setMeterValuesActive(true);
        session.setMeterValuesInterval(interval);
        log.info("Started meter values for session {} every {}s", sessionId, interval);
    }

    /**
     * Arrête l'envoi des MeterValues (interne).
     */
    private void stopMeterValues(String sessionId) {
        stopMeterValuesPublic(sessionId);
    }

    /**
     * Arrête l'envoi des MeterValues.
     * Cette méthode est publique pour être appelée depuis SimuCompatController.
     *
     * @param sessionId ID de la session
     */
    public void stopMeterValuesPublic(String sessionId) {
        ScheduledFuture<?> task = meterValuesTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
            log.info("Stopped meter values for session {}", sessionId);
        }
        sessionService.findSession(sessionId).ifPresent(s -> s.setMeterValuesActive(false));
    }

    /**
     * Simule la progression de la charge.
     */
    private void simulateCharging(String sessionId) {
        sessionService.findSession(sessionId).ifPresent(session -> {
            if (!session.isCharging()) return;

            double soc = session.getSoc();
            double targetSoc = session.getTargetSoc();
            double powerKw = session.getCurrentPowerKw();

            if (powerKw <= 0) {
                powerKw = Math.min(session.getMaxPowerKw(), 11.0); // 11 kW par défaut
            }

            // Réduction de puissance à haut SoC
            if (soc > 80) {
                powerKw *= 0.5;
            } else if (soc > 90) {
                powerKw *= 0.25;
            }

            // Calcul de l'énergie pour l'intervalle réel (en secondes, cohérent avec le scheduler)
            int actualIntervalSec = session.getMeterValuesInterval() > 0 ?
                    session.getMeterValuesInterval() : meterValuesInterval / 1000;
            double intervalHours = actualIntervalSec / 3600.0;
            double energyKwh = powerKw * intervalHours;

            // Mise à jour du SoC (simulation)
            double batteryCapacity = 60.0; // kWh par défaut
            double socIncrement = (energyKwh / batteryCapacity) * 100;
            double newSoc = Math.min(soc + socIncrement, targetSoc);

            // Arrêt si cible atteinte
            if (newSoc >= targetSoc) {
                newSoc = targetSoc;
                powerKw = 0;
                // Arrêter automatiquement
                scheduler.schedule(() -> sendStopTransaction(sessionId), 1, TimeUnit.SECONDS);
            }

            double newEnergy = session.getEnergyDeliveredKwh() + energyKwh;

            sessionService.updateChargingData(sessionId, newSoc, powerKw, newEnergy);
        });
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private String toJson(Map<String, Object> map) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Récupère le nombre de connexions actives.
     */
    public int getActiveConnectionsCount() {
        return (int) clients.values().stream().filter(c -> c.isOpen()).count();
    }

    /**
     * Déconnecte toutes les sessions.
     */
    public void disconnectAll() {
        new ArrayList<>(clients.keySet()).forEach(this::disconnect);
    }
}