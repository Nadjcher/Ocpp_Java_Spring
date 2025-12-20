package com.evse.simulator.ocpp.v16.model.payload.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structure KeyValue OCPP 1.6.
 * Utilisé pour les clés de configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyValue {

    /**
     * Clé de configuration (max 50 chars).
     */
    private String key;

    /**
     * Indique si la clé est en lecture seule.
     */
    private Boolean readonly;

    /**
     * Valeur de la configuration (max 500 chars, optionnel).
     */
    private String value;

    /**
     * Convertit en Map pour les réponses OCPP.
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("key", key);
        if (readonly != null) {
            map.put("readonly", readonly);
        }
        if (value != null) {
            map.put("value", value);
        }
        return map;
    }
}
