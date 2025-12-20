package com.evse.simulator.tnr.engine;

import com.evse.simulator.tnr.model.TnrScenario;
import com.evse.simulator.tnr.model.TnrStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser Gherkin avancé supportant la syntaxe étendue.
 */
@Slf4j
@Component
public class GherkinParser {

    private static final Pattern TAG_PATTERN = Pattern.compile("@([\\w-]+)");
    private static final Pattern FEATURE_PATTERN = Pattern.compile("^Feature:\\s*(.+)$");
    private static final Pattern SCENARIO_PATTERN = Pattern.compile("^Scenario:\\s*(.+)$");
    private static final Pattern SCENARIO_OUTLINE_PATTERN = Pattern.compile("^Scenario Outline:\\s*(.+)$");
    private static final Pattern BACKGROUND_PATTERN = Pattern.compile("^Background:(.*)$");
    private static final Pattern EXAMPLES_PATTERN = Pattern.compile("^Examples:(.*)$");
    private static final Pattern STEP_PATTERN = Pattern.compile("^(Given|When|Then|And|But)\\s+(.+)$");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("^\\|(.+)\\|$");
    private static final Pattern DOC_STRING_PATTERN = Pattern.compile("^\"\"\"(.*)$");

    /**
     * Parse un fichier .feature.
     */
    public List<TnrScenario> parseFile(Path featureFile) throws IOException {
        String content = Files.readString(featureFile);
        return parse(content, featureFile.toString());
    }

