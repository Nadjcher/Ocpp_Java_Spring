package com.evse.simulator.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse paginée générique pour les sessions.
 *
 * @param <T> type des éléments dans la liste
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Réponse paginée générique pour les sessions")
public class PagedSessionsResponse<T> {

    @Schema(description = "Nombre total d'éléments")
    private int total;

    @Schema(description = "Limite demandée")
    private int limit;

    @Schema(description = "Décalage (offset) courant")
    private int offset;

    @Schema(description = "Indique s'il existe encore des éléments après ce lot")
    private boolean hasMore;

    @Schema(description = "Prochain offset suggéré")
    private int nextOffset;

    @Schema(description = "Liste des éléments de ce lot")
    private List<T> sessions;
}