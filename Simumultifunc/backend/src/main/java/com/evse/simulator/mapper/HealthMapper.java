package com.evse.simulator.mapper;

import com.evse.simulator.dto.response.health.HealthStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Mapper pour l'etat de sante du systeme.
 */
@Component
public class HealthMapper {

    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * Cree un HealthStatus.
     */
    public HealthStatus createStatus(
            String status,
            String version,
            int activeSessions,
            int wsConnections,
            int chargingSessions
    ) {
        Runtime runtime = Runtime.getRuntime();
        Duration uptime = Duration.between(startTime, LocalDateTime.now());

        return new HealthStatus(
                status,
                version,
                activeSessions,
                wsConnections,
                chargingSessions,
                uptime.getSeconds(),
                formatUptime(uptime),
                (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
                runtime.maxMemory() / (1024 * 1024)
        );
    }

    private String formatUptime(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }
}
