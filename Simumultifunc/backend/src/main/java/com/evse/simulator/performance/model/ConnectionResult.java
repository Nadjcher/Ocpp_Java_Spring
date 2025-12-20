package com.evse.simulator.performance.model;

import lombok.*;

import java.time.Instant;

/**
 * Résultat d'une connexion dans un test de performance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionResult {
    private String connectionId;
    private String cpId;
    private boolean success;
    private long connectTimeMs;
    private long firstMessageTimeMs;
    private long connectionLatencyMs;
    private long bootLatencyMs;
    private Instant connectedAt;
    private Instant disconnectedAt;
    private String errorMessage;
    private int messagesExchanged;

    /**
     * Gets connection latency, falling back to connect time.
     */
    public long getConnectionLatencyMs() {
        return connectionLatencyMs > 0 ? connectionLatencyMs : connectTimeMs;
    }

    /**
     * Gets boot latency, falling back to first message time.
     */
    public long getBootLatencyMs() {
        return bootLatencyMs > 0 ? bootLatencyMs : firstMessageTimeMs;
    }

    /**
     * Creates a successful connection result.
     */
    public static ConnectionResult success(String cpId, long connectTimeMs, int messagesExchanged) {
        return ConnectionResult.builder()
                .cpId(cpId)
                .success(true)
                .connectTimeMs(connectTimeMs)
                .messagesExchanged(messagesExchanged)
                .connectedAt(Instant.now())
                .build();
    }

    /**
     * Creates a failed connection result.
     */
    public static ConnectionResult failed(String cpId, String errorMessage) {
        return ConnectionResult.builder()
                .cpId(cpId)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
