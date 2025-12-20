package com.evse.simulator.repository;

import com.evse.simulator.document.*;
import com.evse.simulator.document.mapper.SessionDocumentMapper;
import com.evse.simulator.model.*;
import com.evse.simulator.model.TNRScenario.*;
import com.evse.simulator.repository.mongo.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation MongoDB du DataRepository.
 * Active uniquement quand data.use-mongodb=true
 */
@Repository("mongoDataRepository")
@ConditionalOnProperty(name = "data.use-mongodb", havingValue = "true", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class MongoDataRepository implements DataRepository {

    private final SessionMongoRepository sessionRepo;
    private final VehicleProfileMongoRepository vehicleRepo;
    private final TnrScenarioMongoRepository scenarioRepo;
    private final TnrExecutionMongoRepository executionRepo;
    private final SessionDocumentMapper sessionMapper;

    @PostConstruct
    public void init() {
        log.info("MongoDataRepository initialized - using MongoDB as primary storage");
        log.info("  Sessions: {}, Vehicles: {}, Scenarios: {}, Executions: {}",
                sessionRepo.count(), vehicleRepo.count(), scenarioRepo.count(), executionRepo.count());
    }

    // =========================================================================
    // Sessions
    // =========================================================================

    @Override
    public List<Session> findAllSessions() {
        return sessionRepo.findAll().stream()
                .map(sessionMapper::toModel)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Session> findSessionById(String id) {
        return sessionRepo.findById(id)
                .map(sessionMapper::toModel);
    }

    @Override
    public Session saveSession(Session session) {
        session.touch();
        SessionDocument doc = sessionMapper.toDocument(session);
        SessionDocument saved = sessionRepo.save(doc);
        log.debug("Saved session to MongoDB: {}", saved.getId());
        return sessionMapper.toModel(saved);
    }

    @Override
    public void deleteSession(String id) {
        sessionRepo.deleteById(id);
        log.debug("Deleted session from MongoDB: {}", id);
    }

    @Override
    public long countSessions() {
        return sessionRepo.count();
    }

    // =========================================================================
    // Vehicles
    // =========================================================================

    @Override
    public List<VehicleProfile> findAllVehicles() {
        return vehicleRepo.findByActiveTrue().stream()
                .map(this::documentToVehicle)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<VehicleProfile> findVehicleById(String id) {
        return vehicleRepo.findById(id)
                .map(this::documentToVehicle);
    }

    @Override
    public VehicleProfile saveVehicle(VehicleProfile vehicle) {
        VehicleProfileDocument doc = vehicleToDocument(vehicle);
        VehicleProfileDocument saved = vehicleRepo.save(doc);
        log.debug("Saved vehicle to MongoDB: {}", saved.getId());
        return documentToVehicle(saved);
    }

    @Override
    public void deleteVehicle(String id) {
        vehicleRepo.deleteById(id);
        log.debug("Deleted vehicle from MongoDB: {}", id);
    }

    // =========================================================================
    // TNR Scenarios
    // =========================================================================

    @Override
    public List<TNRScenario> findAllTNRScenarios() {
        return scenarioRepo.findByActiveTrue().stream()
                .map(this::documentToScenario)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TNRScenario> findTNRScenarioById(String id) {
        return scenarioRepo.findById(id)
                .map(this::documentToScenario);
    }

    @Override
    public TNRScenario saveTNRScenario(TNRScenario scenario) {
        TnrScenarioDocument doc = scenarioToDocument(scenario);
        TnrScenarioDocument saved = scenarioRepo.save(doc);
        log.debug("Saved TNR scenario to MongoDB: {}", saved.getId());
        return documentToScenario(saved);
    }

    @Override
    public void deleteTNRScenario(String id) {
        scenarioRepo.deleteById(id);
        log.debug("Deleted TNR scenario from MongoDB: {}", id);
    }

    // =========================================================================
    // TNR Executions
    // =========================================================================

    @Override
    public List<ExecutionDetail> findAllTNRExecutions() {
        return executionRepo.findAll().stream()
                .map(this::documentToExecution)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ExecutionDetail> findTNRExecutionById(String id) {
        return executionRepo.findById(id)
                .map(this::documentToExecution);
    }

    @Override
    public ExecutionDetail saveTNRExecution(ExecutionDetail execution) {
        TnrExecutionDocument doc = executionToDocument(execution);
        TnrExecutionDocument saved = executionRepo.save(doc);
        log.debug("Saved TNR execution to MongoDB: {}", saved.getId());
        return documentToExecution(saved);
    }

    @Override
    public void deleteTNRExecution(String id) {
        executionRepo.deleteById(id);
        log.debug("Deleted TNR execution from MongoDB: {}", id);
    }

    // =========================================================================
    // Vehicle Mapping
    // =========================================================================

    private VehicleProfile documentToVehicle(VehicleProfileDocument doc) {
        if (doc == null) return null;

        // Convert Map to NavigableMap
        NavigableMap<Integer, Double> dcCurve = null;
        if (doc.getDcChargingCurve() != null) {
            dcCurve = new TreeMap<>(doc.getDcChargingCurve());
        }

        NavigableMap<Integer, Double> voltCurve = null;
        if (doc.getVoltageCurve() != null) {
            voltCurve = new TreeMap<>(doc.getVoltageCurve());
        }

        return VehicleProfile.builder()
                .id(doc.getId())
                .brand(doc.getBrand())
                .model(doc.getModel())
                .variant(doc.getVariant())
                .name(doc.getName())
                .displayName(doc.getDisplayName())
                .manufacturer(doc.getManufacturer())
                .batteryCapacityKwh(doc.getBatteryCapacityKwh() != null ? doc.getBatteryCapacityKwh() : 50.0)
                .batteryVoltageNominal(doc.getBatteryVoltageNominal() != null ? doc.getBatteryVoltageNominal() : 350.0)
                .batteryVoltageMax(doc.getBatteryVoltageMax() != null ? doc.getBatteryVoltageMax() : 400.0)
                .maxAcPowerKw(doc.getMaxAcPowerKw() != null ? doc.getMaxAcPowerKw() : 11.0)
                .maxAcPhases(doc.getMaxAcPhases() != null ? doc.getMaxAcPhases() : 3)
                .maxAcCurrentA(doc.getMaxAcCurrentA() != null ? doc.getMaxAcCurrentA() : 16.0)
                .onboardChargerKw(doc.getOnboardChargerKw() != null ? doc.getOnboardChargerKw() : 11.0)
                .maxDcPowerKw(doc.getMaxDcPowerKw() != null ? doc.getMaxDcPowerKw() : 100.0)
                .maxDcCurrentA(doc.getMaxDcCurrentA() != null ? doc.getMaxDcCurrentA() : 250.0)
                .dcChargingCurve(dcCurve)
                .voltageCurve(voltCurve)
                .connectorTypes(doc.getConnectorTypes())
                .dcConnectors(doc.getDcConnectors())
                .efficiencyAc(doc.getEfficiencyAc() != null ? doc.getEfficiencyAc() : 0.90)
                .efficiencyDc(doc.getEfficiencyDc() != null ? doc.getEfficiencyDc() : 0.92)
                .defaultInitialSoc(doc.getDefaultInitialSoc() != null ? doc.getDefaultInitialSoc().intValue() : 20)
                .defaultTargetSoc(doc.getDefaultTargetSoc() != null ? doc.getDefaultTargetSoc().intValue() : 80)
                .preconditioning(doc.getPreconditioning() != null ? doc.getPreconditioning() : false)
                .build();
    }

    private VehicleProfileDocument vehicleToDocument(VehicleProfile vehicle) {
        if (vehicle == null) return null;

        // Convert NavigableMap to Map
        Map<Integer, Double> dcCurve = vehicle.getDcChargingCurve() != null ?
                new HashMap<>(vehicle.getDcChargingCurve()) : null;
        Map<Integer, Double> voltCurve = vehicle.getVoltageCurve() != null ?
                new HashMap<>(vehicle.getVoltageCurve()) : null;

        return VehicleProfileDocument.builder()
                .id(vehicle.getId())
                .brand(vehicle.getBrand())
                .model(vehicle.getModel())
                .variant(vehicle.getVariant())
                .name(vehicle.getName())
                .displayName(vehicle.getDisplayName())
                .manufacturer(vehicle.getManufacturer())
                .batteryCapacityKwh(vehicle.getBatteryCapacityKwh())
                .batteryVoltageNominal(vehicle.getBatteryVoltageNominal())
                .batteryVoltageMax(vehicle.getBatteryVoltageMax())
                .maxAcPowerKw(vehicle.getMaxAcPowerKw())
                .maxAcPhases(vehicle.getMaxAcPhases())
                .maxAcCurrentA(vehicle.getMaxAcCurrentA())
                .onboardChargerKw(vehicle.getOnboardChargerKw())
                .maxDcPowerKw(vehicle.getMaxDcPowerKw())
                .maxDcCurrentA(vehicle.getMaxDcCurrentA())
                .dcChargingCurve(dcCurve)
                .voltageCurve(voltCurve)
                .connectorTypes(vehicle.getConnectorTypes())
                .dcConnectors(vehicle.getDcConnectors())
                .efficiencyAc(vehicle.getEfficiencyAc())
                .efficiencyDc(vehicle.getEfficiencyDc())
                .defaultInitialSoc((double) vehicle.getDefaultInitialSoc())
                .defaultTargetSoc((double) vehicle.getDefaultTargetSoc())
                .preconditioning(vehicle.isPreconditioning())
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // =========================================================================
    // TNR Scenario Mapping
    // =========================================================================

    private TNRScenario documentToScenario(TnrScenarioDocument doc) {
        if (doc == null) return null;

        TNRScenario scenario = new TNRScenario();
        scenario.setId(doc.getId());
        scenario.setName(doc.getName());
        scenario.setDescription(doc.getDescription());
        scenario.setCategory(doc.getCategory());
        scenario.setTags(doc.getTags());
        scenario.setAuthor(doc.getAuthor());
        scenario.setVersion(doc.getVersion());

        // Convert steps
        if (doc.getSteps() != null) {
            List<TNRStep> steps = doc.getSteps().stream()
                    .map(this::embeddedStepToStep)
                    .collect(Collectors.toList());
            scenario.setSteps(steps);
        }

        return scenario;
    }

    private TNRStep embeddedStepToStep(TnrScenarioDocument.TnrStepEmbedded embedded) {
        TNRStep step = new TNRStep();
        step.setOrder(embedded.getOrder());
        step.setName(embedded.getName());

        // Convert String to StepType enum
        if (embedded.getType() != null) {
            try {
                step.setType(StepType.valueOf(embedded.getType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                step.setType(StepType.SEND_OCPP);
            }
        }

        step.setAction(embedded.getAction());
        step.setPayload(embedded.getPayload());
        step.setDelayMs(embedded.getDelayMs());
        step.setTimeoutMs(embedded.getTimeoutMs());

        if (embedded.getAssertions() != null) {
            List<TNRAssertion> assertions = embedded.getAssertions().stream()
                    .map(this::embeddedAssertionToAssertion)
                    .collect(Collectors.toList());
            step.setAssertions(assertions);
        }

        return step;
    }

    private TNRAssertion embeddedAssertionToAssertion(TnrScenarioDocument.TnrAssertionEmbedded embedded) {
        TNRAssertion assertion = new TNRAssertion();

        // Convert String to AssertionType enum
        if (embedded.getType() != null) {
            try {
                assertion.setType(AssertionType.valueOf(embedded.getType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                assertion.setType(AssertionType.RESPONSE_STATUS);
            }
        }

        assertion.setPath(embedded.getPath());

        // Convert String to Operator enum
        if (embedded.getOperator() != null) {
            try {
                assertion.setOperator(Operator.valueOf(embedded.getOperator().toUpperCase()));
            } catch (IllegalArgumentException e) {
                assertion.setOperator(Operator.EQUALS);
            }
        }

        assertion.setExpected(embedded.getExpected());
        return assertion;
    }

    private TnrScenarioDocument scenarioToDocument(TNRScenario scenario) {
        if (scenario == null) return null;

        List<TnrScenarioDocument.TnrStepEmbedded> embeddedSteps = null;
        if (scenario.getSteps() != null) {
            embeddedSteps = scenario.getSteps().stream()
                    .map(this::stepToEmbeddedStep)
                    .collect(Collectors.toList());
        }

        return TnrScenarioDocument.builder()
                .id(scenario.getId())
                .name(scenario.getName())
                .description(scenario.getDescription())
                .category(scenario.getCategory())
                .tags(scenario.getTags())
                .author(scenario.getAuthor())
                .version(scenario.getVersion())
                .steps(embeddedSteps)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private TnrScenarioDocument.TnrStepEmbedded stepToEmbeddedStep(TNRStep step) {
        List<TnrScenarioDocument.TnrAssertionEmbedded> embeddedAssertions = null;
        if (step.getAssertions() != null) {
            embeddedAssertions = step.getAssertions().stream()
                    .map(this::assertionToEmbeddedAssertion)
                    .collect(Collectors.toList());
        }

        // Convert payload Object to Map if possible
        Map<String, Object> payloadMap = null;
        if (step.getPayload() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> temp = (Map<String, Object>) step.getPayload();
            payloadMap = temp;
        }

        return TnrScenarioDocument.TnrStepEmbedded.builder()
                .order(step.getOrder())
                .name(step.getName())
                .type(step.getType() != null ? step.getType().name() : null)
                .action(step.getAction())
                .payload(payloadMap)
                .delayMs(step.getDelayMs())
                .timeoutMs(step.getTimeoutMs())
                .assertions(embeddedAssertions)
                .build();
    }

    private TnrScenarioDocument.TnrAssertionEmbedded assertionToEmbeddedAssertion(TNRAssertion assertion) {
        return TnrScenarioDocument.TnrAssertionEmbedded.builder()
                .type(assertion.getType() != null ? assertion.getType().name() : null)
                .path(assertion.getPath())
                .operator(assertion.getOperator() != null ? assertion.getOperator().name() : null)
                .expected(assertion.getExpected())
                .build();
    }

    // =========================================================================
    // TNR Execution Mapping
    // =========================================================================

    private ExecutionDetail documentToExecution(TnrExecutionDocument doc) {
        if (doc == null) return null;

        ExecutionDetail execution = new ExecutionDetail();
        execution.id = doc.getId();
        execution.scenarioName = doc.getScenarioName();
        execution.executedAt = doc.getExecutedAt();
        execution.signature = doc.getStatus(); // Using status as signature fallback
        execution.events = new ArrayList<>(); // Events not stored in document

        return execution;
    }

    private TnrExecutionDocument executionToDocument(ExecutionDetail execution) {
        if (execution == null) return null;

        return TnrExecutionDocument.builder()
                .id(execution.id)
                .scenarioName(execution.scenarioName)
                .executedAt(execution.executedAt)
                .status(execution.signature)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // =========================================================================
    // Global Operations
    // =========================================================================

    @Override
    public void saveAll() {
        // MongoDB automatically persists data, nothing to do
        log.debug("saveAll called - MongoDB auto-persists");
    }

    @Override
    public void reloadAll() {
        // MongoDB always reads fresh data, nothing to do
        log.debug("reloadAll called - MongoDB always reads fresh");
    }
}
