package com.evse.simulator.config;

import com.evse.simulator.websocket.MLWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * Configuration des WebSockets natifs (non-STOMP).
 * Enregistre les handlers pour les connexions WebSocket directes.
 */
@Configuration
@EnableWebSocket
@Slf4j
@RequiredArgsConstructor
public class NativeWebSocketConfig implements WebSocketConfigurer {

    private final MLWebSocketHandler mlWebSocketHandler;

    @Value("${websocket.allowed-origins:http://localhost:3000,http://localhost:3002}")
    private List<String> allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Endpoint pour les notifications ML en temps réel
        registry.addHandler(mlWebSocketHandler, "/ws-ml")
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]));

        log.info("Native WebSocket handlers registered: /ws-ml");
    }
}
