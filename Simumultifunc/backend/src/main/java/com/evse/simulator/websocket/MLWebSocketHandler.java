package com.evse.simulator.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler WebSocket natif pour les notifications ML temps réel.
 * Gère les connexions et diffuse les anomalies détectées.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MLWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("[ML WebSocket] Connection established: {}", session.getId());

        // Envoyer un message de connexion
        Map<String, Object> connectionMsg = Map.of(
            "type", "CONNECTION",
            "message", "Connected to ML WebSocket",
            "sessionId", session.getId()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(connectionMsg)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("[ML WebSocket] Connection closed: {} ({})", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("[ML WebSocket] Received: {}", payload);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");

            if ("ping".equals(type)) {
                // Répondre au ping avec un pong
                Map<String, Object> pong = Map.of(
                    "type", "pong",
                    "timestamp", System.currentTimeMillis()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
            }
        } catch (Exception e) {
            log.error("[ML WebSocket] Error handling message: {}", e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[ML WebSocket] Transport error for session {}: {}",
            session.getId(), exception.getMessage());
        sessions.remove(session.getId());
    }

    /**
     * Diffuse une anomalie ML à tous les clients connectés.
     *
     * @param anomaly données de l'anomalie
     */
    public void broadcastAnomaly(Map<String, Object> anomaly) {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> message = Map.of(
                "type", "ML_ANOMALY",
                "data", anomaly,
                "timestamp", System.currentTimeMillis()
            );
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            sessions.values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                    }
                } catch (Exception e) {
                    log.error("[ML WebSocket] Failed to send to session {}: {}",
                        session.getId(), e.getMessage());
                }
            });

            log.debug("[ML WebSocket] Broadcasted anomaly to {} clients", sessions.size());
        } catch (Exception e) {
            log.error("[ML WebSocket] Failed to serialize anomaly: {}", e.getMessage());
        }
    }

    /**
     * Retourne le nombre de clients connectés.
     */
    public int getConnectedCount() {
        return (int) sessions.values().stream().filter(WebSocketSession::isOpen).count();
    }
}
