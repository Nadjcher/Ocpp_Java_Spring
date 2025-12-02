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
 * Requête de démarrage d'un test de charge.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadTestRequest {

    /**
     * Nombre de sessions cibles.
     */
    @Min(1)
    @Max(25000)
    @Builder.Default
    private int targetSessions = 100;

    /**
     * Temps de montée en charge en secondes.
     */
    @Min(0)
    @Builder.Default
    private int rampUpSeconds = 60;

    /**
     * URL du CSMS.
     */
    @NotBlank(message = "L'URL du CSMS est obligatoire")
    private String csmsUrl;

    /**
     * Préfixe des ID de Charge Point.
     */
    @Builder.Default
    private String cpIdPrefix = "LOAD_TEST_CP";

    /**
     * Type de chargeur.
     */
    @Builder.Default
    private ChargerType chargerType = ChargerType.AC_TRI;

    /**
     * Tag RFID.
     */
    @Builder.Default
    private String idTag = "LOAD_TEST";

    /**
     * SoC initial.
     */
    @Min(0)
    @Max(100)
    @Builder.Default
    private double initialSoc = 20.0;

    /**
     * SoC cible.
     */
    @Min(0)
    @Max(100)
    @Builder.Default
    private double targetSoc = 80.0;

    /**
     * Token Bearer (optionnel).
     */
    private String bearerToken;
}