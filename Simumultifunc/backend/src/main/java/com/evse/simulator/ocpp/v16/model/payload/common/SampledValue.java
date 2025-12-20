package com.evse.simulator.ocpp.v16.model.payload.common;

import com.evse.simulator.ocpp.v16.model.types.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structure SampledValue OCPP 1.6.
 * Représente une valeur mesurée.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SampledValue {

    /**
     * Valeur mesurée.
     */
    private String value;

    /**
     * Contexte de la lecture.
     */
    private ReadingContext context;

    /**
     * Format de la valeur.
     */
    private ValueFormat format;

    /**
     * Type de mesure.
     */
    private Measurand measurand;

    /**
     * Phase électrique.
     */
    private Phase phase;

    /**
     * Localisation de la mesure.
     */
    private Location location;

    /**
     * Unité de mesure.
     */
    private UnitOfMeasure unit;

    /**
     * Convertit en Map pour les réponses OCPP.
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("value", value);
        if (context != null) {
            map.put("context", context.getValue());
        }
        if (format != null) {
            map.put("format", format.getValue());
        }
        if (measurand != null) {
            map.put("measurand", measurand.getValue());
        }
        if (phase != null) {
            map.put("phase", phase.getValue());
        }
        if (location != null) {
            map.put("location", location.getValue());
        }
        if (unit != null) {
            map.put("unit", unit.getValue());
        }
        return map;
    }
}
