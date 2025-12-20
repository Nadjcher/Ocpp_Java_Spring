package com.evse.simulator.tnr.steps;

import com.evse.simulator.tnr.engine.TnrContext;
import com.evse.simulator.tnr.model.TnrStep;
import com.evse.simulator.tnr.model.TnrStepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registre central des step definitions.
 * Permet d'enregistrer et exécuter des steps Gherkin.
 */
@Slf4j
@Component
public class StepRegistry {

    /**
     * Représente une définition de step enregistrée.
     */
    public record StepDefinition(
        Pattern pattern,
        Object instance,
        Method method,
        String description
    ) {}

    /** Steps Given enregistrés */
    private final Map<String, StepDefinition> givenSteps = new ConcurrentHashMap<>();

    /** Steps When enregistrés */
    private final Map<String, StepDefinition> whenSteps = new ConcurrentHashMap<>();

    /** Steps Then enregistrés */
    private final Map<String, StepDefinition> thenSteps = new ConcurrentHashMap<>();

    /** Cache des patterns compilés */
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    /** Toutes les définitions pour lookup rapide */
    private final List<StepDefinition> allDefinitions = Collections.synchronizedList(new ArrayList<>());

    @PostConstruct
    public void init() {
        log.info("StepRegistry initialized with {} Given, {} When, {} Then steps",
            givenSteps.size(), whenSteps.size(), thenSteps.size());
    }

    // =========================================================================
    // Enregistrement des steps
    // =========================================================================

    /**
     * Enregistre un step Given.
     */
    public void registerGiven(String patternStr, Object instance, Method method, String description) {
        Pattern pattern = compilePattern(patternStr);
        StepDefinition def = new StepDefinition(pattern, instance, method, description);
        givenSteps.put(patternStr, def);
        allDefinitions.add(def);
        log.debug("Registered Given step: {}", patternStr);
    }

    /**
     * Enregistre un step When.
     */
    public void registerWhen(String patternStr, Object instance, Method method, String description) {
        Pattern pattern = compilePattern(patternStr);
        StepDefinition def = new StepDefinition(pattern, instance, method, description);
        whenSteps.put(patternStr, def);
        allDefinitions.add(def);
        log.debug("Registered When step: {}", patternStr);
    }

    /**
     * Enregistre un step Then.
     */
    public void registerThen(String patternStr, Object instance, Method method, String description) {
        Pattern pattern = compilePattern(patternStr);
        StepDefinition def = new StepDefinition(pattern, instance, method, description);
        thenSteps.put(patternStr, def);
        allDefinitions.add(def);
        log.debug("Registered Then step: {}", patternStr);
    }

