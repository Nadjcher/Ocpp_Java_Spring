package com.evse.simulator.gpm.controller;

import com.evse.simulator.gpm.data.GPMVehicleDatabase;
import com.evse.simulator.gpm.dto.*;
import com.evse.simulator.gpm.model.EVTypeConfig;
import com.evse.simulator.gpm.model.GPMSimulation;
import com.evse.simulator.gpm.model.GPMVehicleState;
import com.evse.simulator.gpm.model.enums.GPMChargeType;
import com.evse.simulator.gpm.service.GPMDryRunClient;
import com.evse.simulator.gpm.service.GPMSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour le simulateur GPM.
 */
@Slf4j
@RestController
@RequestMapping("/api/gpm")
@Tag(name = "GPM Simulator", description = "Simulation GPM avec mode Dry-Run")
@RequiredArgsConstructor
public class GPMController {

    private final GPMSimulationService simulationService;
    private final GPMVehicleDatabase vehicleDatabase;
    private final GPMDryRunClient dryRunClient;

    // ══════════════════════════════════════════════════════════════
    // TYPES DE VÉHICULES
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/vehicles/types")
    @Operation(summary = "Liste tous les types de véhicules disponibles")
    public ResponseEntity<List<EVTypeConfig>> getVehicleTypes() {
        return ResponseEntity.ok(vehicleDatabase.getAll());
    }

    @GetMapping("/vehicles/types/{chargeType}")
    @Operation(summary = "Liste les véhicules par type de charge")
    public ResponseEntity<List<EVTypeConfig>> getVehiclesByChargeType(
            @PathVariable GPMChargeType chargeType) {
        return ResponseEntity.ok(vehicleDatabase.getByChargeType(chargeType));
    }

    @GetMapping("/vehicles/types/grouped")
    @Operation(summary = "Liste les véhicules groupés par type de charge")
    public ResponseEntity<Map<GPMChargeType, List<EVTypeConfig>>> getVehiclesGrouped() {
        Map<GPMChargeType, List<EVTypeConfig>> grouped = new HashMap<>();
        for (GPMChargeType type : GPMChargeType.values()) {
            grouped.put(type, vehicleDatabase.getByChargeType(type));
        }
        return ResponseEntity.ok(grouped);
    }

    // ══════════════════════════════════════════════════════════════
    // SIMULATIONS
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/simulations")
    @Operation(summary = "Crée une nouvelle simulation")
    public ResponseEntity<GPMSimulationResponse> createSimulation(
            @Valid @RequestBody GPMSimulationRequest request) {
        GPMSimulation simulation = simulationService.createSimulation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(simulationService.toResponse(simulation, false));
    }

