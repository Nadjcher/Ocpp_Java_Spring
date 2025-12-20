package com.evse.simulator.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Reponse API standard.
 */
@Schema(description = "Reponse API standard")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(

    @Schema(description = "Succes", example = "true")
    boolean success,

    @Schema(description = "Message", example = "Operation reussie")
    String message,

    @Schema(description = "Donnees")
    T data,

    @Schema(description = "Erreur (si echec)")
    String error,

    @Schema(description = "Timestamp")
    String timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, null, Instant.now().toString());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now().toString());
    }

    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(false, null, null, error, Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(String message, String error) {
        return new ApiResponse<>(false, message, null, error, Instant.now().toString());
    }
}
