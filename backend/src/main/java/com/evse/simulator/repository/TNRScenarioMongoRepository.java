package com.evse.simulator.repository;

import com.evse.simulator.model.TNRScenario;
import com.evse.simulator.model.TNRScenario.ScenarioStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository MongoDB pour les scénarios TNR.
 * <p>
 * Ce repository est disponible pour une future migration vers MongoDB.
 * L'application continue de fonctionner avec JsonFileRepository tant que
 * les services ne sont pas modifiés pour utiliser ce repository.
 * </p>
 */
@Repository
public interface TNRScenarioMongoRepository extends MongoRepository<TNRScenario, String> {

    /**
     * Chercher les scénarios par catégorie.
     */
    List<TNRScenario> findByCategory(String category);

    /**
     * Chercher les scénarios par statut.
     */
    List<TNRScenario> findByStatus(ScenarioStatus status);

    /**
     * Chercher les scénarios actifs.
     */
    List<TNRScenario> findByActiveTrue();

    /**
     * Chercher les scénarios par auteur.
     */
    List<TNRScenario> findByAuthor(String author);

    /**
     * Chercher les scénarios contenant un tag.
     */
    List<TNRScenario> findByTagsContaining(String tag);

    /**
     * Chercher les scénarios par nom (contient).
     */
    List<TNRScenario> findByNameContainingIgnoreCase(String name);
}
