package com.evse.simulator.gpm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request pour ajouter un véhicule à une simulation GPM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddVehicleRequest {

    @NotBlank(message = "L'evseId est requis")
    private String evseId;

    @NotBlank(message = "Le type de véhicule est requis")
    private String evTypeId;

    @Min(value = 0, message = "Le SOC initial doit être >= 0")
    @Max(value = 100, message = "Le SOC initial doit être <= 100")
    @Builder.Default
    private double initialSoc = 20.0;

    @Min(value = 0, message = "Le SOC cible doit être >= 0")
    @Max(value = 100, message = "Le SOC cible doit être <= 100")
    @Builder.Default
    private double targetSoc = 80.0;

    // Optionnel: override du transactionId (sinon généré)
    private String transactionId;
}
