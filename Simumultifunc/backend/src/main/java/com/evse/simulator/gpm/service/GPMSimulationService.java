package com.evse.simulator.gpm.service;

import com.evse.simulator.gpm.config.GPMProperties;
import com.evse.simulator.gpm.data.GPMVehicleDatabase;
import com.evse.simulator.gpm.dto.*;
import com.evse.simulator.gpm.model.*;
import com.evse.simulator.gpm.model.enums.GPMChargeType;
import com.evse.simulator.gpm.model.enums.GPMSimulationMode;
import com.evse.simulator.gpm.model.enums.GPMSimulationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service principal pour la simulation GPM.
 * Gère le cycle de vie des simulations et l'exécution des ticks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GPMSimulationService {

    private final GPMDryRunClient dryRunClient;
    private final GPMVehicleDatabase vehicleDatabase;
    private final GPMProperties properties;

    // Simulations actives
    private final Map<String, GPMSimulation> simulations = new ConcurrentHashMap<>();
    private final AtomicInteger transactionCounter = new AtomicInteger(1000);

    // ══════════════════════════════════════════════════════════════
    // CRÉATION ET GESTION DES SIMULATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Crée une nouvelle simulation GPM.
     */
    public GPMSimulation createSimulation(GPMSimulationRequest request) {
        String id = UUID.randomUUID().toString();

        GPMSimulationConfig config = GPMSimulationConfig.builder()
            .name(request.getName())
            .rootNodeId(request.getRootNodeId())
            .tickIntervalMinutes(request.getTickIntervalMinutes())
            .numberOfTicks(request.getNumberOfTicks())
            .timeScale(request.getTimeScale())
            .mode(request.getMode() != null ? request.getMode().name() : GPMSimulationMode.DRY_RUN.name())
            .build();

        GPMSimulation simulation = GPMSimulation.builder()
            .id(id)
            .config(config)
            .status(GPMSimulationStatus.CREATED)
            .totalTicks(request.getNumberOfTicks())
            .vehicles(new ArrayList<>())
            .tickResults(new ArrayList<>())
            .apiErrors(new ArrayList<>())
            .build();

        simulations.put(id, simulation);
        log.info("Simulation created: {} - {}", id, request.getName());

        return simulation;
    }

    /**
     * Récupère une simulation par ID.
     */
    public Optional<GPMSimulation> getSimulation(String id) {
        return Optional.ofNullable(simulations.get(id));
    }

    /**
     * Récupère toutes les simulations.
     */
    public List<GPMSimulation> getAllSimulations() {
        return new ArrayList<>(simulations.values());
    }

    /**
     * Supprime une simulation.
     */
    public boolean deleteSimulation(String id) {
        GPMSimulation sim = simulations.remove(id);
        if (sim != null) {
            log.info("Simulation deleted: {}", id);
            return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    // AJOUT DE VÉHICULES
    // ══════════════════════════════════════════════════════════════

    /**
     * Ajoute un véhicule à une simulation.
     */
    public GPMVehicleState addVehicle(String simulationId, AddVehicleRequest request) {
        GPMSimulation simulation = simulations.get(simulationId);
        if (simulation == null) {
            throw new IllegalArgumentException("Simulation not found: " + simulationId);
        }
        if (simulation.getStatus() != GPMSimulationStatus.CREATED) {
            throw new IllegalStateException("Cannot add vehicle to simulation in status: " + simulation.getStatus());
        }

        EVTypeConfig vehicleType = vehicleDatabase.getById(request.getEvTypeId());
        if (vehicleType == null) {
            throw new IllegalArgumentException("Vehicle type not found: " + request.getEvTypeId());
        }

        String transactionId = request.getTransactionId() != null ?
            request.getTransactionId() :
            "TXN_" + transactionCounter.incrementAndGet();

        GPMVehicleState vehicle = GPMVehicleState.builder()
            .evseId(request.getEvseId())
            .evTypeId(request.getEvTypeId())
            .chargeType(vehicleType.getChargeType())
            .transactionId(transactionId)
            .initialSoc(request.getInitialSoc())
            .currentSoc(request.getInitialSoc())
            .targetSoc(request.getTargetSoc())
            .capacityWh(vehicleType.getCapacityWh())
            .maxPowerW(vehicleType.getMaxPowerW())
            .energyRegisterWh(0)
            .charging(true)
            .history(new ArrayList<>())
            .build();

        simulation.getVehicles().add(vehicle);
        log.info("Vehicle added to simulation {}: {} ({}) on {}",
            simulationId, vehicleType.getName(), vehicleType.getChargeType(), request.getEvseId());

        return vehicle;
    }

    /**
     * Supprime un véhicule d'une simulation.
     */
    public boolean removeVehicle(String simulationId, String evseId) {
        GPMSimulation simulation = simulations.get(simulationId);
        if (simulation == null || simulation.getStatus() != GPMSimulationStatus.CREATED) {
            return false;
        }

        return simulation.getVehicles().removeIf(v -> v.getEvseId().equals(evseId));
    }

    // ══════════════════════════════════════════════════════════════
    // CONTRÔLE DE LA SIMULATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Démarre une simulation (asynchrone).
     */
    @Async
    public void startSimulation(String simulationId) {
        GPMSimulation simulation = simulations.get(simulationId);
        if (simulation == null) {
            log.error("Cannot start: simulation not found: {}", simulationId);
            return;
        }

        if (simulation.getVehicles().isEmpty()) {
            log.error("Cannot start: no vehicles in simulation: {}", simulationId);
            simulation.setStatus(GPMSimulationStatus.FAILED);
            return;
        }

        log.info("Starting simulation: {} with {} vehicles",
            simulationId, simulation.getVehicles().size());

        simulation.setStatus(GPMSimulationStatus.RUNNING);
        simulation.setStartedAt(Instant.now());

        boolean isDryRun = simulation.getConfig().isDryRunMode() && dryRunClient.isEnabled();

        log.info("GPM Simulation mode check: configMode={}, isDryRunMode={}, clientEnabled={}, isDryRun={}",
            simulation.getConfig().getMode(),
            simulation.getConfig().isDryRunMode(),
            dryRunClient.isEnabled(),
            isDryRun);

        // Générer un dry-run ID si mode activé
        // L'API TTE n'a pas d'endpoint pour créer un dry-run,
        // le dryRunContext.id est juste passé avec chaque requête
        if (isDryRun) {
            String dryRunId = dryRunClient.generateDryRunId("sim-" + simulation.getId().substring(0, 8));
            simulation.setDryRunId(dryRunId);
            log.info("GPM running in DRY_RUN mode with ID: {}", dryRunId);
        } else {
            log.info("GPM running in LOCAL mode (not calling TTE APIs)");
        }

        // Exécuter les ticks
        try {
            executeSimulation(simulation, isDryRun);
        } catch (Exception e) {
            log.error("Simulation failed: {}", e.getMessage(), e);
            simulation.setStatus(GPMSimulationStatus.FAILED);
            simulation.getApiErrors().add(GPMApiError.builder()
                .tick(simulation.getCurrentTick())
                .type("SIMULATION")
                .message(e.getMessage())
                .timestamp(Instant.now())
                .build());
        }

        simulation.setCompletedAt(Instant.now());

        if (simulation.getStatus() == GPMSimulationStatus.RUNNING) {
            simulation.setStatus(GPMSimulationStatus.COMPLETED);
        }

        log.info("Simulation completed: {} - status: {}",
            simulationId, simulation.getStatus());
    }

    /**
     * Arrête une simulation en cours.
     */
    public boolean stopSimulation(String simulationId) {
        GPMSimulation simulation = simulations.get(simulationId);
        if (simulation == null) {
            return false;
        }

        if (simulation.getStatus() == GPMSimulationStatus.RUNNING) {
            simulation.setStatus(GPMSimulationStatus.CANCELLED);
            log.info("Simulation stopped: {}", simulationId);
            return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    // EXÉCUTION DE LA SIMULATION
    // ══════════════════════════════════════════════════════════════

    private void executeSimulation(GPMSimulation simulation, boolean isDryRun) {
        int totalTicks = simulation.getTotalTicks();
        int tickIntervalMinutes = simulation.getConfig().getTickIntervalMinutes();
        double timeScale = simulation.getConfig().getTimeScale();

        // Délai entre les ticks en millisecondes (ajusté par timeScale)
        long tickDelayMs = (long) (tickIntervalMinutes * 60 * 1000 / timeScale);

        Instant simulatedTime = Instant.now();

        for (int tick = 1; tick <= totalTicks; tick++) {
            // Vérifier si la simulation est annulée
            if (simulation.getStatus() != GPMSimulationStatus.RUNNING) {
                break;
            }

            simulation.setCurrentTick(tick);
            String tickId = UUID.randomUUID().toString();

            log.debug("Executing tick {}/{} for simulation {}",
                tick, totalTicks, simulation.getId());

            // Exécuter le tick
            GPMTickResult tickResult = executeTick(simulation, tick, tickId, simulatedTime, isDryRun);
            simulation.getTickResults().add(tickResult);

            // Avancer le temps simulé
            simulatedTime = simulatedTime.plus(tickIntervalMinutes, ChronoUnit.MINUTES);

            // Attendre le prochain tick (sauf si c'est le dernier)
            if (tick < totalTicks && simulation.getStatus() == GPMSimulationStatus.RUNNING) {
                try {
                    Thread.sleep(tickDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private GPMTickResult executeTick(GPMSimulation simulation, int tick, String tickId,
                                       Instant simulatedTime, boolean isDryRun) {
        Instant now = Instant.now();

        // 1. Calculer la charge pour chaque véhicule
        List<GPMVehicleTickResult> vehicleResults = new ArrayList<>();

        for (GPMVehicleState vehicle : simulation.getVehicles()) {
            if (!vehicle.isCharging()) {
                continue;
            }

            GPMVehicleTickResult result = calculateVehicleCharge(vehicle, simulation.getConfig().getTickIntervalMinutes());
            vehicleResults.add(result);

            // Mettre à jour l'état du véhicule
            updateVehicleState(vehicle, result, tick);

            // 2. Envoyer le meter value pour ce véhicule (mode dry-run)
            if (isDryRun) {
                sendMeterValues(simulation, vehicle, result, tick, simulatedTime);
            }
        }

        // 3. Envoyer le regulation tick (mode dry-run)
        if (isDryRun) {
            sendRegulationTick(simulation, tick, now, simulatedTime);
        }

        // 4. Récupérer les setpoints (mode dry-run)
        if (isDryRun) {
            applySetpoints(simulation, tickId, tick);
        }

        // 5. Calculer les totaux
        double totalPowerW = vehicleResults.stream()
            .mapToDouble(GPMVehicleTickResult::getActualPowerW)
            .sum();
        double totalEnergyWh = vehicleResults.stream()
            .mapToDouble(GPMVehicleTickResult::getEnergyChargedWh)
            .sum();

        return GPMTickResult.builder()
            .tick(tick)
            .tickId(tickId)
            .timestamp(now)
            .simulatedTime(simulatedTime)
            .vehicleResults(vehicleResults)
            .totalPowerW(totalPowerW)
            .totalEnergyWh(totalEnergyWh)
            .build();
    }

    private GPMVehicleTickResult calculateVehicleCharge(GPMVehicleState vehicle, int tickMinutes) {
        EVTypeConfig vehicleType = vehicleDatabase.getById(vehicle.getEvTypeId());
        double soc = vehicle.getCurrentSoc();

        // Puissance demandée par le véhicule selon sa courbe
        double requestedPowerW = vehicleType != null ?
            vehicleType.getPowerAtSoc(soc) : vehicle.getMaxPowerW();

        // Appliquer le setpoint si défini
        Double setpointLimit = vehicle.getLastSetpointW();
        double actualPowerW = setpointLimit != null && setpointLimit > 0 ?
            Math.min(requestedPowerW, setpointLimit) : requestedPowerW;

        // Énergie chargée pendant ce tick
        double tickHours = tickMinutes / 60.0;
        double energyWh = actualPowerW * tickHours;

        // Nouveau SOC
        double newSoc = soc + (energyWh / vehicle.getCapacityWh()) * 100;
        newSoc = Math.min(newSoc, vehicle.getTargetSoc());

        // Calculer les courants par phase
        GPMChargeType chargeType = vehicle.getChargeType();
        Double[] phaseCurrents = calculatePhaseCurrents(actualPowerW, chargeType);

        return GPMVehicleTickResult.builder()
            .evseId(vehicle.getEvseId())
            .transactionId(vehicle.getTransactionId())
            .requestedPowerW(requestedPowerW)
            .actualPowerW(actualPowerW)
            .setpointAppliedW(setpointLimit)
            .energyChargedWh(energyWh)
            .socBefore(soc)
            .socAfter(newSoc)
            .currentL1A(phaseCurrents[0])
            .currentL2A(phaseCurrents[1])
            .currentL3A(phaseCurrents[2])
            .build();
    }

    private Double[] calculatePhaseCurrents(double powerW, GPMChargeType chargeType) {
        Double[] currents = new Double[3];

        switch (chargeType) {
            case MONO:
                // Monophasé: tout sur L1
                double currentMono = powerW / chargeType.getVoltageV();
                currents[0] = currentMono;
                currents[1] = null;
                currents[2] = null;
                break;

            case TRI:
                // Triphasé: réparti équitablement sur 3 phases
                double voltageLN = 230.0; // Phase-neutral
                double currentPerPhase = powerW / (3 * voltageLN);
                currents[0] = currentPerPhase;
                currents[1] = currentPerPhase;
                currents[2] = currentPerPhase;
                break;

            case DC:
                // DC: pas de courants AC
                currents[0] = null;
                currents[1] = null;
                currents[2] = null;
                break;
        }

        return currents;
    }

    private void updateVehicleState(GPMVehicleState vehicle, GPMVehicleTickResult result, int tick) {
        vehicle.setCurrentSoc(result.getSocAfter());
        vehicle.setEnergyRegisterWh(vehicle.getEnergyRegisterWh() + result.getEnergyChargedWh());
        vehicle.setCurrentPowerW(result.getActualPowerW());

        // Ajouter à l'historique
        vehicle.getHistory().add(GPMVehicleState.HistoryEntry.builder()
            .tick(tick)
            .soc(result.getSocAfter())
            .powerW(result.getActualPowerW())
            .energyWh(result.getEnergyChargedWh())
            .setpointW(result.getSetpointAppliedW())
            .timestamp(Instant.now())
            .build());

        // Arrêter la charge si SOC cible atteint
        if (vehicle.getCurrentSoc() >= vehicle.getTargetSoc()) {
            vehicle.setCharging(false);
            log.info("Vehicle {} reached target SOC: {:.1f}%",
                vehicle.getEvseId(), vehicle.getCurrentSoc());
        }
    }

    /**
     * Envoie les meter values à l'API TTE.
     * Un appel par véhicule avec le format cdpDto.
     */
    private void sendMeterValues(GPMSimulation simulation, GPMVehicleState vehicle,
                                  GPMVehicleTickResult result, int tick, Instant timestamp) {
        try {
            // Format attendu par l'API TTE
            DryRunMeterValueRequest.MeterValue meterValue = DryRunMeterValueRequest.MeterValue.builder()
                .evseId(vehicle.getEvseId())
                .ocppTransactionId(vehicle.getTransactionId())
                .timestamp(timestamp)
                .energyRegister(vehicle.getEnergyRegisterWh())
                .powerOffered(vehicle.getMaxPowerW())
                .activePower(result.getActualPowerW())
                .stateOfCharge(result.getSocAfter())
                .build();

            DryRunMeterValueRequest.CdpDto cdpDto = DryRunMeterValueRequest.CdpDto.builder()
                .transactionId(vehicle.getTransactionId())
                .evseId(vehicle.getEvseId())
                .meterValue(meterValue)
                .status("UPDATE")
                .build();

            DryRunMeterValueRequest request = DryRunMeterValueRequest.builder()
                .cdpDto(cdpDto)
                .dryRunContext(DryRunMeterValueRequest.DryRunContext.builder()
                    .id(simulation.getDryRunId())
                    .build())
                .clockOverride(timestamp)
                .build();

            dryRunClient.sendMeterValues(request);
            log.debug("Meter value sent for {}: power={}W, soc={}%",
                vehicle.getEvseId(), result.getActualPowerW(), result.getSocAfter());

        } catch (Exception e) {
            log.error("Failed to send meter value for {} at tick {}: {}",
                vehicle.getEvseId(), tick, e.getMessage());
            simulation.getApiErrors().add(GPMApiError.builder()
                .tick(tick)
                .evseId(vehicle.getEvseId())
                .type("MeterValue")
                .message(e.getMessage())
                .timestamp(Instant.now())
                .build());
        }
    }

    /**
     * Envoie un tick de régulation à l'API TTE.
     * Format: { rootId, dryRunContext, clockOverride }
     */
    private void sendRegulationTick(GPMSimulation simulation, int tick,
                                     Instant timestamp, Instant simulatedTime) {
        try {
            DryRunRegulationTickRequest request = DryRunRegulationTickRequest.builder()
                .rootId(simulation.getConfig().getRootNodeId())
                .dryRunContext(DryRunRegulationTickRequest.DryRunContext.builder()
                    .id(simulation.getDryRunId())
                    .build())
                .clockOverride(simulatedTime)
                .build();

            dryRunClient.sendRegulationTick(request);
            log.debug("Regulation tick sent: tick={}, simulatedTime={}", tick, simulatedTime);

        } catch (Exception e) {
            log.error("Failed to send regulation tick {}: {}", tick, e.getMessage());
            simulation.getApiErrors().add(GPMApiError.builder()
                .tick(tick)
                .type("RegulationTick")
                .message(e.getMessage())
                .timestamp(Instant.now())
                .build());
        }
    }

    private void applySetpoints(GPMSimulation simulation, String tickId, int tick) {
        try {
            SetpointResponse response = dryRunClient.getSetpoints(
                simulation.getConfig().getRootNodeId(), tickId);

            if (response != null && response.getSetpoints() != null) {
                // Créer une map evseId -> setpoint pour lookup rapide
                Map<String, SetpointResponse.Setpoint> setpointMap = response.getSetpoints().stream()
                    .collect(Collectors.toMap(SetpointResponse.Setpoint::getEvseId, s -> s));

                // Appliquer aux véhicules
                for (GPMVehicleState vehicle : simulation.getVehicles()) {
                    SetpointResponse.Setpoint setpoint = setpointMap.get(vehicle.getEvseId());
                    if (setpoint != null) {
                        vehicle.setLastSetpointW(setpoint.getMaxPowerW());
                        log.debug("Applied setpoint to {}: {} W",
                            vehicle.getEvseId(), setpoint.getMaxPowerW());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get/apply setpoints at tick {}: {}", tick, e.getMessage());
            simulation.getApiErrors().add(GPMApiError.builder()
                .tick(tick)
                .type("Setpoint")
                .message(e.getMessage())
                .timestamp(Instant.now())
                .build());
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CONVERSION EN RESPONSE
    // ══════════════════════════════════════════════════════════════

    public GPMSimulationResponse toResponse(GPMSimulation simulation, boolean includeResults) {
        double totalEnergy = simulation.getTickResults().stream()
            .mapToDouble(GPMTickResult::getTotalEnergyWh)
            .sum();

        double avgPower = simulation.getTickResults().isEmpty() ? 0 :
            simulation.getTickResults().stream()
                .mapToDouble(GPMTickResult::getTotalPowerW)
                .average()
                .orElse(0);

        double peakPower = simulation.getTickResults().stream()
            .mapToDouble(GPMTickResult::getTotalPowerW)
            .max()
            .orElse(0);

        Instant currentSimulatedTime = null;
        if (!simulation.getTickResults().isEmpty()) {
            currentSimulatedTime = simulation.getTickResults()
                .get(simulation.getTickResults().size() - 1)
                .getSimulatedTime();
        }

        return GPMSimulationResponse.builder()
            .id(simulation.getId())
            .name(simulation.getConfig().getName())
            .dryRunId(simulation.getDryRunId())
            .status(simulation.getStatus())
            .rootNodeId(simulation.getConfig().getRootNodeId())
            .tickIntervalMinutes(simulation.getConfig().getTickIntervalMinutes())
            .numberOfTicks(simulation.getTotalTicks())
            .timeScale(simulation.getConfig().getTimeScale())
            .currentTick(simulation.getCurrentTick())
            .totalTicks(simulation.getTotalTicks())
            .progressPercent(simulation.getTotalTicks() > 0 ?
                (simulation.getCurrentTick() * 100.0 / simulation.getTotalTicks()) : 0)
            .startedAt(simulation.getStartedAt())
            .completedAt(simulation.getCompletedAt())
            .currentSimulatedTime(currentSimulatedTime)
            .vehicles(simulation.getVehicles())
            .tickResults(includeResults ? simulation.getTickResults() : null)
            .apiErrors(simulation.getApiErrors())
            .errorCount(simulation.getApiErrors().size())
            .totalEnergyWh(totalEnergy)
            .averagePowerW(avgPower)
            .peakPowerW(peakPower)
            .build();
    }
}
