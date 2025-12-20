package com.evse.simulator.repository.mongo;

import com.evse.simulator.document.SessionDocument;
import com.evse.simulator.model.enums.SessionState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for session documents.
 */
@Repository
public interface SessionMongoRepository extends MongoRepository<SessionDocument, String> {

    // =========================================================================
    // Find by single field
    // =========================================================================

    Optional<SessionDocument> findByCpId(String cpId);

    List<SessionDocument> findByState(SessionState state);

    List<SessionDocument> findByConnectedTrue();

    List<SessionDocument> findByConnectedFalse();

    List<SessionDocument> findByChargingTrue();

    List<SessionDocument> findByBackgroundedTrue();

    // =========================================================================
    // Find by multiple fields
    // =========================================================================

    List<SessionDocument> findByCpIdAndState(String cpId, SessionState state);

    List<SessionDocument> findByStateIn(List<SessionState> states);

    List<SessionDocument> findByConnectedTrueAndChargingTrue();

    // =========================================================================
    // Custom queries
    // =========================================================================

    @Query("{ 'state': { $in: ['CHARGING', 'PREPARING', 'SUSPENDED_EV', 'SUSPENDED_EVSE'] } }")
    List<SessionDocument> findActiveSessions();

    @Query("{ 'updatedAt': { $lt: ?0 }, 'connected': true }")
    List<SessionDocument> findStaleConnections(LocalDateTime threshold);

    @Query("{ 'transactionId': { $ne: null } }")
    List<SessionDocument> findWithActiveTransaction();

    @Query(value = "{ 'cpId': ?0 }", fields = "{ 'logs': 0, 'socData': 0, 'powerData': 0, 'ocppMessages': 0 }")
    Optional<SessionDocument> findByCpIdWithoutEmbedded(String cpId);

    // =========================================================================
    // Count queries
    // =========================================================================

    long countByState(SessionState state);

    long countByConnectedTrue();

    long countByChargingTrue();

    // =========================================================================
    // Delete queries
    // =========================================================================

    void deleteByCpId(String cpId);

    void deleteByStateAndUpdatedAtBefore(SessionState state, LocalDateTime threshold);

    // =========================================================================
    // Existence queries
    // =========================================================================

    boolean existsByCpId(String cpId);

    boolean existsByCpIdAndConnectedTrue(String cpId);
}
