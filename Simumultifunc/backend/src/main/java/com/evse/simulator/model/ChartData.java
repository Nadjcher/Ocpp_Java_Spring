package com.evse.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Données de graphique pour diffusion WebSocket.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartData {
    private String sessionId;
    private ChartPoint socPoint;
    private ChartPoint powerPoint;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
