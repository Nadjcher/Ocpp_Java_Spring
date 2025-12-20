package com.evse.simulator.tnr.validation;

import com.evse.simulator.tnr.model.OcppMessageCapture;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validateur de séquences de messages OCPP.
 * <p>
 * Permet de vérifier l'ordre, le timing et la présence de messages
 * dans une séquence d'échanges OCPP.
 * </p>
 *
 * @example
 * <pre>
 * SequenceValidator validator = new SequenceValidator();
 * validator.expect("BootNotification");
 * validator.expect("StatusNotification").after("BootNotification");
 * validator.expect("Heartbeat").within(Duration.ofSeconds(60)).atLeast(1);
 *
 * ValidationResult result = validator.validate(messageHistory);
 * </pre>
 */
@Slf4j
@Component
public class SequenceValidator {

    /**
     * Valide une séquence de messages contre des règles.
     *
     * @param messages Messages OCPP capturés
     * @param rules    Règles de séquence
     * @return Résultat de validation
     */
    public SequenceValidationResult validate(List<OcppMessageCapture> messages, List<SequenceRule> rules) {
        SequenceValidationResult result = new SequenceValidationResult();
        result.setTotalRules(rules.size());

        for (SequenceRule rule : rules) {
            RuleValidationResult ruleResult = validateRule(messages, rule);
            result.addRuleResult(ruleResult);
        }

        result.calculateSummary();
        return result;
    }

    /**
     * Valide une règle individuelle.
     */
    private RuleValidationResult validateRule(List<OcppMessageCapture> messages, SequenceRule rule) {
        RuleValidationResult result = RuleValidationResult.builder()
                .rule(rule)
                .build();

        // Trouver les messages correspondants
        List<OcppMessageCapture> matching = findMatchingMessages(messages, rule);
        result.setMatchingMessages(matching);
        result.setMatchCount(matching.size());

        // Vérifier le nombre d'occurrences
        if (!validateCount(matching.size(), rule)) {
            result.setPassed(false);
            result.setMessage(buildCountErrorMessage(matching.size(), rule));
            return result;
        }

        // Vérifier l'ordre si spécifié
        if (rule.getAfterAction() != null && !matching.isEmpty()) {
            if (!validateOrder(messages, matching.get(0), rule.getAfterAction())) {
                result.setPassed(false);
                result.setMessage(String.format("'%s' should appear after '%s'",
                        rule.getAction(), rule.getAfterAction()));
                return result;
            }
        }

        // Vérifier le timing si spécifié
        if (rule.getWithinDuration() != null && !matching.isEmpty()) {
            Instant reference = rule.getReferenceTime() != null ?
                    rule.getReferenceTime() : messages.get(0).getTimestamp();

            if (!validateTiming(matching, reference, rule.getWithinDuration())) {
                result.setPassed(false);
                result.setMessage(String.format("'%s' not received within %s",
                        rule.getAction(), rule.getWithinDuration()));
                return result;
            }
        }

        // Vérifier l'intervalle entre messages si spécifié
        if (rule.getMinInterval() != null && matching.size() > 1) {
            if (!validateInterval(matching, rule.getMinInterval(), rule.getMaxInterval())) {
                result.setPassed(false);
                result.setMessage(String.format("Interval between '%s' messages out of bounds",
                        rule.getAction()));
                return result;
            }
        }

        result.setPassed(true);
        return result;
    }

    /**
     * Trouve les messages correspondant à une règle.
     */
    private List<OcppMessageCapture> findMatchingMessages(List<OcppMessageCapture> messages, SequenceRule rule) {
        List<OcppMessageCapture> matching = new ArrayList<>();

        for (OcppMessageCapture msg : messages) {
            if (matchesAction(msg, rule)) {
                if (rule.getDirection() == null || rule.getDirection() == msg.getDirection()) {
                    matching.add(msg);
                }
            }
        }

        return matching;
    }

    private boolean matchesAction(OcppMessageCapture msg, SequenceRule rule) {
        if (msg.getAction() == null) return false;

        if (rule.getActionPattern() != null) {
            return rule.getActionPattern().matcher(msg.getAction()).matches();
        }

        return rule.getAction().equalsIgnoreCase(msg.getAction());
    }

    private boolean validateCount(int count, SequenceRule rule) {
        if (rule.getExpectedCount() != null) {
            return count == rule.getExpectedCount();
        }
        if (rule.getMinCount() != null && count < rule.getMinCount()) {
            return false;
        }
        if (rule.getMaxCount() != null && count > rule.getMaxCount()) {
            return false;
        }
        return count > 0 || !rule.isRequired();
    }

    private boolean validateOrder(List<OcppMessageCapture> messages,
                                   OcppMessageCapture target,
                                   String afterAction) {
        int targetIndex = messages.indexOf(target);
        for (int i = 0; i < targetIndex; i++) {
            if (afterAction.equalsIgnoreCase(messages.get(i).getAction())) {
                return true;
            }
        }
        return false;
    }

