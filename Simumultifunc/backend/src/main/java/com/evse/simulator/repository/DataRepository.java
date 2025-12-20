package com.evse.simulator.repository;

import com.evse.simulator.model.ExecutionDetail;
import com.evse.simulator.model.Session;
import com.evse.simulator.model.TNRScenario;
import com.evse.simulator.model.VehicleProfile;

import java.util.List;
import java.util.Optional;

/**
 * Interface pour le repository de données de l'application.
 */
public interface DataRepository {

    // =========================================================================
    // Sessions
    // =========================================================================

    List<Session> findAllSessions();

    Optional<Session> findSessionById(String id);

    Session saveSession(Session session);

    void deleteSession(String id);

    long countSessions();

    // =========================================================================
    // Vehicles
    // =========================================================================

    List<VehicleProfile> findAllVehicles();

    Optional<VehicleProfile> findVehicleById(String id);

    VehicleProfile saveVehicle(VehicleProfile vehicle);

    void deleteVehicle(String id);

    // =========================================================================
    // TNR Scenarios
    // =========================================================================

    List<TNRScenario> findAllTNRScenarios();

    Optional<TNRScenario> findTNRScenarioById(String id);

    TNRScenario saveTNRScenario(TNRScenario scenario);

    void deleteTNRScenario(String id);

    // =========================================================================
    // TNR Executions
    // =========================================================================

    List<ExecutionDetail> findAllTNRExecutions();

    Optional<ExecutionDetail> findTNRExecutionById(String id);

    ExecutionDetail saveTNRExecution(ExecutionDetail execution);

    void deleteTNRExecution(String id);

    // =========================================================================
    // Utility
    // =========================================================================

    void saveAll();

    void reloadAll();
}
