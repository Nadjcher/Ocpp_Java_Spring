package com.evse.simulator.domain.service;

import com.evse.simulator.model.VehicleProfile;

import java.util.List;
import java.util.Optional;

/**
 * Interface de gestion des profils de vehicules electriques.
 */
public interface VehicleService {

    // CRUD Operations
    List<VehicleProfile> getAllVehicles();
    VehicleProfile getVehicle(String id);
    Optional<VehicleProfile> findVehicle(String id);
    VehicleProfile createVehicle(VehicleProfile vehicle);
    VehicleProfile updateVehicle(String id, VehicleProfile updates);
    void deleteVehicle(String id);

    // Queries
    List<VehicleProfile> findByManufacturer(String manufacturer);
    List<VehicleProfile> findByConnectorType(String connectorType);

    // Calculations
    int estimateChargingTime(String vehicleId, double startSoc, double targetSoc,
                            double availablePowerKw, boolean isDC);
}