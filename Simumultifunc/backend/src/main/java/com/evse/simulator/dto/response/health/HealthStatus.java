package com.evse.simulator.dto.response.health;

/**
 * Statut de santé du système.
 */
public record HealthStatus(
        String status,
        String version,
        int activeSessions,
        int wsConnections,
        int chargingSessions,
        long uptimeSeconds,
        String uptimeFormatted,
        long usedMemoryMb,
        long maxMemoryMb
) {}
