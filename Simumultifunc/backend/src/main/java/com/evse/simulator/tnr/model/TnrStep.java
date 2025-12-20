package com.evse.simulator.tnr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Représente un step Gherkin (Given/When/Then/And/But).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrStep {

    /**
     * Type de step Gherkin.
     */
    public enum StepType {
        GIVEN, WHEN, THEN, AND, BUT
    }

    /** Identifiant unique du step */
    private String id;

    /** Ordre d'exécution dans le scénario */
    @Builder.Default
    private int order = 0;

    /** Type du step */
    private StepType type;

    /** Keyword original (GIVEN, WHEN, THEN, etc.) */
    private String keyword;

    /** Texte complet du step (après le keyword) */
    private String text;

    /** Pattern regex correspondant */
    private String pattern;

    /** Paramètres extraits du texte */
    @Builder.Default
    private List<String> parameters = new ArrayList<>();

    /** Paramètres nommés pour l'exécution */
    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /** DataTable associée (si présente) */
    @Builder.Default
    private List<Map<String, String>> dataTable = new ArrayList<>();

    /** DocString associé (si présent) */
    private String docString;

    /** Variables à résoudre ${...} */
    @Builder.Default
    private Map<String, String> variables = new HashMap<>();

    /** Ligne dans le fichier source */
    private int sourceLine;

    /** Step optionnel (ne fait pas échouer le scénario) */
    @Builder.Default
    private boolean optional = false;

    /** Timeout spécifique pour ce step en ms */
    @Builder.Default
    private long timeoutMs = 30000;

    /** Nombre de tentatives en cas d'échec */
    @Builder.Default
    private int retryCount = 0;

    /** Délai entre les tentatives en ms */
    @Builder.Default
    private long retryDelayMs = 1000;

    /**
     * Retourne le type effectif (AND/BUT héritent du step précédent).
     */
    public StepType getEffectiveType(StepType previousType) {
        if (type == StepType.AND || type == StepType.BUT) {
            return previousType != null ? previousType : StepType.GIVEN;
        }
        return type;
    }

    /**
     * Vérifie si le step a une DataTable.
     */
    public boolean hasDataTable() {
        return dataTable != null && !dataTable.isEmpty();
    }

    /**
     * Vérifie si le step a un DocString.
     */
    public boolean hasDocString() {
        return docString != null && !docString.isEmpty();
    }

    /**
     * Retourne le texte complet avec keyword.
     */
    public String getFullText() {
        return type.name() + " " + text;
    }
}
