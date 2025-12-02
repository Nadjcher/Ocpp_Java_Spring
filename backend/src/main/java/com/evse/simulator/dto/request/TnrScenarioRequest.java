package com.evse.simulator.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Requête de création/mise à jour de scénario TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrScenarioRequest {

    /**
     * Nom du scénario.
     */
    @NotBlank(message = "Le nom du scénario est obligatoire")
    private String name;

    /**
     * Description du scénario.
     */
    private String description;

    /**
     * Catégorie du scénario.
     */
    private String category;

    /**
     * Tags pour la recherche.
     */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /**
     * Étapes du scénario.
     */
    @Builder.Default
    private List<TnrStepRequest> steps = new ArrayList<>();

    /**
     * Configuration du scénario.
     */
    private TnrConfigRequest config;

    /**
     * Auteur du scénario.
     */
    private String author;

    /**
     * Version du scénario.
     */
    @Builder.Default
    private String version = "1.0.0";

    /**
     * Étape de scénario TNR.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TnrStepRequest {
        private int order;
        private String name;
        private String type;
        private String action;
        private Object payload;
        @Builder.Default
        private long delayMs = 0;
        @Builder.Default
        private long timeoutMs = 30000;
        @Builder.Default
        private List<TnrAssertionRequest> assertions = new ArrayList<>();
    }

    /**
     * Assertion TNR.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TnrAssertionRequest {
        private String type;
        private String path;
        @Builder.Default
        private String operator = "EQUALS";
        private Object expected;
        private String message;
    }

    /**
     * Configuration TNR.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TnrConfigRequest {
        private String csmsUrl;
        private String cpId;
        @Builder.Default
        private int repeatCount = 1;
        @Builder.Default
        private boolean continueOnError = false;
        @Builder.Default
        private long globalTimeoutMs = 300000;
        private Map<String, Object> variables;
    }
}