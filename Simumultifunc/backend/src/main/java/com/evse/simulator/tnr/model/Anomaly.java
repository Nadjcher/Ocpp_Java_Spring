package com.evse.simulator.tnr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Représente une anomalie détectée pendant l'exécution TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Anomaly {

    /**
     * Sévérité de l'anomalie.
     */
    public enum Severity {
        INFO,       // Information
        WARNING,    // Avertissement
        ERROR,      // Erreur
        CRITICAL    // Critique
    }

    /**
     * Type d'anomalie.
     */
    public enum Type {
        LATENCY_HIGH,           // Latence anormalement élevée
        LATENCY_SPIKE,          // Pic de latence
        RESPONSE_UNEXPECTED,    // Réponse inattendue
        SEQUENCE_VIOLATION,     // Violation de séquence
        STATE_INCONSISTENT,     // État incohérent
        TIMEOUT,                // Timeout
        ENERGY_MISMATCH,        // Incohérence énergie
        POWER_ANOMALY,          // Anomalie puissance
        RETRY_EXCESSIVE,        // Trop de retries
        MEMORY_LEAK,            // Fuite mémoire potentielle
        REGRESSION,             // Régression détectée
        PATTERN_BREAK           // Rupture de pattern
    }

    /** ID unique */
    private String id;

    /** Type d'anomalie */
    private Type type;

    /** Sévérité */
    private Severity severity;

    /** Message descriptif */
    private String message;

    /** Description détaillée */
    private String description;

    /** Scénario concerné */
    private String scenarioId;

    /** Step concerné */
    private String stepId;

    /** Timestamp de détection */
    @Builder.Default
    private Instant detectedAt = Instant.now();

    /** Valeur attendue */
    private Object expectedValue;

    /** Valeur réelle */
    private Object actualValue;

    /** Écart en pourcentage */
    private Double deviationPercent;

    /** Métadonnées supplémentaires */
    private Map<String, Object> metadata;

    /** Suggestion de correction */
    private String suggestion;

    /** Déjà vu (même anomalie détectée précédemment) */
    @Builder.Default
    private boolean recurring = false;

    /** Nombre d'occurrences */
    @Builder.Default
    private int occurrenceCount = 1;

    /**
     * Crée une anomalie de latence.
     */
    public static Anomaly latencyHigh(String scenarioId, long actualMs, long thresholdMs) {
        return Anomaly.builder()
            .type(Type.LATENCY_HIGH)
            .severity(actualMs > thresholdMs * 2 ? Severity.ERROR : Severity.WARNING)
            .message(String.format("Latence élevée: %dms (seuil: %dms)", actualMs, thresholdMs))
            .scenarioId(scenarioId)
            .expectedValue(thresholdMs)
            .actualValue(actualMs)
            .deviationPercent((double)(actualMs - thresholdMs) / thresholdMs * 100)
            .suggestion("Vérifier la charge serveur et les performances réseau")
            .build();
    }

    /**
     * Crée une anomalie de régression.
     */
    public static Anomaly regression(String scenarioId, String field, Object baseline, Object current) {
        return Anomaly.builder()
            .type(Type.REGRESSION)
            .severity(Severity.ERROR)
            .message(String.format("Régression détectée sur %s", field))
            .scenarioId(scenarioId)
            .expectedValue(baseline)
            .actualValue(current)
            .suggestion("Comparer avec la baseline et identifier le changement")
            .build();
    }

    /**
     * Crée une anomalie d'incohérence d'état.
     */
    public static Anomaly stateInconsistent(String scenarioId, String expectedState, String actualState) {
        return Anomaly.builder()
            .type(Type.STATE_INCONSISTENT)
            .severity(Severity.ERROR)
            .message(String.format("État incohérent: attendu %s, trouvé %s", expectedState, actualState))
            .scenarioId(scenarioId)
            .expectedValue(expectedState)
            .actualValue(actualState)
            .suggestion("Vérifier les transitions d'état et les conditions")
            .build();
    }
}
