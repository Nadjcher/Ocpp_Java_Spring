package com.evse.simulator.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Réponse d'erreur standardisée.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Code d'erreur HTTP.
     */
    private int status;

    /**
     * Type d'erreur.
     */
    private String error;

    /**
     * Message d'erreur.
     */
    private String message;

    /**
     * Chemin de la requête.
     */
    private String path;

    /**
     * Horodatage.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Détails des erreurs de validation.
     */
    private List<FieldError> fieldErrors;

    /**
     * Détails supplémentaires.
     */
    private Map<String, Object> details;

    /**
     * Erreur de champ pour la validation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }

    /**
     * Crée une réponse d'erreur simple.
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return ErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .build();
    }
}