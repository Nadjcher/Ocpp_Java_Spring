package com.evse.simulator.controller;

import com.evse.simulator.model.VehicleProfile;
import com.evse.simulator.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour la gestion des profils de véhicules.
 */
@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicles", description = "Gestion des profils de véhicules électriques")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    @Operation(summary = "Liste tous les profils de véhicules")
    public ResponseEntity<List<VehicleProfile>> getAllVehicles() {
        return ResponseEntity.ok(vehicleService.getAllVehicles());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupère un profil par ID")
    public ResponseEntity<VehicleProfile> getVehicle(@PathVariable String id) {
        return ResponseEntity.ok(vehicleService.getVehicle(id));
    }

    @PostMapping
    @Operation(summary = "Crée un nouveau profil")
    public ResponseEntity<VehicleProfile> createVehicle(@Valid @RequestBody VehicleProfile vehicle) {
        VehicleProfile created = vehicleService.createVehicle(vehicle);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Met à jour un profil")
    public ResponseEntity<VehicleProfile> updateVehicle(
            @PathVariable String id,
            @RequestBody VehicleProfile updates) {
        return ResponseEntity.ok(vehicleService.updateVehicle(id, updates));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprime un profil")
    public ResponseEntity<Void> deleteVehicle(@PathVariable String id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/manufacturer/{manufacturer}")
    @Operation(summary = "Recherche par fabricant")
    public ResponseEntity<List<VehicleProfile>> findByManufacturer(@PathVariable String manufacturer) {
        return ResponseEntity.ok(vehicleService.findByManufacturer(manufacturer));
    }

    @GetMapping("/connector/{connectorType}")
    @Operation(summary = "Recherche par type de connecteur")
    public ResponseEntity<List<VehicleProfile>> findByConnectorType(@PathVariable String connectorType) {
        return ResponseEntity.ok(vehicleService.findByConnectorType(connectorType));
    }

    @GetMapping("/{id}/charging-time")
    @Operation(summary = "Estime le temps de charge")
    public ResponseEntity<Map<String, Object>> estimateChargingTime(
            @PathVariable String id,
            @RequestParam(defaultValue = "20") double startSoc,
            @RequestParam(defaultValue = "80") double targetSoc,
            @RequestParam(defaultValue = "22") double powerKw,
            @RequestParam(defaultValue = "false") boolean isDC) {

        int minutes = vehicleService.estimateChargingTime(id, startSoc, targetSoc, powerKw, isDC);

        return ResponseEntity.ok(Map.of(
                "vehicleId", id,
                "startSoc", startSoc,
                "targetSoc", targetSoc,
                "availablePowerKw", powerKw,
                "chargingType", isDC ? "DC" : "AC",
                "estimatedMinutes", minutes,
                "estimatedTime", formatTime(minutes)
        ));
    }

    private String formatTime(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        int hours = minutes / 60;
        int mins = minutes % 60;
        return String.format("%dh %02dmin", hours, mins);
    }
}