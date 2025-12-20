package com.evse.simulator.repository.mongo;

import com.evse.simulator.document.OcpiPartnerDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for OCPI partner documents.
 */
@Repository
public interface OcpiPartnerMongoRepository extends MongoRepository<OcpiPartnerDocument, String> {

    // =========================================================================
    // Find by single field
    // =========================================================================

    Optional<OcpiPartnerDocument> findByName(String name);

    List<OcpiPartnerDocument> findByActiveTrue();

    List<OcpiPartnerDocument> findByActiveFalse();

    List<OcpiPartnerDocument> findByStatus(String status);

    List<OcpiPartnerDocument> findByCountryCode(String countryCode);

    // =========================================================================
    // Find by multiple fields
    // =========================================================================

    Optional<OcpiPartnerDocument> findByCountryCodeAndPartyId(String countryCode, String partyId);

    List<OcpiPartnerDocument> findByStatusAndActiveTrue(String status);

    // =========================================================================
    // Search queries
    // =========================================================================

    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    List<OcpiPartnerDocument> searchByName(String namePattern);

    @Query("{ '$or': [ " +
           "{ 'name': { $regex: ?0, $options: 'i' } }, " +
           "{ 'description': { $regex: ?0, $options: 'i' } } " +
           "] }")
    List<OcpiPartnerDocument> searchByKeyword(String keyword);

    // =========================================================================
    // Module queries
    // =========================================================================

    @Query("{ 'supportedModules': ?0 }")
    List<OcpiPartnerDocument> findBySupportedModule(String module);

    @Query("{ 'supportedModules': { $all: ?0 } }")
    List<OcpiPartnerDocument> findByAllSupportedModules(List<String> modules);

    // =========================================================================
    // Count queries
    // =========================================================================

    long countByActiveTrue();

    long countByStatus(String status);

    // =========================================================================
    // Existence queries
    // =========================================================================

    boolean existsByName(String name);

    boolean existsByCountryCodeAndPartyId(String countryCode, String partyId);
}
