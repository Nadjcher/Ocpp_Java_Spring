package com.evse.simulator.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler WebSocket pour les sessions de simulation.
 * Gère les connexions WebSocket natives (non-STOMP).
 */
@Slf4j
@Component
public class SessionWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("WebSocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("WebSocket connection closed: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("Received message: {}", message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error", exception);
        sessions.remove(session.getId());
    }

    /**
     * Envoie un message à toutes les sessions connectées.
     */
    public void broadcast(String message) {
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            }
        });
    }

    /**
     * Récupère le nombre de sessions actives.
     */
    public int getActiveSessionCount() {
        return (int) sessions.values().stream().filter(WebSocketSession::isOpen).count();
    }
}