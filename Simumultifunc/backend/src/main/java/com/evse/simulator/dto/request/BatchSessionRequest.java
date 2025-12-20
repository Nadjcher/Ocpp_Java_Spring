package com.evse.simulator.dto.request;

import com.evse.simulator.model.enums.ChargerType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Requête de création de sessions en lot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSessionRequest {

    /**
     * Nombre de sessions à créer.
     */
    @Min(1)
    @Max(1000)
    @Builder.Default
    private int count = 10;

    /**
     * URL du CSMS.
     */
    @NotBlank(message = "L'URL du CSMS est obligatoire")
    private String url;

    /**
     * Préfixe de l'ID Charge Point.
     */
    @Builder.Default
    private String cpIdPrefix = "CP";

    /**
     * Type de chargeur.
     */
    @Builder.Default
    private ChargerType chargerType = ChargerType.AC_TRI;

    /**
     * Tag RFID.
     */
    @Builder.Default
    private String idTag = "BATCH";

    /**
     * SoC initial.
     */
    @Min(0)
    @Max(100)
    @Builder.Default
    private double soc = 20.0;

    /**
     * SoC cible.
     */
    @Min(0)
    @Max(100)
    @Builder.Default
    private double targetSoc = 80.0;

    /**
     * Connexion automatique après création.
     */
    @Builder.Default
    private boolean autoConnect = false;

    /**
     * Token Bearer (optionnel).
     */
    private String bearerToken;
}
