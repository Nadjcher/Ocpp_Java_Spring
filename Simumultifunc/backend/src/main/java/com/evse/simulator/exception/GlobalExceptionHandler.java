package com.evse.simulator.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire global des exceptions REST.
 * <p>
 * Transforme toutes les exceptions en réponses JSON structurées.
 * </p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Gère les exceptions de session non trouvée.
     */
    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSessionNotFound(
            SessionNotFoundException ex, WebRequest request) {

        log.warn("Session not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .error("Session Not Found")
                        .message(ex.getMessage())
                        .path(request.getDescription(false))
                        .build());
    }

    /**
     * Gère les exceptions de véhicule non trouvé.
     */
    @ExceptionHandler(VehicleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVehicleNotFound(
            VehicleNotFoundException ex, WebRequest request) {

        log.warn("Vehicle not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .error("Vehicle Not Found")
                        .message(ex.getMessage())
                        .path(request.getDescription(false))
                        .build());
    }

    /**
     * Gère les exceptions OCPP.
     */
    @ExceptionHandler(OCPPException.class)
    public ResponseEntity<ErrorResponse> handleOCPPException(
            OCPPException ex, WebRequest request) {

        log.error("OCPP error: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("OCPP Error")
                        .message(ex.getMessage())
                        .errorCode(ex.getErrorCode())
                        .path(request.getDescription(false))
                        .build());
    }

    /**
     * Gère les erreurs de validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation errors: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Validation Error")
                        .message("Invalid request parameters")
                        .validationErrors(errors)
                        .path(request.getDescription(false))
                        .build());
    }

    /**
     * Gère les arguments illégaux.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        log.warn("Illegal argument: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error("Bad Request")
                        .message(ex.getMessage())
                        .path(request.getDescription(false))
                        .build());
    }

    /**
     * Gère les états illégaux.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, WebRequest request) {

        log.warn("Illegal state: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.CONFLICT.value())
                        .error("Conflict")
                        .message(ex.getMessage())
                        .path(request.getDescription(false))
                        .build());
    }

    /**
     * Gère les exceptions génériques.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {

        String path = request.getDescription(false);
        log.error("Unexpected error at {}: {} - {}", path, ex.getClass().getSimpleName(), ex.getMessage(), ex);

        // Pour le debugging, inclure le message réel de l'exception
        String errorMessage = ex.getMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = ex.getClass().getSimpleName();
        }

        // Extraire la cause racine si présente
        Throwable rootCause = ex;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String rootMessage = rootCause.getMessage();
        if (rootMessage != null && !rootMessage.equals(errorMessage)) {
            errorMessage = errorMessage + " (root: " + rootMessage + ")";
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error("Internal Server Error")
                        .message(errorMessage)
                        .path(path)
                        .build());
    }

    /**
     * Réponse d'erreur structurée.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorResponse {

        @Builder.Default
        private LocalDateTime timestamp = LocalDateTime.now();

        private int status;
        private String error;
        private String message;
        private String path;
        private String errorCode;
        private Map<String, String> validationErrors;
    }
}