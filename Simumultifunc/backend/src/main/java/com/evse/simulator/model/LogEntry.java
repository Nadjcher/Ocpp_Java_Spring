package com.evse.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entrée de log structurée pour une session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEntry {

    /**
     * Horodatage du log.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Niveau de log.
     */
    @Builder.Default
    private Level level = Level.INFO;

    /**
     * Message de log.
     */
    private String message;

    /**
     * Catégorie du log.
     */
    private String category;

    /**
     * Données supplémentaires.
     */
    private Object data;

    /**
     * Niveaux de log.
     */
    public enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR,
        SUCCESS
    }

    /**
     * Crée un log INFO.
     */
    public static LogEntry info(String message) {
        return LogEntry.builder()
                .level(Level.INFO)
                .message(message)
                .build();
    }

    /**
     * Crée un log INFO avec catégorie.
     */
    public static LogEntry info(String category, String message) {
        return LogEntry.builder()
                .level(Level.INFO)
                .category(category)
                .message(message)
                .build();
    }

    /**
     * Crée un log ERROR.
     */
    public static LogEntry error(String message) {
        return LogEntry.builder()
                .level(Level.ERROR)
                .message(message)
                .build();
    }

    /**
     * Crée un log ERROR avec catégorie.
     */
    public static LogEntry error(String category, String message) {
        return LogEntry.builder()
                .level(Level.ERROR)
                .category(category)
                .message(message)
                .build();
    }

    /**
     * Crée un log WARN.
     */
    public static LogEntry warn(String message) {
        return LogEntry.builder()
                .level(Level.WARN)
                .message(message)
                .build();
    }

    /**
     * Crée un log WARN avec catégorie.
     */
    public static LogEntry warn(String category, String message) {
        return LogEntry.builder()
                .level(Level.WARN)
                .category(category)
                .message(message)
                .build();
    }

    /**
     * Crée un log SUCCESS.
     */
    public static LogEntry success(String message) {
        return LogEntry.builder()
                .level(Level.SUCCESS)
                .message(message)
                .build();
    }

    /**
     * Crée un log SUCCESS avec catégorie.
     */
    public static LogEntry success(String category, String message) {
        return LogEntry.builder()
                .level(Level.SUCCESS)
                .category(category)
                .message(message)
                .build();
    }

    /**
     * Crée un log DEBUG.
     */
    public static LogEntry debug(String message) {
        return LogEntry.builder()
                .level(Level.DEBUG)
                .message(message)
                .build();
    }

    /**
     * Crée un log OCPP.
     */
    public static LogEntry ocpp(String direction, String action, String status) {
        return LogEntry.builder()
                .level(Level.INFO)
                .category("OCPP")
                .message(String.format("%s %s: %s", direction, action, status))
                .build();
    }
}