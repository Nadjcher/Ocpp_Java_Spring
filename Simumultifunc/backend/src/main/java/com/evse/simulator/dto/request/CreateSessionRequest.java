package com.evse.simulator.dto.request;

import com.evse.simulator.model.enums.ChargerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Requete de creation de session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Creation d'une nouvelle session de charge")
public class CreateSessionRequest {

    @Schema(description = "Titre de la session (optionnel)", example = "Test Session 1")
    @Size(max = 100, message = "Le titre ne peut pas depasser 100 caracteres")
    private String title;

    @Schema(description = "URL du CSMS", example = "wss://evse-test.total-ev-charge.com/ocpp/WebSocket", required = true)
    @NotBlank(message = "L'URL du CSMS est obligatoire")
    private String url;

    @Schema(description = "Identifiant du Charge Point", example = "CP001", required = true)
    @NotBlank(message = "L'ID du Charge Point est obligatoire")
    @Size(max = 50, message = "L'ID du CP ne peut pas depasser 50 caracteres")
    private String cpId;

    @Schema(description = "Token d'authentification Bearer (optionnel)", example = "eyJhbGciOiJIUzI1NiIs...")
    private String bearerToken;

    @Schema(description = "ID du profil vehicule", example = "TESLA_MODEL3_LR")
    private String vehicleProfileId;

    @Schema(description = "Type de chargeur", example = "AC_TRI",
            allowableValues = {"AC_MONO", "AC_BI", "AC_TRI", "DC"})
    @Builder.Default
    private ChargerType chargerType = ChargerType.AC_TRI;

    @Schema(description = "Tag RFID pour l'autorisation", example = "ABCD1234")
    @Builder.Default
    private String idTag = "EVSE001";

    @Schema(description = "Etat de charge initial (%)", example = "20", minimum = "0", maximum = "100")
    @Min(value = 0, message = "Le SoC initial doit etre >= 0")
    @Max(value = 100, message = "Le SoC initial doit etre <= 100")
    @Builder.Default
    private double soc = 20.0;

    @Schema(description = "Etat de charge cible (%)", example = "80", minimum = "1", maximum = "100")
    @Min(value = 1, message = "Le SoC cible doit etre >= 1")
    @Max(value = 100, message = "Le SoC cible doit etre <= 100")
    @Builder.Default
    private double targetSoc = 80.0;

    @Schema(description = "Intervalle heartbeat en secondes", example = "30", minimum = "1")
    @Min(value = 1, message = "L'intervalle heartbeat doit etre >= 1")
    @Builder.Default
    private int heartbeatInterval = 30;

    @Schema(description = "Intervalle MeterValues en secondes", example = "10", minimum = "1")
    @Min(value = 1, message = "L'intervalle MeterValues doit etre >= 1")
    @Builder.Default
    private int meterValuesInterval = 10;
}
