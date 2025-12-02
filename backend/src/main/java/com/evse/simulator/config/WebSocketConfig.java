package com.evse.simulator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;

/**
 * Configuration WebSocket avec STOMP pour la communication temps réel.
 * <p>
 * Configure les endpoints WebSocket, le broker de messages STOMP,
 * et les paramètres de transport optimisés pour 25k+ connexions.
 * </p>
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${websocket.allowed-origins:http://localhost:3000,http://localhost:3002}")
    private List<String> allowedOrigins;

    @Value("${websocket.message.buffer-size:65536}")
    private int messageBufferSize;

    /**
     * Configure le broker de messages STOMP.
     * <p>
     * - /topic : pour les diffusions (broadcast) vers tous les abonnés
     * - /queue : pour les messages point-à-point
     * - /app : préfixe pour les messages entrants de l'application
     * </p>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Créer un TaskScheduler pour le heartbeat
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler =
            new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("ws-heartbeat-");
        taskScheduler.initialize();

        // Broker simple en mémoire pour /topic et /queue
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{25000, 25000}) // heartbeat toutes les 25s
                .setTaskScheduler(taskScheduler);

        // Préfixe pour les destinations de l'application
        registry.setApplicationDestinationPrefixes("/app");

        // Préfixe pour les destinations utilisateur
        registry.setUserDestinationPrefix("/user");

        log.info("Message broker configured with /topic, /queue and /app prefix");
    }

    /**
     * Enregistre les endpoints STOMP.
     * <p>
     * Endpoint principal : /ws avec support SockJS pour compatibilité navigateur.
     * </p>
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket avec SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .withSockJS()
                .setStreamBytesLimit(messageBufferSize)
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30000);

        // Endpoint WebSocket natif (sans SockJS)
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]));

        log.info("STOMP endpoints registered: /ws (SockJS), /ws-native");
    }

    /**
     * Configure les paramètres de transport WebSocket.
     * <p>
     * Optimisé pour les gros volumes de messages et 25k+ connexions.
     * </p>
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(messageBufferSize)        // Taille max message
                .setSendBufferSizeLimit(512 * 1024)            // Buffer d'envoi 512KB
                .setSendTimeLimit(20000)                        // Timeout envoi 20s
                .setTimeToFirstMessage(30000);                  // Timeout premier message 30s

        log.info("WebSocket transport configured: messageSize={}, sendBuffer=512KB",
                messageBufferSize);
    }
}