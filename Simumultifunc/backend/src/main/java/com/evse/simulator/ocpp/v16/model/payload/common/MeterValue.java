package com.evse.simulator.ocpp.v16.model.payload.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Structure MeterValue OCPP 1.6.
 * Contient une collection de valeurs mesurées à un instant donné.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeterValue {

    /**
     * Horodatage de la mesure.
     */
    private Instant timestamp;

    /**
     * Liste des valeurs mesurées.
     */
    @Builder.Default
    private List<SampledValue> sampledValue = new ArrayList<>();

    /**
     * Convertit en Map pour les réponses OCPP.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("timestamp", timestamp != null ? timestamp.toString() : Instant.now().toString());
        if (sampledValue != null && !sampledValue.isEmpty()) {
            map.put("sampledValue", sampledValue.stream()
                    .map(SampledValue::toMap)
                    .collect(Collectors.toList()));
        }
        return map;
    }
}