    private boolean validateTiming(List<OcppMessageCapture> matching,
                                    Instant reference,
                                    Duration within) {
        for (OcppMessageCapture msg : matching) {
            Duration elapsed = Duration.between(reference, msg.getTimestamp());
            if (elapsed.compareTo(within) <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean validateInterval(List<OcppMessageCapture> matching,
                                      Duration minInterval,
                                      Duration maxInterval) {
        for (int i = 1; i < matching.size(); i++) {
            Duration interval = Duration.between(
                    matching.get(i - 1).getTimestamp(),
                    matching.get(i).getTimestamp());

            if (minInterval != null && interval.compareTo(minInterval) < 0) {
                return false;
            }
            if (maxInterval != null && interval.compareTo(maxInterval) > 0) {
                return false;
            }
        }
        return true;
    }

    private String buildCountErrorMessage(int actual, SequenceRule rule) {
        if (rule.getExpectedCount() != null) {
            return String.format("Expected exactly %d '%s' messages but found %d",
                    rule.getExpectedCount(), rule.getAction(), actual);
        }
        if (rule.getMinCount() != null && actual < rule.getMinCount()) {
            return String.format("Expected at least %d '%s' messages but found %d",
                    rule.getMinCount(), rule.getAction(), actual);
        }
        if (rule.getMaxCount() != null && actual > rule.getMaxCount()) {
            return String.format("Expected at most %d '%s' messages but found %d",
                    rule.getMaxCount(), rule.getAction(), actual);
        }
        if (rule.isRequired() && actual == 0) {
            return String.format("Expected at least one '%s' message but found none",
                    rule.getAction());
        }
        return "Unknown validation error";
    }

    // =========================================================================
    // Builder pour créer des règles
    // =========================================================================

    /**
     * Crée un builder de règle pour une action.
     */
    public static SequenceRuleBuilder expect(String action) {
        return new SequenceRuleBuilder(action);
    }

    public static class SequenceRuleBuilder {
        private final SequenceRule rule;

        public SequenceRuleBuilder(String action) {
            this.rule = new SequenceRule();
            this.rule.setAction(action);
            this.rule.setRequired(true);
        }

        public SequenceRuleBuilder matching(String pattern) {
            rule.setActionPattern(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
            return this;
        }

        public SequenceRuleBuilder after(String action) {
            rule.setAfterAction(action);
            return this;
        }

        public SequenceRuleBuilder within(Duration duration) {
            rule.setWithinDuration(duration);
            return this;
        }

        public SequenceRuleBuilder exactly(int count) {
            rule.setExpectedCount(count);
            return this;
        }

        public SequenceRuleBuilder atLeast(int count) {
            rule.setMinCount(count);
            return this;
        }

        public SequenceRuleBuilder atMost(int count) {
            rule.setMaxCount(count);
            return this;
        }

        public SequenceRuleBuilder optional() {
            rule.setRequired(false);
            return this;
        }

        public SequenceRuleBuilder outbound() {
            rule.setDirection(OcppMessageCapture.Direction.OUTBOUND);
            return this;
        }

        public SequenceRuleBuilder inbound() {
            rule.setDirection(OcppMessageCapture.Direction.INBOUND);
            return this;
        }

        public SequenceRuleBuilder withInterval(Duration min, Duration max) {
            rule.setMinInterval(min);
            rule.setMaxInterval(max);
            return this;
        }

        public SequenceRuleBuilder referenceTime(Instant time) {
            rule.setReferenceTime(time);
            return this;
        }

        public SequenceRule build() {
            return rule;
        }
    }

    // =========================================================================
    // Modèles
    // =========================================================================

    /**
     * Règle de séquence.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SequenceRule {
        private String action;
        private Pattern actionPattern;
        private OcppMessageCapture.Direction direction;
        private String afterAction;
        private Duration withinDuration;
        private Instant referenceTime;
        private Integer expectedCount;
        private Integer minCount;
        private Integer maxCount;
        private Duration minInterval;
        private Duration maxInterval;
        private boolean required;
    }

    /**
     * Résultat de validation d'une règle.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RuleValidationResult {
        private SequenceRule rule;
        private boolean passed;
        private int matchCount;
        private List<OcppMessageCapture> matchingMessages;
        private String message;
    }

    /**
     * Résultat global de validation de séquence.
     */
    @Data
    public static class SequenceValidationResult {
        private int totalRules;
        private int passedRules;
        private int failedRules;
        private boolean success;
        private List<RuleValidationResult> ruleResults = new ArrayList<>();

        public void addRuleResult(RuleValidationResult result) {
            ruleResults.add(result);
        }

        public void calculateSummary() {
            passedRules = (int) ruleResults.stream().filter(RuleValidationResult::isPassed).count();
            failedRules = totalRules - passedRules;
            success = failedRules == 0;
        }

        public List<RuleValidationResult> getFailures() {
            return ruleResults.stream().filter(r -> !r.isPassed()).toList();
        }

        public String getSummary() {
            return String.format("%d/%d sequence rules passed%s",
                    passedRules, totalRules,
                    success ? "" : " (" + failedRules + " failed)");
        }
    }
}
