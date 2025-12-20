package com.evse.simulator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Exemple simple de Document MongoDB.
 *
 * @Document = dit à Spring que c'est une "fiche" MongoDB
 * collection = le nom du "tiroir" dans MongoDB
 */
@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * ID unique (MongoDB génère automatiquement un ObjectId)
     */
    @Id
    private String id;

    /**
     * Nom de l'utilisateur
     */
    private String nom;

    /**
     * Email
     */
    private String email;

    /**
     * Date de création
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
