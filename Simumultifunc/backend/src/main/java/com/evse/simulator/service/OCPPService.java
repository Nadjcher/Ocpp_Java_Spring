package com.evse.simulator.service;

import com.evse.simulator.domain.service.BroadcastService;
import com.evse.simulator.exception.OCPPException;
import com.evse.simulator.exception.SessionNotFoundException;
import com.evse.simulator.model.*;
import com.evse.simulator.model.enums.*;
import com.evse.simulator.model.enums.ChargerType;
import com.evse.simulator.ocpp.handler.*;
import com.evse.simulator.ocpp.v16.Ocpp16MessageRouter;
import com.evse.simulator.websocket.OCPPWebSocketClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
    private final com.evse.simulator.domain.service.TNRService tnrService;
    private final com.evse.simulator.domain.service.SmartChargingService smartChargingService;

    public OCPPService(SessionService sessionService,
                       BroadcastService broadcaster,
                       OcppHandlerRegistry handlerRegistry,
                       ChargingProfileManager chargingProfileManager,
                       Ocpp16MessageRouter messageRouter,
                       @Lazy com.evse.simulator.domain.service.TNRService tnrService,
                       com.evse.simulator.domain.service.SmartChargingService smartChargingService) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.handlerRegistry = handlerRegistry;
        this.chargingProfileManager = chargingProfileManager;
        this.messageRouter = messageRouter;
        this.tnrService = tnrService;
        this.smartChargingService = smartChargingService;
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
    private final Map<String, ScheduledFuture<?>> clockAlignedDataTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    private static final String CONTEXT_CLOCK_ALIGNED = "Sample.Clock";

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

            // Determine OCPP subprotocol based on session configuration
            String ocppVersion = session.getOcppVersion() != null ? session.getOcppVersion() : "1.6";
            String subprotocol = ocppVersion.startsWith("2") ? "ocpp2.0.1" : "ocpp1.6";

            // Créer le client WebSocket avec le gestionnaire de profils de charge et le routeur OCPP 1.6
            // The subprotocol is passed to the constructor for proper WebSocket handshake negotiation
            // Le broadcaster permet au client de diffuser les mises à jour de session après les handlers
            OCPPWebSocketClient client = new OCPPWebSocketClient(uri, session, this, chargingProfileManager, messageRouter, subprotocol, broadcaster);

            // Injecter le service TNR pour l'enregistrement des événements
            client.setTnrService(tnrService);

            // Ajouter le token d'authentification si présent
            if (session.getBearerToken() != null && !session.getBearerToken().isBlank()) {
                client.addHeader("Authorization", "Bearer " + session.getBearerToken());
            }

            log.info("Connecting session {} with OCPP subprotocol: {}", sessionId, subprotocol);

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
        stopClockAlignedData(sessionId);

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

        // Enregistrer l'événement CALL sortant pour TNR (important pour le replay!)
        recordOutgoingCall(sessionId, messageId, action.getValue(), payload);

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
     * Enregistre un CALL sortant pour TNR.
     * Permet de capturer les CALLs client pour un replay complet.
     */
    private void recordOutgoingCall(String sessionId, String messageId, String action, Map<String, Object> payload) {
        if (tnrService != null && tnrService.isRecording()) {
            com.evse.simulator.model.TNREvent event = new com.evse.simulator.model.TNREvent();
            event.setTimestamp(System.currentTimeMillis());
            event.setSessionId(sessionId);
            event.setType("ocpp_call");
            event.setAction(action);
            event.setPayload(java.util.Map.of(
                "direction", "outgoing",
                "messageId", messageId,
                "payload", payload
            ));
            tnrService.recordEvent(event);
            log.debug("TNR: Recorded outgoing CALL {} [{}]", action, messageId);
        }
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

    // =========================================================================
    // Clock Aligned Data (MeterValues alignés sur l'horloge)
    // =========================================================================

    /**
     * Démarre l'envoi des MeterValues alignés sur l'horloge.
     * Les envois sont programmés aux minutes alignées (ex: 00, 15, 30, 45 pour un intervalle de 900s).
     *
     * @param sessionId ID de la session
     * @param intervalSec Intervalle en secondes (ex: 900 = 15 minutes)
     */
    public void startClockAlignedData(String sessionId, int intervalSec) {
        if (intervalSec <= 0) {
            log.info("ClockAlignedData disabled for session {} (interval=0)", sessionId);
            return;
        }

        // D'abord arrêter toute tâche existante
        stopClockAlignedData(sessionId);

        Session session = sessionService.getSession(sessionId);
        session.setClockAlignedDataInterval(intervalSec);

        // Calculer le délai initial jusqu'au prochain moment aligné
        long delayMs = calculateDelayToNextAlignedTime(intervalSec);

        log.info("Starting ClockAlignedData for session {} every {}s, first in {}ms",
                sessionId, intervalSec, delayMs);
        sessionService.addLog(sessionId, LogEntry.info("ClockAlignedData",
                "Activé: envoi toutes les " + intervalSec + "s, prochain dans " + (delayMs / 1000) + "s"));

        // Programmer la tâche avec scheduleAtFixedRate à partir du moment aligné
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> sendClockAlignedMeterValues(sessionId),
                delayMs, intervalSec * 1000L, TimeUnit.MILLISECONDS);

        clockAlignedDataTasks.put(sessionId, task);
        session.setClockAlignedDataActive(true);
    }

    /**
     * Arrête l'envoi des MeterValues alignés sur l'horloge.
     *
     * @param sessionId ID de la session
     */
    public void stopClockAlignedData(String sessionId) {
        ScheduledFuture<?> task = clockAlignedDataTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
            log.info("Stopped ClockAlignedData for session {}", sessionId);
            sessionService.addLog(sessionId, LogEntry.info("ClockAlignedData", "Désactivé"));
        }
        sessionService.findSession(sessionId).ifPresent(s -> {
            s.setClockAlignedDataActive(false);
            s.setClockAlignedDataInterval(0);
        });
    }

    /**
     * Calcule le délai en millisecondes jusqu'au prochain moment aligné sur l'horloge.
     * Par exemple, pour un intervalle de 900s (15 min), aligne sur :00, :15, :30, :45.
     *
     * @param intervalSec Intervalle en secondes
     * @return Délai en millisecondes
     */
    private long calculateDelayToNextAlignedTime(int intervalSec) {
        long now = System.currentTimeMillis();
        long intervalMs = intervalSec * 1000L;

        // Calculer le prochain moment aligné
        // On se base sur le début de l'heure (ou du jour pour des intervalles > 1h)
        java.time.ZonedDateTime nowZdt = java.time.Instant.ofEpochMilli(now)
                .atZone(java.time.ZoneId.systemDefault());

        // Trouver le début de l'heure actuelle
        java.time.ZonedDateTime hourStart = nowZdt.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        long hourStartMs = hourStart.toInstant().toEpochMilli();

        // Calculer combien de périodes complètes depuis le début de l'heure
        long elapsedSinceHourStart = now - hourStartMs;
        long periodsElapsed = elapsedSinceHourStart / intervalMs;

        // Le prochain moment aligné
        long nextAlignedMs = hourStartMs + (periodsElapsed + 1) * intervalMs;
        long delay = nextAlignedMs - now;

        // Si le délai est trop court (< 1s), passer au suivant
        if (delay < 1000) {
            delay += intervalMs;
        }

        log.debug("ClockAligned: now={}, nextAligned={}, delay={}ms",
                nowZdt, java.time.Instant.ofEpochMilli(nextAlignedMs), delay);

        return delay;
    }

    /**
     * Envoie MeterValues avec le contexte Sample.Clock (ClockAlignedData).
     *
     * @param sessionId ID de la session
     * @return CompletableFuture avec la réponse
     */
    public CompletableFuture<Map<String, Object>> sendClockAlignedMeterValues(String sessionId) {
        Session session = sessionService.getSession(sessionId);

        // Utiliser le handler pour construire le payload avec context = Sample.Clock
        Integer transactionId = session.getTransactionId() != null ?
                Integer.parseInt(session.getTransactionId()) : null;

        OcppMessageContext context = OcppMessageContext.builder()
                .sessionId(sessionId)
                .session(session)
                .connectorId(session.getConnectorId())
                .transactionId(transactionId)
                .meterValue((long) session.getMeterValue())
                .readingContext(CONTEXT_CLOCK_ALIGNED)
                .build();

        Map<String, Object> payload = handlerRegistry
                .getHandlerOrThrow(OCPPAction.METER_VALUES)
                .buildPayload(context);

        log.info("[ClockAligned] Session {}: Sending MeterValues at aligned time", sessionId);
        return sendCall(sessionId, OCPPAction.METER_VALUES, payload);
    }

    /**
     * Simule la progression de la charge.
     * Applique les limites Smart Charging si un profil est actif.
     * Supporte le mode Idle Fee: charge pendant X minutes puis idle (power=0) pendant Y minutes.
     */
    private void simulateCharging(String sessionId) {
        sessionService.findSession(sessionId).ifPresent(session -> {
            if (!session.isCharging()) return;

            double soc = session.getSoc();
            double targetSoc = session.getTargetSoc();
            double powerKw = 0;
            double energyKwh = 0;
            String limitedBy = "vehicle/evse";

            // ═══════════════════════════════════════════════════════════════════
            // MODE IDLE FEE: Charge limitée dans le temps + période d'idle
            // ═══════════════════════════════════════════════════════════════════
            if (session.isIdleFeeEnabled()) {
                // Vérifier si on doit passer en mode idle
                if (session.shouldEnterIdleMode() && !session.isInIdleMode()) {
                    session.enterIdleMode();
                    log.info("[IDLE-FEE] Session {}: Entering IDLE mode after {} minutes of charging",
                        sessionId, session.getChargingDurationMinutes());
                    sessionService.addLog(sessionId, LogEntry.info("IDLE-FEE",
                        "Charge terminée, début de la période d'idle (" + session.getIdleDurationMinutes() + " min)"));
                }

                // En mode idle: power = 0, pas de consommation d'énergie
                if (session.isInIdleMode()) {
                    powerKw = 0;
                    energyKwh = 0;
                    limitedBy = "idle-fee";

                    // Vérifier si la période d'idle est terminée
                    // Si idleDurationMinutes >= 999, c'est un idle manuel infini - pas d'arrêt automatique
                    boolean isManualIdleMode = session.getIdleDurationMinutes() >= 999;

                    if (!isManualIdleMode && session.isIdlePeriodComplete()) {
                        log.info("[IDLE-FEE] Session {}: Idle period complete, stopping transaction",
                            sessionId);
                        sessionService.addLog(sessionId, LogEntry.success("IDLE-FEE",
                            "Période d'idle terminée, arrêt de la transaction"));
                        // Arrêter la transaction
                        scheduler.schedule(() -> sendStopTransaction(sessionId), 1, TimeUnit.SECONDS);
                        return;
                    }

                    // Log de progression idle (toutes les 10 itérations pour éviter spam)
                    long idleMinutes = session.getIdleStartTime() != null ?
                        java.time.Duration.between(session.getIdleStartTime(), LocalDateTime.now()).toMinutes() : 0;

                    if (isManualIdleMode) {
                        // Mode idle manuel - log moins fréquent
                        if (idleMinutes % 5 == 0) {
                            log.info("[IDLE] Session {}: Manual IDLE mode - {} min elapsed, power=0 kW (session stays alive)",
                                sessionId, idleMinutes);
                        }
                    } else {
                        log.info("[IDLE-FEE] Session {}: IDLE mode - {} min elapsed, {} min remaining, power=0 kW",
                            sessionId, idleMinutes, session.getIdleFeeRemainingMinutes());
                    }

                    // Mettre à jour les données (power=0, énergie inchangée)
                    sessionService.updateChargingData(sessionId, soc, powerKw, session.getEnergyDeliveredKwh());
                    return;
                }

                // Sinon, charge normale jusqu'à la fin de la durée de charge
                log.info("[IDLE-FEE] Session {}: Charging mode - {} min remaining before idle",
                    sessionId, session.getChargingDurationMinutes() -
                    (session.getStartTime() != null ?
                        java.time.Duration.between(session.getStartTime(), LocalDateTime.now()).toMinutes() : 0));
            }

            // ═══════════════════════════════════════════════════════════════════
            // CALCUL DE LA PUISSANCE (mode normal ou mode idle-fee en charge)
            // ═══════════════════════════════════════════════════════════════════

            // Déterminer la puissance maximale selon le type de chargeur
            ChargerType chargerType = session.getChargerType();
            double chargerMaxPower = chargerType != null ? chargerType.getMaxPowerKw() : 22.0;
            double sessionMaxPower = session.getMaxPowerKw();

            // Utiliser le min entre la capacité du chargeur et la limite de session
            double effectiveMaxPower = Math.min(chargerMaxPower, sessionMaxPower > 0 ? sessionMaxPower : chargerMaxPower);

            // Calculer la puissance selon la courbe de charge
            if (chargerType != null && chargerType.isDC()) {
                // Courbe DC réaliste
                if (soc < 20) {
                    powerKw = effectiveMaxPower * 0.9; // Montée en charge
                } else if (soc < 50) {
                    powerKw = effectiveMaxPower; // Pleine puissance
                } else if (soc < 80) {
                    powerKw = effectiveMaxPower * (1.0 - (soc - 50) / 100); // Dégressif
                } else {
                    powerKw = effectiveMaxPower * 0.3 * (1.0 - (soc - 80) / 40); // Très réduit
                }
            } else {
                // Courbe AC - réduction à haut SoC
                if (soc > 90) {
                    powerKw = effectiveMaxPower * 0.25;
                } else if (soc > 80) {
                    powerKw = effectiveMaxPower * 0.5;
                } else {
                    powerKw = effectiveMaxPower;
                }
            }

            // ═══════════════════════════════════════════════════════════════════
            // APPLIQUER LA LIMITE SMART CHARGING (SCP)
            // ═══════════════════════════════════════════════════════════════════
            try {
                double scpLimit = smartChargingService.getCurrentLimit(sessionId);
                log.info("[SIM] Session {}: Calculated power={} kW, SCP limit={} kW",
                    sessionId, powerKw, scpLimit);

                if (scpLimit < powerKw) {
                    log.info("[SIM] Session {}: APPLYING SCP limit {} kW (was {} kW)",
                        sessionId, scpLimit, powerKw);
                    powerKw = scpLimit;
                    limitedBy = "scp";
                    session.setScpLimitKw(scpLimit);

                    // Calculer aussi scpLimitA depuis la limite kW
                    double voltage = session.getVoltage() > 0 ? session.getVoltage() : 230.0;
                    int phases = session.getEffectivePhases();
                    double scpLimitA;
                    if (phases > 1 && voltage < 300) {
                        scpLimitA = (scpLimit * 1000) / (voltage * phases);
                    } else if (phases > 1) {
                        scpLimitA = (scpLimit * 1000) / (voltage * Math.sqrt(3));
                    } else {
                        scpLimitA = (scpLimit * 1000) / voltage;
                    }
                    session.setScpLimitA(scpLimitA);
                }
            } catch (Exception e) {
                log.warn("[SIM] Session {}: Error getting SCP limit: {}", sessionId, e.getMessage());
            }

            // Calcul de l'énergie pour l'intervalle réel (en secondes, cohérent avec le scheduler)
            int actualIntervalSec = session.getMeterValuesInterval() > 0 ?
                    session.getMeterValuesInterval() : meterValuesInterval / 1000;
            double intervalHours = actualIntervalSec / 3600.0;
            energyKwh = powerKw * intervalHours;

            // Mise à jour du SoC (simulation)
            double batteryCapacity = 60.0; // kWh par défaut
            double socIncrement = (energyKwh / batteryCapacity) * 100;
            double newSoc = Math.min(soc + socIncrement, targetSoc);

            // Arrêt si cible atteinte (seulement si idle fee n'est pas activé)
            if (!session.isIdleFeeEnabled() && newSoc >= targetSoc) {
                newSoc = targetSoc;
                powerKw = 0;
                // Arrêter automatiquement
                scheduler.schedule(() -> sendStopTransaction(sessionId), 1, TimeUnit.SECONDS);
            }

            double newEnergy = session.getEnergyDeliveredKwh() + energyKwh;

            log.info("[SIM] Session {}: SoC={}%, power={} kW, energy={} kWh, limitedBy={}{}",
                sessionId, String.format("%.1f", newSoc), String.format("%.2f", powerKw),
                String.format("%.3f", newEnergy), limitedBy,
                session.isIdleFeeEnabled() ? " [IDLE-FEE enabled]" : "");

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