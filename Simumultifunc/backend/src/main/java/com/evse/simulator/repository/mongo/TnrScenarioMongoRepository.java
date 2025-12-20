package com.evse.simulator.repository.mongo;

import com.evse.simulator.document.TnrScenarioDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for TNR scenario documents.
 */
@Repository
public interface TnrScenarioMongoRepository extends MongoRepository<TnrScenarioDocument, String> {

    // =========================================================================
    // Find by single field
    // =========================================================================

    Optional<TnrScenarioDocument> findByName(String name);

    List<TnrScenarioDocument> findByCategory(String category);

    List<TnrScenarioDocument> findByStatus(String status);

    List<TnrScenarioDocument> findByActiveTrue();

    List<TnrScenarioDocument> findByActiveFalse();

    List<TnrScenarioDocument> findByAuthor(String author);

    // =========================================================================
    // Find by multiple fields
    // =========================================================================

    List<TnrScenarioDocument> findByCategoryAndActiveTrue(String category);

    List<TnrScenarioDocument> findByStatusAndActiveTrue(String status);

    List<TnrScenarioDocument> findByCategoryAndStatus(String category, String status);

    // =========================================================================
    // Tag queries
    // =========================================================================

    @Query("{ 'tags': ?0 }")
    List<TnrScenarioDocument> findByTag(String tag);

    @Query("{ 'tags': { $all: ?0 } }")
    List<TnrScenarioDocument> findByAllTags(List<String> tags);

    @Query("{ 'tags': { $in: ?0 } }")
    List<TnrScenarioDocument> findByAnyTag(List<String> tags);

    // =========================================================================
    // Search queries
    // =========================================================================

    @Query("{ 'name': { $regex: ?0, $options: 'i' } }")
    List<TnrScenarioDocument> searchByName(String namePattern);

    @Query("{ '$or': [ " +
           "{ 'name': { $regex: ?0, $options: 'i' } }, " +
           "{ 'description': { $regex: ?0, $options: 'i' } } " +
           "] }")
    List<TnrScenarioDocument> searchByKeyword(String keyword);

    // =========================================================================
    // Date queries
    // =========================================================================

    List<TnrScenarioDocument> findByLastRunAtAfter(LocalDateTime date);

    List<TnrScenarioDocument> findByLastRunAtBefore(LocalDateTime date);

    @Query("{ 'lastRunAt': null, 'active': true }")
    List<TnrScenarioDocument> findNeverExecuted();

    // =========================================================================
    // Result queries
    // =========================================================================

    @Query("{ 'lastResult.status': 'PASSED', 'active': true }")
    List<TnrScenarioDocument> findLastPassed();

    @Query("{ 'lastResult.status': 'FAILED', 'active': true }")
    List<TnrScenarioDocument> findLastFailed();

    // =========================================================================
    // Count queries
    // =========================================================================

    long countByActiveTrue();

    long countByCategory(String category);

    long countByStatus(String status);

    @Query(value = "{ 'lastResult.status': 'PASSED' }", count = true)
    long countPassed();

    @Query(value = "{ 'lastResult.status': 'FAILED' }", count = true)
    long countFailed();

    // =========================================================================
    // Distinct queries
    // =========================================================================

    @Query(value = "{}", fields = "{ 'category': 1 }")
    List<TnrScenarioDocument> findDistinctCategories();

    @Query(value = "{}", fields = "{ 'tags': 1 }")
    List<TnrScenarioDocument> findAllTags();

    // =========================================================================
    // Existence queries
    // =========================================================================

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, String id);
}
