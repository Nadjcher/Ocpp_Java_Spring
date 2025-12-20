package com.evse.simulator.service;

import com.evse.simulator.exception.VehicleNotFoundException;
import com.evse.simulator.model.VehicleProfile;
import com.evse.simulator.repository.DataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service de gestion des profils de véhicules électriques.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VehicleService implements com.evse.simulator.domain.service.VehicleService {

    private final DataRepository repository;

    /**
     * Récupère tous les profils de véhicules.
     *
     * @return liste de tous les profils
     */
    public List<VehicleProfile> getAllVehicles() {
        return repository.findAllVehicles();
    }

    /**
     * Récupère un profil par ID.
     *
     * @param id identifiant du profil
     * @return le profil trouvé
     * @throws VehicleNotFoundException si le profil n'existe pas
     */
    public VehicleProfile getVehicle(String id) {
        return repository.findVehicleById(id)
                .orElseThrow(() -> new VehicleNotFoundException(id));
    }

    /**
     * Récupère un profil par ID (Optional).
     *
     * @param id identifiant du profil
     * @return Optional contenant le profil ou vide
     */
    public Optional<VehicleProfile> findVehicle(String id) {
        return repository.findVehicleById(id);
    }

    /**
     * Crée un nouveau profil de véhicule.
     *
     * @param vehicle données du profil
     * @return le profil créé
     */
    public VehicleProfile createVehicle(VehicleProfile vehicle) {
        if (vehicle.getId() == null || vehicle.getId().isBlank()) {
            vehicle.setId(UUID.randomUUID().toString());
        }

        // Vérifier que l'ID n'existe pas déjà
        if (repository.findVehicleById(vehicle.getId()).isPresent()) {
            throw new IllegalArgumentException("Vehicle with ID " + vehicle.getId() + " already exists");
        }

        VehicleProfile saved = repository.saveVehicle(vehicle);
        log.info("Created vehicle profile: {} - {}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Met à jour un profil existant.
     *
     * @param id identifiant du profil
     * @param updates données de mise à jour
     * @return le profil mis à jour
     */
    public VehicleProfile updateVehicle(String id, VehicleProfile updates) {
        VehicleProfile vehicle = getVehicle(id);

        if (updates.getName() != null) {
            vehicle.setName(updates.getName());
        }
        if (updates.getManufacturer() != null) {
            vehicle.setManufacturer(updates.getManufacturer());
        }
        if (updates.getBatteryCapacityKwh() > 0) {
            vehicle.setBatteryCapacityKwh(updates.getBatteryCapacityKwh());
        }
        if (updates.getMaxChargingPowerKw() > 0) {
            vehicle.setMaxChargingPowerKw(updates.getMaxChargingPowerKw());
        }
        if (updates.getMaxAcChargingPowerKw() > 0) {
            vehicle.setMaxAcChargingPowerKw(updates.getMaxAcChargingPowerKw());
        }
        if (updates.getMaxDcChargingPowerKw() > 0) {
            vehicle.setMaxDcChargingPowerKw(updates.getMaxDcChargingPowerKw());
        }
        if (updates.getOnboardChargerKw() > 0) {
            vehicle.setOnboardChargerKw(updates.getOnboardChargerKw());
        }
        if (updates.getConnectorTypes() != null) {
            vehicle.setConnectorTypes(updates.getConnectorTypes());
        }
        if (updates.getChargingCurve() != null) {
            vehicle.setChargingCurve(updates.getChargingCurve());
        }
        if (updates.getEfficiency() > 0) {
            vehicle.setEfficiency(updates.getEfficiency());
        }
        vehicle.setPreconditioning(updates.isPreconditioning());

        VehicleProfile saved = repository.saveVehicle(vehicle);
        log.debug("Updated vehicle profile: {}", saved.getId());
        return saved;
    }

    /**
     * Supprime un profil de véhicule.
     *
     * @param id identifiant du profil
     */
    public void deleteVehicle(String id) {
        getVehicle(id); // Vérifie l'existence
        repository.deleteVehicle(id);
        log.info("Deleted vehicle profile: {}", id);
    }

    /**
     * Recherche des véhicules par fabricant.
     *
     * @param manufacturer nom du fabricant
     * @return liste des véhicules du fabricant
     */
    public List<VehicleProfile> findByManufacturer(String manufacturer) {
        return repository.findAllVehicles().stream()
                .filter(v -> v.getManufacturer() != null &&
                        v.getManufacturer().equalsIgnoreCase(manufacturer))
                .toList();
    }

    /**
     * Recherche des véhicules supportant un type de connecteur.
     *
     * @param connectorType type de connecteur (TYPE2, CCS, CHADEMO)
     * @return liste des véhicules compatibles
     */
    public List<VehicleProfile> findByConnectorType(String connectorType) {
        return repository.findAllVehicles().stream()
                .filter(v -> v.getConnectorTypes() != null &&
                        v.getConnectorTypes().stream()
                                .anyMatch(c -> c.equalsIgnoreCase(connectorType)))
                .toList();
    }

    /**
     * Calcule le temps de charge estimé pour un véhicule.
     *
     * @param vehicleId ID du véhicule
     * @param startSoc SoC de départ
     * @param targetSoc SoC cible
     * @param availablePowerKw puissance disponible
     * @param isDC charge DC ou AC
     * @return temps estimé en minutes
     */
    public int estimateChargingTime(String vehicleId, double startSoc, double targetSoc,
                                    double availablePowerKw, boolean isDC) {
        VehicleProfile vehicle = getVehicle(vehicleId);
        return vehicle.estimateChargingTime(startSoc, targetSoc, availablePowerKw, !isDC);
    }
}