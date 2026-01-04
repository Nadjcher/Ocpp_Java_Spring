package com.evse.simulator.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler WebSocket pour les connexions OCPP.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OCPPWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("OCPP WebSocket connection established: {}", session.getId());
        sendMessage(session, Map.of("type", "ocpp_connected", "sessionId", session.getId()));
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        try {
            String payload = message.getPayload().toString();
            log.debug("OCPP Message received from {}: {}", session.getId(), payload);
        } catch (Exception e) {
            log.error("Failed to process OCPP message from session {}", session.getId(), e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Transport error on session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        log.info("OCPP WebSocket connection closed: {} ({})", session.getId(), closeStatus);
        sessions.remove(session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void sendMessage(WebSocketSession session, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to send message on session {}", session.getId(), e);
        }
    }
}
