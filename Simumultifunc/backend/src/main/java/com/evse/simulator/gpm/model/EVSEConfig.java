package com.evse.simulator.gpm.model;

import com.evse.simulator.gpm.model.enums.GPMChargeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration d'une borne EVSE pour le simulateur GPM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EVSEConfig {

    private String evseId;
    private String nodeId;
    private List<GPMChargeType> types;
    private String name;

    /**
     * Vérifie si cette borne supporte un type de charge donné.
     */
    public boolean supportsType(GPMChargeType type) {
        return types != null && types.contains(type);
    }
}
