package com.evse.simulator.tnr.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Représente une assertion dans un step TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrAssertion {

    /**
     * Type de matcher.
     */
    public enum MatcherType {
        EQUALS,
        NOT_EQUALS,
        CONTAINS,
        NOT_CONTAINS,
        MATCHES,        // Regex
        GREATER_THAN,
        LESS_THAN,
        GREATER_OR_EQUAL,
        LESS_OR_EQUAL,
        BETWEEN,
        IS_NULL,
        IS_NOT_NULL,
        IS_EMPTY,
        IS_NOT_EMPTY,
        HAS_SIZE,
        IS_ONE_OF,
        JSON_PATH,
        SCHEMA
    }

    /** Type de matcher utilisé */
    private MatcherType matcherType;

    /** Chemin JSONPath ou nom du champ */
    private String path;

    /** Valeur attendue */
    private Object expected;

    /** Valeur réelle */
    private Object actual;

    /** Résultat de l'assertion */
    private boolean passed;

    /** Message descriptif */
    private String message;

    /** Message d'erreur si échec */
    private String errorMessage;

    /** Timestamp */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /** Tolérance pour comparaisons numériques */
    private Double tolerance;

    /** Valeur min pour BETWEEN */
    private Object minValue;

    /** Valeur max pour BETWEEN */
    private Object maxValue;

    /**
     * Crée une assertion réussie.
     */
    public static TnrAssertion passed(String path, MatcherType matcher, Object expected, Object actual) {
        return TnrAssertion.builder()
            .path(path)
            .matcherType(matcher)
            .expected(expected)
            .actual(actual)
            .passed(true)
            .message(String.format("%s %s %s (actual: %s) ✓", path, matcher, expected, actual))
            .build();
    }

    /**
     * Crée une assertion échouée.
     */
    public static TnrAssertion failed(String path, MatcherType matcher, Object expected, Object actual, String error) {
        return TnrAssertion.builder()
            .path(path)
            .matcherType(matcher)
            .expected(expected)
            .actual(actual)
            .passed(false)
            .message(String.format("%s %s %s (actual: %s) ✗", path, matcher, expected, actual))
            .errorMessage(error)
            .build();
    }
}
