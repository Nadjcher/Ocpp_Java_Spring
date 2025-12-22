package com.evse.simulator.repository;

import com.evse.simulator.model.VehicleProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository MongoDB pour les profils de véhicules.
 * <p>
 * Ce repository est disponible pour une future migration vers MongoDB.
 * L'application continue de fonctionner avec JsonFileRepository tant que
 * les services ne sont pas modifiés pour utiliser ce repository.
 * </p>
 */
@Repository
public interface VehicleProfileMongoRepository extends MongoRepository<VehicleProfile, String> {

    /**
     * Chercher un véhicule par marque.
     */
    List<VehicleProfile> findByBrand(String brand);

    /**
     * Chercher un véhicule par marque et modèle.
     */
    Optional<VehicleProfile> findByBrandAndModel(String brand, String model);

    /**
     * Chercher un véhicule par nom.
     */
    Optional<VehicleProfile> findByName(String name);

    /**
     * Chercher les véhicules supportant CCS.
     */
    List<VehicleProfile> findByConnectorTypesContaining(String connectorType);

    /**
     * Chercher les véhicules avec une capacité batterie minimum.
     */
    List<VehicleProfile> findByBatteryCapacityKwhGreaterThanEqual(double minCapacity);

    /**
     * Chercher les véhicules avec une puissance DC minimum.
     */
    List<VehicleProfile> findByMaxDcPowerKwGreaterThanEqual(double minPower);
}