    /**
     * Compile un pattern Gherkin en regex Java.
     */
    private Pattern compilePattern(String patternStr) {
        return patternCache.computeIfAbsent(patternStr, p -> {
            // Convertir les placeholders Cucumber-style en regex
            String regex = p
                // {word} -> capture mot
                .replaceAll("\\{word\\}", "(\\\\w+)")
                // {string} -> capture chaîne entre guillemets
                .replaceAll("\\{string\\}", "\"([^\"]+)\"")
                // {int} -> capture entier
                .replaceAll("\\{int\\}", "(\\\\d+)")
                // {long} -> capture entier long
                .replaceAll("\\{long\\}", "(\\\\d+)")
                // {float} -> capture décimal
                .replaceAll("\\{float\\}", "(\\\\d+\\\\.?\\\\d*)")
                // {double} -> capture décimal (alias de float)
                .replaceAll("\\{double\\}", "(\\\\d+\\\\.?\\\\d*)")
                // {any} -> capture tout
                .replaceAll("\\{any\\}", "(.+)")
                // Escape special chars
                .replace("(", "\\(")
                .replace(")", "\\)")
                // Restore captures
                .replace("\\\\(", "(")
                .replace("\\\\)", ")");

            return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE);
        });
    }

    // =========================================================================
    // Exécution des steps
    // =========================================================================

    /**
     * Exécute un step en trouvant le handler correspondant.
     */
    public boolean executeStep(TnrStep step, TnrContext context, TnrStepResult result) {
        Map<String, StepDefinition> registry = getRegistryForType(step.getType());

        // Essayer de trouver une correspondance
        for (StepDefinition def : allDefinitions) {
            Matcher matcher = def.pattern().matcher(step.getText());
            if (matcher.matches()) {
                try {
                    // Extraire les paramètres
                    List<Object> params = extractParameters(matcher, step, context);

                    // Invoquer la méthode
                    Object resultValue = invokeStepMethod(def, params, context, step);

                    // Stocker le résultat
                    if (resultValue != null) {
                        context.set("lastResult", resultValue);
                    }

                    result.setStatus(TnrStepResult.Status.PASSED);
                    return true;

                } catch (AssertionError e) {
                    result.setStatus(TnrStepResult.Status.FAILED);
                    result.setErrorMessage(e.getMessage());
                    throw e;
                } catch (Exception e) {
                    result.setStatus(TnrStepResult.Status.ERROR);
                    result.setErrorMessage(e.getMessage());
                    throw new RuntimeException("Step execution failed: " + e.getMessage(), e);
                }
            }
        }

        return false; // Aucun handler trouvé
    }

    /**
     * Retourne le registre correspondant au type de step.
     */
    private Map<String, StepDefinition> getRegistryForType(TnrStep.StepType type) {
        return switch (type) {
            case GIVEN -> givenSteps;
            case WHEN -> whenSteps;
            case THEN -> thenSteps;
            case AND, BUT -> givenSteps; // AND/BUT utilisent le contexte
        };
    }

    /**
     * Extrait les paramètres d'un matcher.
     */
    private List<Object> extractParameters(Matcher matcher, TnrStep step, TnrContext context) {
        List<Object> params = new ArrayList<>();

        // Groupes du regex
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String value = matcher.group(i);
            // Résoudre les variables
            value = context.resolveVariables(value);
            params.add(convertParameter(value));
        }

        // DataTable si présente
        if (step.hasDataTable()) {
            params.add(step.getDataTable());
        }

        // DocString si présent
        if (step.hasDocString()) {
            params.add(context.resolveVariables(step.getDocString()));
        }

        return params;
    }

    /**
     * Convertit un paramètre string vers le type approprié.
     */
    private Object convertParameter(String value) {
        if (value == null) return null;

        // Essayer int
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {}

        // Essayer double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}

        // Essayer boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }

        return value;
    }

    /**
     * Invoque la méthode du step.
     */
    private Object invokeStepMethod(StepDefinition def, List<Object> params,
                                     TnrContext context, TnrStep step) throws Exception {
        Method method = def.method();
        Object instance = def.instance();

        // Construire les arguments selon la signature de la méthode
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        int paramIndex = 0;
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> type = paramTypes[i];

            if (type.equals(TnrContext.class)) {
                args[i] = context;
            } else if (type.equals(TnrStep.class)) {
                args[i] = step;
            } else if (type.equals(List.class) && paramIndex < params.size() &&
                       params.get(paramIndex) instanceof List) {
                args[i] = params.get(paramIndex++);
            } else if (paramIndex < params.size()) {
                args[i] = convertToType(params.get(paramIndex++), type);
            }
        }

        return method.invoke(instance, args);
    }

    /**
     * Convertit une valeur vers un type spécifique.
     */
    private Object convertToType(Object value, Class<?> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return value;

        String strValue = value.toString();

        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(strValue);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(strValue);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(strValue);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(strValue);
        }
        if (type == String.class) {
            return strValue;
        }

        return value;
    }

    // =========================================================================
    // Introspection
    // =========================================================================

    /**
     * Retourne toutes les définitions enregistrées.
     */
    public List<StepDefinition> getAllDefinitions() {
        return new ArrayList<>(allDefinitions);
    }

    /**
     * Vérifie si un step a un handler.
     */
    public boolean hasHandler(TnrStep step) {
        for (StepDefinition def : allDefinitions) {
            if (def.pattern().matcher(step.getText()).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retourne les steps non implémentés.
     */
    public List<String> getUndefinedSteps(List<TnrStep> steps) {
        List<String> undefined = new ArrayList<>();
        for (TnrStep step : steps) {
            if (!hasHandler(step)) {
                undefined.add(step.getFullText());
            }
        }
        return undefined;
    }

    /**
     * Génère le snippet de code pour un step non défini.
     */
    public String generateSnippet(TnrStep step) {
        String methodName = toMethodName(step.getText());
        String annotation = step.getType().name().toLowerCase();

        return String.format("""
            @%s("%s")
            public void %s(TnrContext context) {
                // TODO: Implement this step
                throw new UnsupportedOperationException("Step not implemented");
            }
            """,
            capitalize(annotation),
            escapeQuotes(step.getText()),
            methodName
        );
    }

    private String toMethodName(String text) {
        return text.toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_|_$", "");
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String escapeQuotes(String s) {
        return s.replace("\"", "\\\"");
    }

    /**
     * Retourne des statistiques sur les steps.
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
            "givenCount", givenSteps.size(),
            "whenCount", whenSteps.size(),
            "thenCount", thenSteps.size(),
            "totalCount", allDefinitions.size()
        );
    }
}