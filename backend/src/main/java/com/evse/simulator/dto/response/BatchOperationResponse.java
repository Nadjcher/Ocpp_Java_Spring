package com.evse.simulator.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Réponse d'une opération en lot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchOperationResponse {

    /**
     * Type d'opération (create, connect, disconnect, delete).
     */
    private String operation;

    /**
     * Nombre total d'éléments traités.
     */
    private int total;

    /**
     * Nombre de succès.
     */
    private int successful;

    /**
     * Nombre d'échecs.
     */
    private int failed;

    /**
     * IDs des sessions créées/traitées avec succès.
     */
    private List<String> successIds;

    /**
     * IDs des sessions en échec avec les messages d'erreur.
     */
    private List<FailedItem> failures;

    /**
     * Horodatage.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Élément en échec.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedItem {
        private String id;
        private String error;
    }

    /**
     * Crée une réponse de succès complet.
     */
    public static BatchOperationResponse success(String operation, List<String> ids) {
        return BatchOperationResponse.builder()
                .operation(operation)
                .total(ids.size())
                .successful(ids.size())
                .failed(0)
                .successIds(ids)
                .build();
    }
}