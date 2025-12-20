package com.evse.simulator.ocpp.v16.model.payload.common;

import com.evse.simulator.ocpp.v16.model.types.AuthorizationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Structure IdTagInfo OCPP 1.6.
 * Contient les informations sur un identifiant utilisateur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdTagInfo {

    /**
     * Status de l'autorisation.
     */
    private AuthorizationStatus status;

    /**
     * Date d'expiration de l'autorisation (optionnel).
     */
    private LocalDateTime expiryDate;

    /**
     * Identifiant parent (optionnel, max 20 chars).
     */
    private String parentIdTag;

    /**
     * Crée un IdTagInfo accepté.
     */
    public static IdTagInfo accepted() {
        return IdTagInfo.builder()
                .status(AuthorizationStatus.ACCEPTED)
                .build();
    }

    /**
     * Crée un IdTagInfo refusé.
     */
    public static IdTagInfo invalid() {
        return IdTagInfo.builder()
                .status(AuthorizationStatus.INVALID)
                .build();
    }

    /**
     * Crée un IdTagInfo bloqué.
     */
    public static IdTagInfo blocked() {
        return IdTagInfo.builder()
                .status(AuthorizationStatus.BLOCKED)
                .build();
    }

    /**
     * Crée un IdTagInfo expiré.
     */
    public static IdTagInfo expired() {
        return IdTagInfo.builder()
                .status(AuthorizationStatus.EXPIRED)
                .build();
    }

    /**
     * Convertit en Map pour les réponses OCPP.
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        if (status != null) {
            map.put("status", status.getValue());
        }
        if (expiryDate != null) {
            map.put("expiryDate", expiryDate.toString());
        }
        if (parentIdTag != null) {
            map.put("parentIdTag", parentIdTag);
        }
        return map;
    }
}
