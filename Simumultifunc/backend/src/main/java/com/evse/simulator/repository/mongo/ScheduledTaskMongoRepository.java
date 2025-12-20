package com.evse.simulator.repository.mongo;

import com.evse.simulator.document.ScheduledTaskDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for scheduled task documents.
 */
@Repository
public interface ScheduledTaskMongoRepository extends MongoRepository<ScheduledTaskDocument, String> {

    // =========================================================================
    // Find by single field
    // =========================================================================

    Optional<ScheduledTaskDocument> findByName(String name);

    List<ScheduledTaskDocument> findByEnabledTrue();

    List<ScheduledTaskDocument> findByEnabledFalse();

    List<ScheduledTaskDocument> findByScheduleType(String scheduleType);

    List<ScheduledTaskDocument> findByActionType(String actionType);

    // =========================================================================
    // Find by multiple fields
    // =========================================================================

    List<ScheduledTaskDocument> findByEnabledTrueAndScheduleType(String scheduleType);

    // =========================================================================
    // Scheduling queries
    // =========================================================================

    @Query("{ 'enabled': true, 'nextRunAt': { $lte: ?0 } }")
    List<ScheduledTaskDocument> findDueTasks(LocalDateTime now);

    @Query("{ 'enabled': true, 'nextRunAt': { $gte: ?0, $lte: ?1 } }")
    List<ScheduledTaskDocument> findTasksInWindow(LocalDateTime from, LocalDateTime to);

    List<ScheduledTaskDocument> findByNextRunAtBefore(LocalDateTime threshold);

    List<ScheduledTaskDocument> findByNextRunAtAfter(LocalDateTime threshold);

    // =========================================================================
    // Status queries
    // =========================================================================

    List<ScheduledTaskDocument> findByLastRunStatus(String status);

    @Query("{ 'lastRunStatus': 'ERROR', 'enabled': true }")
    List<ScheduledTaskDocument> findFailedTasks();

    @Query("{ 'failCount': { $gt: ?0 } }")
    List<ScheduledTaskDocument> findByFailCountGreaterThan(int minFails);

    // =========================================================================
    // Search queries
    // =========================================================================

    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    List<ScheduledTaskDocument> searchByName(String namePattern);

    @Query("{ '$or': [ " +
           "{ 'name': { $regex: ?0, $options: 'i' } }, " +
           "{ 'description': { $regex: ?0, $options: 'i' } } " +
           "] }")
    List<ScheduledTaskDocument> searchByKeyword(String keyword);

    // =========================================================================
    // Statistics queries
    // =========================================================================

    @Query(value = "{ 'enabled': true }", count = true)
    long countEnabled();

    @Query(value = "{ 'lastRunStatus': 'SUCCESS' }", count = true)
    long countSuccessful();

    @Query(value = "{ 'lastRunStatus': 'ERROR' }", count = true)
    long countFailed();

    // =========================================================================
    // Ordered queries
    // =========================================================================

    List<ScheduledTaskDocument> findByEnabledTrueOrderByNextRunAtAsc();

    List<ScheduledTaskDocument> findTop10ByEnabledTrueOrderByNextRunAtAsc();

    // =========================================================================
    // Existence queries
    // =========================================================================

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, String id);
}
