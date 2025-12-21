package com.evse.simulator.gpm.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request pour l'API TTE Dry-Run regulation-ticks.
 * POST /qa/dry-run/regulation-ticks
 *
 * Format attendu par l'API:
 * {
 *   "rootId": "5434404181",
 *   "dryRunContext": { "id": "test-dryrun-001" },
 *   "clockOverride": "2025-12-18T16:08:00.000Z"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DryRunRegulationTickRequest {

    private String rootId;  // Root node ID
    private DryRunContext dryRunContext;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant clockOverride;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DryRunContext {
        private String id;
    }
}
