package com.evse.simulator.domain.service;

import com.evse.simulator.model.ExecutionDetail;
import com.evse.simulator.model.ExecutionMeta;
import com.evse.simulator.model.TNRScenario;
import com.evse.simulator.model.TNRScenario.TNRResult;
import com.evse.simulator.model.TNREvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface de gestion des Tests Non-Regression (TNR).
 */
public interface TNRService {

    // Executions list and detail
    List<ExecutionMeta> listExecutions();
    ExecutionDetail getExecution(String executionId) throws Exception;

    // Scenario CRUD
    List<TNRScenario> getAllScenarios();
    TNRScenario getScenario(String id);
    TNRScenario createScenario(TNRScenario scenario);
    TNRScenario updateScenario(String id, TNRScenario updates);
    void deleteScenario(String id);

    // Execution
    CompletableFuture<TNRResult> runScenario(String scenarioId);
    boolean isRunning(String scenarioId);

    // Results
    List<TNRResult> getAllResults();
    TNRResult getResult(String scenarioId);

    // Recording
    void startRecording(String executionId, String scenarioName);
    void stopRecording();
    void recordEvent(TNREvent event);

    // Export
    Map<String, Object> exportToXray();
}