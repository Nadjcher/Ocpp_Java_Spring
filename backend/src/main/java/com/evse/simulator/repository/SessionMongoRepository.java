package com.evse.simulator.repository;

import com.evse.simulator.model.Session;
import com.evse.simulator.model.enums.SessionState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository MongoDB pour les Sessions.
 * <p>
 * Ce repository est disponible pour une future migration vers MongoDB.
 * L'application continue de fonctionner avec JsonFileRepository tant que
 * les services ne sont pas modifiés pour utiliser ce repository.
 * </p>
 */
@Repository
public interface SessionMongoRepository extends MongoRepository<Session, String> {

    /**
     * Chercher une session par son Charge Point ID.
     */
    Optional<Session> findByCpId(String cpId);

    /**
     * Chercher toutes les sessions par état.
     */
    List<Session> findByState(SessionState state);

    /**
     * Chercher toutes les sessions connectées.
     */
    List<Session> findByConnectedTrue();

    /**
     * Chercher toutes les sessions en charge.
     */
    List<Session> findByChargingTrue();

    /**
     * Chercher les sessions par URL du CSMS.
     */
    List<Session> findByUrl(String url);

    /**
     * Vérifier si une session existe avec ce Charge Point ID.
     */
    boolean existsByCpId(String cpId);
}
