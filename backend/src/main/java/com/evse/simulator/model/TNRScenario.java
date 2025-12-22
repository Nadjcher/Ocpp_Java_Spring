package com.evse.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scénario de test non-régressif (TNR).
 * <p>
 * Définit une séquence d'actions OCPP à exécuter pour valider
 * le comportement du simulateur ou du CSMS.
 * </p>
 * <p>
 * Annotation @Document pour MongoDB (optionnel - fonctionne aussi sans MongoDB).
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "tnr_scenarios")
public class TNRScenario {

    /**
     * Identifiant unique du scénario.
     */
    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /**
     * Nom du scénario.
     */
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
    private List<TNRStep> steps = new ArrayList<>();

    /**
     * Configuration du scénario.
     */
    @Builder.Default
    private TNRConfig config = new TNRConfig();

    /**
     * État du scénario.
     */
    @Builder.Default
    private ScenarioStatus status = ScenarioStatus.PENDING;

    /**
     * Résultats de la dernière exécution.
     */
    private TNRResult lastResult;

    /**
     * Date de création.
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Date de dernière modification.
     */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Date de dernière exécution.
     */
    private LocalDateTime lastRunAt;

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
     * Actif ou archivé.
     */
    @Builder.Default
    private boolean active = true;

    /**
     * État du scénario.
     */
    public enum ScenarioStatus {
        PENDING,
        RUNNING,
        PASSED,
        FAILED,
        SKIPPED,
        ERROR
    }

    /**
     * Étape d'un scénario TNR.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TNRStep {

        /**
         * Numéro de l'étape.
         */
        private int order;

        /**
         * Nom de l'étape.
         */
        private String name;

        /**
         * Type d'action.
         */
        private StepType type;

        /**
         * Action OCPP à exécuter.
         */
        private String action;

        /**
         * Payload de l'action.
         */
        private Object payload;

        /**
         * Délai avant exécution en ms.
         */
        @Builder.Default
        private long delayMs = 0;

        /**
         * Timeout de l'étape en ms.
         */
        @Builder.Default
        private long timeoutMs = 30000;

        /**
         * Conditions de validation.
         */
        @Builder.Default
        private List<TNRAssertion> assertions = new ArrayList<>();

        /**
         * Résultat de l'étape.
         */
        private StepResult result;
    }

    /**
     * Type d'étape.
     */
    public enum StepType {
        CONNECT,
        DISCONNECT,
        SEND_OCPP,
        WAIT_OCPP,
        ASSERT_STATE,
        DELAY,
        SET_VALUE,
        CUSTOM
    }

    /**
     * Assertion de validation.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TNRAssertion {

        /**
         * Type d'assertion.
         */
        private AssertionType type;

        /**
         * Chemin JSON de la valeur à vérifier.
         */
        private String path;

        /**
         * Opérateur de comparaison.
         */
        @Builder.Default
        private Operator operator = Operator.EQUALS;

        /**
         * Valeur attendue.
         */
        private Object expected;

        /**
         * Message d'erreur personnalisé.
         */
        private String message;

        /**
         * Résultat de l'assertion.
         */
        private Boolean passed;

        /**
         * Valeur réelle trouvée.
         */
        private Object actual;
    }

    /**
     * Type d'assertion.
     */
    public enum AssertionType {
        RESPONSE_STATUS,
        RESPONSE_FIELD,
        SESSION_STATE,
        SESSION_FIELD,
        CUSTOM
    }

    /**
     * Opérateur de comparaison.
     */
    public enum Operator {
        EQUALS,
        NOT_EQUALS,
        CONTAINS,
        NOT_CONTAINS,
        GREATER_THAN,
        LESS_THAN,
        REGEX,
        IS_NULL,
        IS_NOT_NULL
    }

    /**
     * Résultat d'une étape.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StepResult {
        private boolean passed;
        private String message;
        private long durationMs;
        private Object response;
        private String error;
    }

    /**
     * Configuration du scénario.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TNRConfig {

        /**
         * URL du CSMS à utiliser.
         */
        private String csmsUrl;

        /**
         * ID du Charge Point.
         */
        private String cpId;

        /**
         * Nombre de répétitions.
         */
        @Builder.Default
        private int repeatCount = 1;

        /**
         * Continuer après erreur.
         */
        @Builder.Default
        private boolean continueOnError = false;

        /**
         * Timeout global en ms.
         */
        @Builder.Default
        private long globalTimeoutMs = 300000;

        /**
         * Variables du scénario.
         */
        @Builder.Default
        private java.util.Map<String, Object> variables = new java.util.HashMap<>();
    }

    /**
     * Résultat complet d'un scénario.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TNRResult {

        /**
         * ID du scénario.
         */
        private String scenarioId;

        /**
         * Statut global.
         */
        private ScenarioStatus status;

        /**
         * Nombre d'étapes passées.
         */
        private int passedSteps;

        /**
         * Nombre d'étapes échouées.
         */
        private int failedSteps;

        /**
         * Nombre total d'étapes.
         */
        private int totalSteps;

        /**
         * Durée totale en ms.
         */
        private long durationMs;

        /**
         * Date d'exécution.
         */
        @Builder.Default
        private LocalDateTime executedAt = LocalDateTime.now();

        /**
         * Résultats par étape.
         */
        @Builder.Default
        private List<StepResult> stepResults = new ArrayList<>();

        /**
         * Message d'erreur global.
         */
        private String errorMessage;

        /**
         * Logs d'exécution.
         */
        @Builder.Default
        private List<String> logs = new ArrayList<>();
    }
}