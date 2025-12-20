package com.evse.simulator.repository;

import com.evse.simulator.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository MongoDB pour les Users.
 *
 * MongoRepository donne automatiquement :
 * - findAll()     : récupérer tous les users
 * - findById(id)  : récupérer un user par son ID
 * - save(user)    : sauvegarder un user
 * - delete(user)  : supprimer un user
 * - count()       : compter les users
 *
 * Tu peux ajouter tes propres méthodes ci-dessous.
 * Spring crée automatiquement la requête MongoDB !
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Chercher un user par email
     * Spring comprend tout seul : findBy + Email = chercher par le champ "email"
     */
    Optional<User> findByEmail(String email);

    /**
     * Chercher tous les users avec un nom
     */
    List<User> findByNom(String nom);

    /**
     * Vérifier si un email existe déjà
     */
    boolean existsByEmail(String email);
}
