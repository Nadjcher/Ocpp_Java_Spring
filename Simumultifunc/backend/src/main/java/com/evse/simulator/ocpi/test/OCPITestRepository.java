package com.evse.simulator.ocpi.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for OCPI test scenarios and results.
 */
@Repository
@Slf4j
public class OCPITestRepository {

    @Value("${ocpi.tests.scenarios-directory:./data/ocpi/scenarios}")
    private String scenariosDirectory;

    @Value("${ocpi.tests.results-directory:./data/ocpi/results}")
    private String resultsDirectory;

    private final ObjectMapper objectMapper;
    private final Map<String, OCPITestScenario> scenariosCache = new ConcurrentHashMap<>();
    private final Map<String, OCPITestResult> resultsCache = new ConcurrentHashMap<>();
    private volatile boolean resultsDirty = false;

    public OCPITestRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        loadScenarios();
        loadResults();
    }

    // =========================================================================
    // Scenarios
    // =========================================================================

    /**
     * Load test scenarios from directory.
     */
    public void loadScenarios() {
        File dir = new File(scenariosDirectory);
        if (!dir.exists()) {
            log.info("Creating scenarios directory: {}", scenariosDirectory);
            dir.mkdirs();
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            log.info("No test scenarios found in {}", scenariosDirectory);
            return;
        }

        scenariosCache.clear();
        for (File file : files) {
            try {
                OCPITestScenario scenario = objectMapper.readValue(file, OCPITestScenario.class);
                if (scenario.getId() == null) {
                    scenario.setId(file.getName().replace(".json", ""));
                }
                scenariosCache.put(scenario.getId(), scenario);
                log.info("Loaded test scenario: {} ({})", scenario.getName(), scenario.getId());
            } catch (IOException e) {
                log.error("Failed to load scenario from {}: {}", file.getName(), e.getMessage());
            }
        }

        log.info("Loaded {} test scenarios", scenariosCache.size());
    }

    /**
     * Get all scenarios.
     */
    public List<OCPITestScenario> getAllScenarios() {
        return new ArrayList<>(scenariosCache.values());
    }

    /**
     * Get scenarios by category.
     */
    public List<OCPITestScenario> getScenariosByCategory(String category) {
        return scenariosCache.values().stream()
                .filter(s -> category.equalsIgnoreCase(s.getCategory()))
                .collect(Collectors.toList());
    }

    /**
     * Get scenario by ID.
     */
    public Optional<OCPITestScenario> getScenario(String id) {
        return Optional.ofNullable(scenariosCache.get(id));
    }

    /**
     * Save scenario.
     */
    public OCPITestScenario saveScenario(OCPITestScenario scenario) {
        if (scenario.getId() == null) {
            scenario.setId(UUID.randomUUID().toString());
        }

        scenariosCache.put(scenario.getId(), scenario);

        try {
            File dir = new File(scenariosDirectory);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, scenario.getId() + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, scenario);
            log.info("Saved scenario: {}", scenario.getId());
        } catch (IOException e) {
            log.error("Failed to save scenario: {}", e.getMessage());
        }

        return scenario;
    }

    /**
     * Delete scenario.
     */
    public void deleteScenario(String id) {
        scenariosCache.remove(id);
        File file = new File(scenariosDirectory, id + ".json");
        if (file.exists()) {
            file.delete();
        }
    }

    // =========================================================================
    // Results
    // =========================================================================

    /**
     * Load test results from directory.
     */
    public void loadResults() {
        File dir = new File(resultsDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;

        resultsCache.clear();
        for (File file : files) {
            try {
                OCPITestResult result = objectMapper.readValue(file, OCPITestResult.class);
                resultsCache.put(result.getId(), result);
            } catch (IOException e) {
                log.error("Failed to load result from {}: {}", file.getName(), e.getMessage());
            }
        }

        log.info("Loaded {} test results", resultsCache.size());
    }

    /**
     * Get all results.
     */
    public List<OCPITestResult> getAllResults() {
        return new ArrayList<>(resultsCache.values());
    }

    /**
     * Get result by ID.
     */
    public Optional<OCPITestResult> getResult(String id) {
        return Optional.ofNullable(resultsCache.get(id));
    }

    /**
     * Get results for a partner.
     */
    public List<OCPITestResult> getResultsForPartner(String partnerId) {
        return resultsCache.values().stream()
                .filter(r -> partnerId.equals(r.getPartnerId()))
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .collect(Collectors.toList());
    }

    /**
     * Get results for a scenario.
     */
    public List<OCPITestResult> getResultsForScenario(String scenarioId) {
        return resultsCache.values().stream()
                .filter(r -> scenarioId.equals(r.getScenarioId()))
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .collect(Collectors.toList());
    }

    /**
     * Get recent results.
     */
    public List<OCPITestResult> getRecentResults(int limit) {
        return resultsCache.values().stream()
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Save test result.
     */
    public OCPITestResult saveResult(OCPITestResult result) {
        resultsCache.put(result.getId(), result);
        resultsDirty = true;
        return result;
    }

    /**
     * Delete result.
     */
    public void deleteResult(String id) {
        resultsCache.remove(id);
        File file = new File(resultsDirectory, id + ".json");
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Delete old results (keep last N per scenario/partner combo).
     */
    public void cleanupOldResults(int keepCount) {
        Map<String, List<OCPITestResult>> grouped = resultsCache.values().stream()
                .collect(Collectors.groupingBy(r -> r.getScenarioId() + ":" + r.getPartnerId()));

        for (List<OCPITestResult> results : grouped.values()) {
            if (results.size() > keepCount) {
                results.stream()
                        .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                        .skip(keepCount)
                        .forEach(r -> deleteResult(r.getId()));
            }
        }
    }

    /**
     * Auto-save results periodically.
     */
    @Scheduled(fixedDelayString = "${ocpi.tests.auto-save-interval:30000}")
    public void autoSave() {
        if (resultsDirty) {
            saveResultsToDisk();
            resultsDirty = false;
        }
    }

    private void saveResultsToDisk() {
        File dir = new File(resultsDirectory);
        if (!dir.exists()) dir.mkdirs();

        for (OCPITestResult result : resultsCache.values()) {
            try {
                File file = new File(dir, result.getId() + ".json");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, result);
            } catch (IOException e) {
                log.error("Failed to save result {}: {}", result.getId(), e.getMessage());
            }
        }

        log.debug("Saved {} test results to disk", resultsCache.size());
    }

    /**
     * Reload all data from disk.
     */
    public void reload() {
        loadScenarios();
        loadResults();
    }
}
