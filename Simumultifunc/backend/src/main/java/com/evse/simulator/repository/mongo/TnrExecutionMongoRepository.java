package com.evse.simulator.repository.mongo;

import com.evse.simulator.document.TnrExecutionDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB repository for TNR execution documents.
 */
@Repository
public interface TnrExecutionMongoRepository extends MongoRepository<TnrExecutionDocument, String> {

    // =========================================================================
    // Find by scenario
    // =========================================================================

    List<TnrExecutionDocument> findByScenarioId(String scenarioId);

    List<TnrExecutionDocument> findByScenarioIdOrderByExecutedAtDesc(String scenarioId);

    Page<TnrExecutionDocument> findByScenarioId(String scenarioId, Pageable pageable);

    // =========================================================================
    // Find by status
    // =========================================================================

    List<TnrExecutionDocument> findByStatus(String status);

    List<TnrExecutionDocument> findByStatusOrderByExecutedAtDesc(String status);

    // =========================================================================
    // Find by scenario and status
    // =========================================================================

    List<TnrExecutionDocument> findByScenarioIdAndStatus(String scenarioId, String status);

    // =========================================================================
    // Date queries
    // =========================================================================

    List<TnrExecutionDocument> findByExecutedAtAfter(LocalDateTime date);

    List<TnrExecutionDocument> findByExecutedAtBefore(LocalDateTime date);

    List<TnrExecutionDocument> findByExecutedAtBetween(LocalDateTime from, LocalDateTime to);

    // =========================================================================
    // Latest executions
    // =========================================================================

    @Query(value = "{ 'scenarioId': ?0 }", sort = "{ 'executedAt': -1 }")
    List<TnrExecutionDocument> findLatestByScenarioId(String scenarioId);

    List<TnrExecutionDocument> findTop1ByScenarioIdOrderByExecutedAtDesc(String scenarioId);

    List<TnrExecutionDocument> findTop10ByOrderByExecutedAtDesc();

    // =========================================================================
    // Count queries
    // =========================================================================

    long countByScenarioId(String scenarioId);

    long countByStatus(String status);

    long countByScenarioIdAndStatus(String scenarioId, String status);

    @Query(value = "{ 'executedAt': { $gte: ?0 } }", count = true)
    long countExecutionsSince(LocalDateTime date);

    // =========================================================================
    // Statistics
    // =========================================================================

    @Query(value = "{ 'scenarioId': ?0, 'status': 'PASSED' }", count = true)
    long countPassedByScenario(String scenarioId);

    @Query(value = "{ 'scenarioId': ?0, 'status': 'FAILED' }", count = true)
    long countFailedByScenario(String scenarioId);

    // =========================================================================
    // Cleanup
    // =========================================================================

    void deleteByScenarioId(String scenarioId);

    void deleteByExecutedAtBefore(LocalDateTime threshold);

    @Query(value = "{ 'scenarioId': ?0, 'executedAt': { $lt: ?1 } }", delete = true)
    void deleteOldExecutions(String scenarioId, LocalDateTime threshold);
}
