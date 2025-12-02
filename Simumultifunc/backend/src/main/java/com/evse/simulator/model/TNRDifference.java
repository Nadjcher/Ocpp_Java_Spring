package com.evse.simulator.model;

import lombok.Data;

/**
 * Représente une différence trouvée lors de la comparaison TNR.
 */
@Data
public class TNRDifference {
    /**
     * Index de l'événement dans la séquence.
     */
    private Integer eventIndex;

    /**
     * Chemin JSON vers le champ différent.
     */
    private String path;

    /**
     * Valeur attendue.
     */
    private Object expected;

    /**
     * Valeur actuelle.
     */
    private Object actual;

    /**
     * Type de différence : missing, different, extra, error.
     */
    private String type;
}