    @GetMapping("/simulations")
    @Operation(summary = "Liste toutes les simulations")
    public ResponseEntity<List<GPMSimulationResponse>> getAllSimulations() {
        List<GPMSimulationResponse> responses = simulationService.getAllSimulations().stream()
            .map(sim -> simulationService.toResponse(sim, false))
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/simulations/{id}")
    @Operation(summary = "Récupère les détails d'une simulation")
    public ResponseEntity<GPMSimulationResponse> getSimulation(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeResults) {
        return simulationService.getSimulation(id)
            .map(sim -> ResponseEntity.ok(simulationService.toResponse(sim, includeResults)))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/simulations/{id}")
    @Operation(summary = "Supprime une simulation")
    public ResponseEntity<Void> deleteSimulation(@PathVariable String id) {
        if (simulationService.deleteSimulation(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ══════════════════════════════════════════════════════════════
    // VÉHICULES DANS SIMULATION
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/simulations/{id}/vehicles")
    @Operation(summary = "Ajoute un véhicule à la simulation")
    public ResponseEntity<?> addVehicle(
            @PathVariable String id,
            @Valid @RequestBody AddVehicleRequest request) {
        try {
            GPMVehicleState vehicle = simulationService.addVehicle(id, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(vehicle);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/simulations/{id}/vehicles/{evseId}")
    @Operation(summary = "Supprime un véhicule de la simulation")
    public ResponseEntity<Void> removeVehicle(
            @PathVariable String id,
            @PathVariable String evseId) {
        if (simulationService.removeVehicle(id, evseId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ══════════════════════════════════════════════════════════════
    // CONTRÔLE DE SIMULATION
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/simulations/{id}/start")
    @Operation(summary = "Démarre la simulation")
    public ResponseEntity<?> startSimulation(@PathVariable String id) {
        return simulationService.getSimulation(id)
            .map(sim -> {
                if (sim.getVehicles().isEmpty()) {
                    return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cannot start simulation without vehicles"));
                }
                simulationService.startSimulation(id);
                return ResponseEntity.accepted()
                    .body(Map.of("message", "Simulation started", "id", id));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/simulations/{id}/stop")
    @Operation(summary = "Arrête la simulation")
    public ResponseEntity<?> stopSimulation(@PathVariable String id) {
        if (simulationService.stopSimulation(id)) {
            return ResponseEntity.ok(Map.of("message", "Simulation stopped", "id", id));
        }
        return ResponseEntity.notFound().build();
    }

    // ══════════════════════════════════════════════════════════════
    // RÉSULTATS
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/simulations/{id}/results")
    @Operation(summary = "Récupère les résultats de la simulation")
    public ResponseEntity<GPMSimulationResponse> getResults(@PathVariable String id) {
        return simulationService.getSimulation(id)
            .map(sim -> ResponseEntity.ok(simulationService.toResponse(sim, true)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/config/charge-types")
    @Operation(summary = "Récupère la configuration des types de charge")
    public ResponseEntity<List<Map<String, Object>>> getChargeTypes() {
        List<Map<String, Object>> types = new java.util.ArrayList<>();
        for (GPMChargeType type : GPMChargeType.values()) {
            Map<String, Object> config = new HashMap<>();
            config.put("type", type.name());
            config.put("label", type.getLabel());
            config.put("phases", type.getPhases());
            config.put("voltageV", type.getVoltageV());
            config.put("maxCurrentA", type.getMaxCurrentA());
            config.put("maxPowerW", type.getMaxPowerW());
            types.add(config);
        }
        return ResponseEntity.ok(types);
    }

    @GetMapping("/config/status")
    @Operation(summary = "Vérifie le statut de la configuration GPM")
    public ResponseEntity<Map<String, Object>> getConfigStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("dryRunEnabled", dryRunClient.isEnabled());
        status.put("connectionOk", dryRunClient.isEnabled() && dryRunClient.testConnection());
        status.put("vehicleTypesCount", vehicleDatabase.getAll().size());

        Map<String, Integer> vehiclesByType = new HashMap<>();
        for (GPMChargeType type : GPMChargeType.values()) {
            vehiclesByType.put(type.name(), vehicleDatabase.getByChargeType(type).size());
        }
        status.put("vehiclesByType", vehiclesByType);

        return ResponseEntity.ok(status);
    }

    // ══════════════════════════════════════════════════════════════
    // API TTE DIRECTE (pour Simu EVSE)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/setpoints")
    @Operation(summary = "Récupère les setpoints depuis l'API TTE")
    public ResponseEntity<?> getSetpoints(
            @RequestParam String rootNodeId,
            @RequestParam(required = false) String tickId) {
        if (!dryRunClient.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "GPM API not configured"));
        }

        try {
            SetpointResponse response = dryRunClient.getSetpoints(rootNodeId, tickId);
            if (response != null) {
                // Ensure setpoints is never null
                if (response.getSetpoints() == null) {
                    response.setSetpoints(List.of());
                }
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.ok(Map.of("setpoints", List.of(), "rootNodeId", rootNodeId));
        } catch (Exception e) {
            log.error("Error fetching setpoints: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/snapshots")
    @Operation(summary = "Récupère les snapshots energy nodes depuis l'API TTE")
    public ResponseEntity<?> getSnapshots(@RequestParam String rootNodeId) {
        if (!dryRunClient.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "GPM API not configured"));
        }

        try {
            EnergyNodeSnapshotResponse response = dryRunClient.getEnergyNodeSnapshots(rootNodeId);
            if (response != null) {
                // Ensure nodes is never null
                if (response.getNodes() == null) {
                    response.setNodes(List.of());
                }
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.ok(Map.of("nodes", List.of(), "rootNodeId", rootNodeId));
        } catch (Exception e) {
            log.error("Error fetching snapshots: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/test-connection")
    @Operation(summary = "Teste la connexion à l'API TTE GPM")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", dryRunClient.isEnabled());

        if (dryRunClient.isEnabled()) {
            boolean connected = dryRunClient.testConnection();
            result.put("connected", connected);
            result.put("message", connected ? "Connexion OK" : "Échec de connexion");
        } else {
            result.put("connected", false);
            result.put("message", "API non configurée (credentials manquants)");
        }

        return ResponseEntity.ok(result);
    }
}
