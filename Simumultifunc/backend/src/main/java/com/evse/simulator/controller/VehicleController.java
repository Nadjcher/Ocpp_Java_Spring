package com.evse.simulator.controller;

import com.evse.simulator.data.VehicleDatabase;
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

import com.evse.simulator.model.enums.ChargerType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

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

    // =========================================================================
    // ENDPOINTS POUR VehicleDatabase (profils prédéfinis)
    // =========================================================================

    @GetMapping("/database")
    @Operation(summary = "Liste tous les véhicules prédéfinis de la base de données")
    public ResponseEntity<List<VehicleProfile>> getDatabaseVehicles() {
        return ResponseEntity.ok(VehicleDatabase.getAll());
    }

    @GetMapping("/database/{id}")
    @Operation(summary = "Récupère un véhicule prédéfini par ID")
    public ResponseEntity<VehicleProfile> getDatabaseVehicle(@PathVariable String id) {
        return ResponseEntity.ok(VehicleDatabase.getById(id));
    }

    @GetMapping("/database/ids")
    @Operation(summary = "Liste tous les IDs de véhicules prédéfinis")
    public ResponseEntity<List<String>> getDatabaseIds() {
        return ResponseEntity.ok(VehicleDatabase.getAllIds());
    }

    @GetMapping("/database/names")
    @Operation(summary = "Retourne les noms d'affichage des véhicules prédéfinis")
    public ResponseEntity<Map<String, String>> getDatabaseDisplayNames() {
        return ResponseEntity.ok(VehicleDatabase.getDisplayNames());
    }

    @GetMapping("/database/{id}/charging-curve")
    @Operation(summary = "Récupère la courbe de charge DC d'un véhicule prédéfini")
    public ResponseEntity<Map<String, Object>> getDatabaseChargingCurve(@PathVariable String id) {
        VehicleProfile vehicle = VehicleDatabase.getById(id);
        NavigableMap<Integer, Double> curve = vehicle.getDcChargingCurve();

        return ResponseEntity.ok(Map.of(
            "vehicleId", vehicle.getId(),
            "displayName", vehicle.getDisplayName(),
            "maxDcPowerKw", vehicle.getMaxDcPowerKw(),
            "dcChargingCurve", curve != null ? curve : Map.of(),
            "voltageCurve", vehicle.getVoltageCurve() != null ? vehicle.getVoltageCurve() : Map.of()
        ));
    }

    @GetMapping("/database/{id}/power-at-soc")
    @Operation(summary = "Calcule la puissance DC à un SoC donné")
    public ResponseEntity<Map<String, Object>> getPowerAtSoc(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int soc) {

        VehicleProfile vehicle = VehicleDatabase.getById(id);
        double power = vehicle.getDcPowerAtSoc(soc);
        double voltage = vehicle.getVoltageAtSoc(soc);
        double current = vehicle.getDcCurrentAtSoc(soc);

        return ResponseEntity.ok(Map.of(
            "vehicleId", vehicle.getId(),
            "displayName", vehicle.getDisplayName(),
            "soc", soc,
            "powerKw", Math.round(power * 10.0) / 10.0,
            "voltageV", Math.round(voltage * 10.0) / 10.0,
            "currentA", Math.round(current * 10.0) / 10.0
        ));
    }

    @GetMapping("/database/ccs")
    @Operation(summary = "Liste les véhicules supportant le CCS")
    public ResponseEntity<List<VehicleProfile>> getCCSVehicles() {
        return ResponseEntity.ok(VehicleDatabase.getCCSVehicles());
    }

    @GetMapping("/database/chademo")
    @Operation(summary = "Liste les véhicules supportant le CHAdeMO")
    public ResponseEntity<List<VehicleProfile>> getCHAdeMOVehicles() {
        return ResponseEntity.ok(VehicleDatabase.getCHAdeMOVehicles());
    }

    @GetMapping("/database/monophase")
    @Operation(summary = "Liste les véhicules mono-phase")
    public ResponseEntity<List<VehicleProfile>> getMonoPhaseVehicles() {
        return ResponseEntity.ok(VehicleDatabase.getMonoPhaseVehicles());
    }

    @GetMapping("/database/800v")
    @Operation(summary = "Liste les véhicules avec architecture 800V")
    public ResponseEntity<List<VehicleProfile>> get800VVehicles() {
        return ResponseEntity.ok(VehicleDatabase.get800VVehicles());
    }

    @GetMapping("/{id}/compatible-charger-types")
    @Operation(summary = "Retourne les types de chargeurs compatibles avec le véhicule")
    public ResponseEntity<Map<String, Object>> getCompatibleChargerTypes(@PathVariable String id) {
        VehicleProfile vehicle = vehicleService.getVehicle(id);
        List<String> connectors = vehicle.getConnectorTypes() != null
                ? vehicle.getConnectorTypes()
                : List.of("TYPE2", "CCS");
        int acPhases = vehicle.getMaxAcPhases();

        List<String> compatible = new ArrayList<>();
        boolean supportsAC = connectors.stream()
                .anyMatch(c -> c.equalsIgnoreCase("TYPE2") || c.equalsIgnoreCase("CCS"));
        boolean supportsDC = connectors.stream()
                .anyMatch(c -> c.equalsIgnoreCase("CCS") || c.equalsIgnoreCase("CHADEMO"));

        if (supportsAC) {
            compatible.add(ChargerType.AC_MONO.getValue());
            if (acPhases >= 2) compatible.add(ChargerType.AC_BI.getValue());
            if (acPhases >= 3) {
                compatible.add(ChargerType.AC_TRI.getValue());
                compatible.add(ChargerType.AC_TRI_43.getValue());
            }
        }
        if (supportsDC) {
            compatible.add(ChargerType.DC_50.getValue());
            compatible.add(ChargerType.DC_150.getValue());
            compatible.add(ChargerType.DC_350.getValue());
        }

        return ResponseEntity.ok(Map.of(
            "vehicleId", vehicle.getId(),
            "displayName", vehicle.getDisplayName(),
            "connectorTypes", connectors,
            "maxAcPhases", acPhases,
            "compatibleChargerTypes", compatible
        ));
    }

    @GetMapping("/database/{id}/ac-power")
    @Operation(summary = "Calcule la puissance AC effective selon les capacités de la borne")
    public ResponseEntity<Map<String, Object>> getEffectiveAcPower(
            @PathVariable String id,
            @RequestParam(defaultValue = "3") int evsePhases,
            @RequestParam(defaultValue = "32") double evseCurrentA,
            @RequestParam(defaultValue = "230") double evseVoltageV) {

        VehicleProfile vehicle = VehicleDatabase.getById(id);
        double effectivePower = vehicle.getEffectiveAcPower(evsePhases, evseCurrentA, evseVoltageV);

        double evsePower = (evseVoltageV * evseCurrentA * evsePhases) / 1000.0;

        return ResponseEntity.ok(Map.of(
            "vehicleId", vehicle.getId(),
            "displayName", vehicle.getDisplayName(),
            "evsePhases", evsePhases,
            "evsePowerKw", Math.round(evsePower * 100.0) / 100.0,
            "vehicleMaxAcPowerKw", vehicle.getMaxAcPowerKw(),
            "vehicleMaxPhases", vehicle.getMaxAcPhases(),
            "vehicleMaxCurrentA", vehicle.getMaxAcCurrentA(),
            "effectivePowerKw", Math.round(effectivePower * 100.0) / 100.0,
            "limitedBy", effectivePower < vehicle.getMaxAcPowerKw() ?
                (vehicle.getMaxAcPhases() < evsePhases ? "vehicle phases" : "vehicle charger") :
                (evsePower < vehicle.getMaxAcPowerKw() ? "evse" : "both equal")
        ));
    }
}