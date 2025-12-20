package com.evse.simulator.ocpp.v16.model.payload.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structure AuthorizationData OCPP 1.6.
 * Utilisé pour SendLocalList.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthorizationData {

    /**
     * Identifiant du badge (max 20 chars).
     */
    private String idTag;

    /**
     * Informations sur l'autorisation (optionnel pour UpdateType.Full).
     */
    private IdTagInfo idTagInfo;

    /**
     * Convertit en Map pour les réponses OCPP.
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("idTag", idTag);
        if (idTagInfo != null) {
            map.put("idTagInfo", idTagInfo.toMap());
        }
        return map;
    }

    /**
     * Parse depuis une Map.
     */
    @SuppressWarnings("unchecked")
    public static AuthorizationData fromMap(java.util.Map<String, Object> map) {
        if (map == null) return null;

        AuthorizationData data = new AuthorizationData();
        data.setIdTag((String) map.get("idTag"));

        Object idTagInfoObj = map.get("idTagInfo");
        if (idTagInfoObj instanceof java.util.Map) {
            java.util.Map<String, Object> infoMap = (java.util.Map<String, Object>) idTagInfoObj;
            IdTagInfo info = new IdTagInfo();
            String status = (String) infoMap.get("status");
            if (status != null) {
                info.setStatus(com.evse.simulator.ocpp.v16.model.types.AuthorizationStatus.fromValue(status));
            }
            data.setIdTagInfo(info);
        }

        return data;
    }
}
