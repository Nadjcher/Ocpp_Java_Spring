package com.evse.simulator.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Résultat de la comparaison entre deux exécutions TNR.
 */
@Data
@Builder
public class TnrPlusCompareResult {
    /**
     * ID de l'exécution de référence (baseline).
     */
    private String baselineId;

    /**
     * ID de l'exécution courante.
     */
    private String currentId;

    /**
     * Indique si les signatures correspondent.
     */
    private boolean signatureMatch;

    /**
     * Nombre total d'événements dans la baseline.
     */
    private int totalEventsBaseline;

    /**
     * Nombre total d'événements dans l'exécution courante.
     */
    private int totalEventsCurrent;

    /**
     * Nombre de différences trouvées.
     */
    private int differencesCount;

    /**
     * Liste détaillée des différences.
     */
    private List<TNRDifference> differences;
}