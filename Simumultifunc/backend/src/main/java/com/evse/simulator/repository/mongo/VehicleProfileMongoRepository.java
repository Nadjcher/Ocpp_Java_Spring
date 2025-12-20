package com.evse.simulator.repository.mongo;

import com.evse.simulator.document.VehicleProfileDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for vehicle profile documents.
 */
@Repository
public interface VehicleProfileMongoRepository extends MongoRepository<VehicleProfileDocument, String> {

    // =========================================================================
    // Find by single field
    // =========================================================================

    Optional<VehicleProfileDocument> findByName(String name);

    List<VehicleProfileDocument> findByBrand(String brand);

    List<VehicleProfileDocument> findByActiveTrue();

    List<VehicleProfileDocument> findByActiveFalse();

    // =========================================================================
    // Find by multiple fields
    // =========================================================================

    List<VehicleProfileDocument> findByBrandAndActiveTrue(String brand);

    Optional<VehicleProfileDocument> findByBrandAndModel(String brand, String model);

    Optional<VehicleProfileDocument> findByBrandAndModelAndVariant(String brand, String model, String variant);

    // =========================================================================
    // Search queries
    // =========================================================================

    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    List<VehicleProfileDocument> searchByName(String namePattern);

    @Query("{ '$or': [ " +
           "{ 'name': { $regex: ?0, $options: 'i' } }, " +
           "{ 'brand': { $regex: ?0, $options: 'i' } }, " +
           "{ 'model': { $regex: ?0, $options: 'i' } } " +
           "] }")
    List<VehicleProfileDocument> searchByKeyword(String keyword);

    // =========================================================================
    // Filter queries
    // =========================================================================

    @Query("{ 'maxDcPowerKw': { $gte: ?0 }, 'active': true }")
    List<VehicleProfileDocument> findByMinDcPower(double minPowerKw);

    @Query("{ 'maxAcPowerKw': { $gte: ?0 }, 'active': true }")
    List<VehicleProfileDocument> findByMinAcPower(double minPowerKw);

    @Query("{ 'batteryCapacityKwh': { $gte: ?0, $lte: ?1 }, 'active': true }")
    List<VehicleProfileDocument> findByBatteryCapacityRange(double minKwh, double maxKwh);

    @Query("{ 'connectorTypes': ?0, 'active': true }")
    List<VehicleProfileDocument> findByConnectorType(String connectorType);

    // =========================================================================
    // Count queries
    // =========================================================================

    long countByActiveTrue();

    long countByBrand(String brand);

    // =========================================================================
    // Distinct queries
    // =========================================================================

    @Query(value = "{}", fields = "{ 'brand': 1 }")
    List<VehicleProfileDocument> findDistinctBrands();

    // =========================================================================
    // Existence queries
    // =========================================================================

    boolean existsByName(String name);

    boolean existsByBrandAndModel(String brand, String model);
}
