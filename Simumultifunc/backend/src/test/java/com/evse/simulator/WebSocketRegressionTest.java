package com.evse.simulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocket Regression Tests for Spring Boot 3.5 migration.
 * <p>
 * These tests ensure that WebSocket/STOMP functionality continues
 * to work correctly after the migration.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("WebSocket Regression Tests")
class WebSocketRegressionTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("WebSocket STOMP connection should succeed with SockJS")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void webSocketStompConnectionShouldSucceed() throws Exception {
        // Create SockJS client
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        // Create STOMP client
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<Boolean> connected = new CompletableFuture<>();

        String url = "ws://localhost:" + port + "/ws";

        stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connected.complete(true);
                session.disconnect();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connected.completeExceptionally(exception);
            }
        });

        Boolean result = connected.get(15, TimeUnit.SECONDS);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("WebSocket native connection should succeed")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void webSocketNativeConnectionShouldSucceed() throws Exception {
        // Create native WebSocket client (without SockJS)
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<Boolean> connected = new CompletableFuture<>();

        String url = "ws://localhost:" + port + "/ws-native";

        stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connected.complete(true);
                session.disconnect();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connected.completeExceptionally(exception);
            }
        });

        Boolean result = connected.get(15, TimeUnit.SECONDS);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("WebSocket subscription to /topic should work")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void webSocketSubscriptionShouldWork() throws Exception {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<Boolean> subscribed = new CompletableFuture<>();

        String url = "ws://localhost:" + port + "/ws";

        stompClient.connectAsync(url, new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                // Try to subscribe to a topic
                session.subscribe("/topic/sessions", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // Frame received
                    }
                });
                subscribed.complete(true);
                session.disconnect();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                subscribed.completeExceptionally(exception);
            }
        });

        Boolean result = subscribed.get(15, TimeUnit.SECONDS);
        assertThat(result).isTrue();
    }
}
