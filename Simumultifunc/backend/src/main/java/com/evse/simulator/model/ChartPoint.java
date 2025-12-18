package com.evse.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Point de données pour les graphiques temps réel.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartPoint {

    /**
     * Horodatage du point.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Valeur du point.
     */
    private double value;

    /**
     * Label optionnel.
     */
    private String label;

    /**
     * Crée un point avec timestamp actuel.
     */
    public static ChartPoint of(double value) {
        return ChartPoint.builder()
                .value(value)
                .build();
    }

    /**
     * Crée un point avec timestamp et label.
     */
    public static ChartPoint of(double value, String label) {
        return ChartPoint.builder()
                .value(value)
                .label(label)
                .build();
    }

    /**
     * Crée un point avec timestamp spécifique.
     */
    public static ChartPoint of(LocalDateTime timestamp, double value) {
        return ChartPoint.builder()
                .timestamp(timestamp)
                .value(value)
                .build();
    }
}