    /**
     * Parse du contenu Gherkin.
     */
    public List<TnrScenario> parse(String gherkinContent, String sourceFile) {
        List<TnrScenario> scenarios = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(gherkinContent))) {
            String featureName = null;
            String featureDescription = null;
            List<String> featureTags = new ArrayList<>();
            List<TnrStep> backgroundSteps = new ArrayList<>();

            String line;
            int lineNumber = 0;
            List<String> currentTags = new ArrayList<>();
            TnrScenario.TnrScenarioBuilder currentScenario = null;
            List<TnrStep> currentSteps = new ArrayList<>();
            List<Map<String, String>> currentExamples = new ArrayList<>();
            boolean inBackground = false;
            boolean inExamples = false;
            boolean inDocString = false;
            StringBuilder docStringContent = new StringBuilder();
            TnrStep lastStep = null;
            List<String> tableHeaders = null;
            List<Map<String, String>> tableRows = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();

                // Skip empty lines and comments
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                // Handle doc strings
                if (inDocString) {
                    if (trimmed.startsWith("\"\"\"")) {
                        inDocString = false;
                        if (lastStep != null) {
                            lastStep.setDocString(docStringContent.toString());
                        }
                        docStringContent = new StringBuilder();
                    } else {
                        if (docStringContent.length() > 0) {
                            docStringContent.append("\n");
                        }
                        docStringContent.append(line); // Keep indentation
                    }
                    continue;
                }

                // Check for doc string start
                Matcher docStringMatcher = DOC_STRING_PATTERN.matcher(trimmed);
                if (docStringMatcher.matches()) {
                    inDocString = true;
                    continue;
                }

                // Handle tags
                if (trimmed.startsWith("@")) {
                    Matcher tagMatcher = TAG_PATTERN.matcher(trimmed);
                    while (tagMatcher.find()) {
                        currentTags.add(tagMatcher.group(1));
                    }
                    continue;
                }

                // Handle Feature
                Matcher featureMatcher = FEATURE_PATTERN.matcher(trimmed);
                if (featureMatcher.matches()) {
                    featureName = featureMatcher.group(1);
                    featureTags = new ArrayList<>(currentTags);
                    currentTags.clear();
                    continue;
                }

                // Handle Background
                Matcher backgroundMatcher = BACKGROUND_PATTERN.matcher(trimmed);
                if (backgroundMatcher.matches()) {
                    inBackground = true;
                    inExamples = false;
                    continue;
                }

                // Handle Scenario
                Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(trimmed);
                if (scenarioMatcher.matches()) {
                    // Save previous scenario if any
                    if (currentScenario != null) {
                        finishScenario(currentScenario, currentSteps, currentExamples, backgroundSteps, scenarios);
                    }

                    currentScenario = TnrScenario.builder()
                        .id(UUID.randomUUID().toString())
                        .name(scenarioMatcher.group(1))
                        .tags(mergeTags(featureTags, currentTags))
                        .sourceFile(sourceFile)
                        .sourceLine(lineNumber)
                        .gherkinContent("");

                    currentSteps = new ArrayList<>();
                    currentExamples = new ArrayList<>();
                    currentTags.clear();
                    inBackground = false;
                    inExamples = false;
                    tableHeaders = null;
                    tableRows.clear();
                    continue;
                }

                // Handle Scenario Outline
                Matcher outlineMatcher = SCENARIO_OUTLINE_PATTERN.matcher(trimmed);
                if (outlineMatcher.matches()) {
                    // Save previous scenario if any
                    if (currentScenario != null) {
                        finishScenario(currentScenario, currentSteps, currentExamples, backgroundSteps, scenarios);
                    }

                    currentScenario = TnrScenario.builder()
                        .id(UUID.randomUUID().toString())
                        .name(outlineMatcher.group(1))
                        .tags(mergeTags(featureTags, currentTags))
                        .sourceFile(sourceFile)
                        .sourceLine(lineNumber)
                        .gherkinContent("");

                    currentSteps = new ArrayList<>();
                    currentExamples = new ArrayList<>();
                    currentTags.clear();
                    inBackground = false;
                    inExamples = false;
                    tableHeaders = null;
                    tableRows.clear();
                    continue;
                }

                // Handle Examples
                Matcher examplesMatcher = EXAMPLES_PATTERN.matcher(trimmed);
                if (examplesMatcher.matches()) {
                    inExamples = true;
                    tableHeaders = null;
                    continue;
                }

                // Handle table rows
                Matcher tableMatcher = TABLE_ROW_PATTERN.matcher(trimmed);
                if (tableMatcher.matches()) {
                    String[] cells = tableMatcher.group(1).split("\\|");
                    List<String> values = Arrays.stream(cells)
                        .map(String::trim)
                        .toList();

                    if (inExamples) {
                        if (tableHeaders == null) {
                            tableHeaders = new ArrayList<>(values);
                        } else {
                            Map<String, String> row = new LinkedHashMap<>();
                            for (int i = 0; i < tableHeaders.size() && i < values.size(); i++) {
                                row.put(tableHeaders.get(i), values.get(i));
                            }
                            currentExamples.add(row);
                        }
                    } else if (lastStep != null) {
                        // Data table for step
                        if (tableHeaders == null) {
                            tableHeaders = new ArrayList<>(values);
                        } else {
                            Map<String, String> row = new LinkedHashMap<>();
                            for (int i = 0; i < tableHeaders.size() && i < values.size(); i++) {
                                row.put(tableHeaders.get(i), values.get(i));
                            }
                            tableRows.add(row);
                        }
                    }
                    continue;
                }

                // Handle steps
                Matcher stepMatcher = STEP_PATTERN.matcher(trimmed);
                if (stepMatcher.matches()) {
                    // Save previous step's table if any
                    if (lastStep != null && !tableRows.isEmpty()) {
                        lastStep.setDataTable(new ArrayList<>(tableRows));
                        tableRows.clear();
                        tableHeaders = null;
                    }

                    TnrStep.StepType type = TnrStep.StepType.valueOf(stepMatcher.group(1).toUpperCase());
                    String text = stepMatcher.group(2);

                    TnrStep step = TnrStep.builder()
                        .type(type)
                        .text(text)
                        .sourceLine(lineNumber)
                        .parameters(extractParameters(text))
                        .build();

                    if (inBackground) {
                        backgroundSteps.add(step);
                    } else {
                        currentSteps.add(step);
                    }
                    lastStep = step;
                    tableHeaders = null;
                    tableRows = new ArrayList<>();
                    continue;
                }
            }

            // Save last step's table if any
            if (lastStep != null && !tableRows.isEmpty()) {
                lastStep.setDataTable(new ArrayList<>(tableRows));
            }

            // Save last scenario
            if (currentScenario != null) {
                finishScenario(currentScenario, currentSteps, currentExamples, backgroundSteps, scenarios);
            }

        } catch (IOException e) {
            log.error("Error parsing Gherkin content", e);
        }

        log.info("Parsed {} scenarios from {}", scenarios.size(), sourceFile);
        return scenarios;
    }

    /**
     * Termine et ajoute un scénario à la liste.
     */
    private void finishScenario(TnrScenario.TnrScenarioBuilder builder,
                                List<TnrStep> steps,
                                List<Map<String, String>> examples,
                                List<TnrStep> backgroundSteps,
                                List<TnrScenario> scenarios) {

        TnrScenario scenario = builder
            .steps(new ArrayList<>(steps))
            .backgroundSteps(new ArrayList<>(backgroundSteps))
            .examples(new ArrayList<>(examples))
            .build();

        // Extract priority from tags
        for (String tag : scenario.getTags()) {
            if (tag.startsWith("priority-")) {
                try {
                    scenario.setPriority(Integer.parseInt(tag.substring(9)));
                } catch (NumberFormatException ignored) {}
            }
            if (tag.equals("critical")) {
                scenario.setCritical(true);
            }
            if (tag.equals("skip") || tag.equals("disabled")) {
                scenario.setEnabled(false);
            }
        }

        // Determine category from tags
        String category = determineCategory(scenario.getTags());
        scenario.setCategory(category);

        scenarios.add(scenario);
    }

    /**
     * Fusionne les tags feature et scenario.
     */
    private List<String> mergeTags(List<String> featureTags, List<String> scenarioTags) {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(featureTags);
        merged.addAll(scenarioTags);
        return new ArrayList<>(merged);
    }

    /**
     * Détermine la catégorie depuis les tags.
     */
    private String determineCategory(List<String> tags) {
        for (String tag : tags) {
            if (tag.equals("core")) return "core";
            if (tag.equals("smartcharging")) return "smartcharging";
            if (tag.equals("combination")) return "combinations";
            if (tag.equals("error")) return "errors";
            if (tag.equals("performance")) return "performance";
            if (tag.equals("edge")) return "edge";
            if (tag.equals("flow")) return "flows";
        }
        return "general";
    }

    /**
     * Extrait les paramètres d'un texte de step.
     */
    private List<String> extractParameters(String text) {
        List<String> params = new ArrayList<>();

        // Quoted strings
        Pattern quoted = Pattern.compile("\"([^\"]+)\"");
        Matcher m = quoted.matcher(text);
        while (m.find()) {
            params.add(m.group(1));
        }

        // Numbers
        Pattern numbers = Pattern.compile("\\b(\\d+)\\b");
        m = numbers.matcher(text);
        while (m.find()) {
            params.add(m.group(1));
        }

        return params;
    }

    /**
     * Valide la syntaxe d'un fichier Gherkin.
     */
    public List<String> validate(String gherkinContent) {
        List<String> errors = new ArrayList<>();
        try {
            List<TnrScenario> scenarios = parse(gherkinContent, "validation");
            if (scenarios.isEmpty()) {
                errors.add("No scenarios found");
            }
            for (TnrScenario scenario : scenarios) {
                if (scenario.getSteps().isEmpty()) {
                    errors.add("Scenario '" + scenario.getName() + "' has no steps");
                }
                // Verify step order (Given before When before Then)
                validateStepOrder(scenario.getSteps(), errors, scenario.getName());
            }
        } catch (Exception e) {
            errors.add("Parse error: " + e.getMessage());
        }
        return errors;
    }

    /**
     * Valide l'ordre des steps.
     */
    private void validateStepOrder(List<TnrStep> steps, List<String> errors, String scenarioName) {
        TnrStep.StepType currentPhase = TnrStep.StepType.GIVEN;

        for (TnrStep step : steps) {
            TnrStep.StepType type = step.getType();
            if (type == TnrStep.StepType.AND || type == TnrStep.StepType.BUT) {
                continue; // Ces types héritent de la phase courante
            }

            // Given -> When -> Then
            if (type == TnrStep.StepType.GIVEN && currentPhase != TnrStep.StepType.GIVEN) {
                errors.add("Scenario '" + scenarioName + "': Given step after " + currentPhase);
            }
            if (type == TnrStep.StepType.WHEN) {
                currentPhase = TnrStep.StepType.WHEN;
            }
            if (type == TnrStep.StepType.THEN) {
                if (currentPhase == TnrStep.StepType.GIVEN) {
                    errors.add("Scenario '" + scenarioName + "': Then step without When");
                }
                currentPhase = TnrStep.StepType.THEN;
            }
        }
    }
}
