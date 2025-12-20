package com.evse.simulator.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Document MongoDB pour les profils de vehicules electriques.
 */
@Document(collection = "vehicleProfiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleProfileDocument {

    @Id
    private String id;

    @Indexed
    private String brand;

    private String model;
    private String variant;

    @Indexed
    private String name;

    private String displayName;
    private String manufacturer;

    // =========================================================================
    // Battery specifications
    // =========================================================================

    private Double batteryCapacityKwh;
    private Double batteryVoltageNominal;
    private Double batteryVoltageMax;

    // =========================================================================
    // AC Charging capabilities
    // =========================================================================

    private Double maxAcPowerKw;

    @Builder.Default
    private Integer maxAcPhases = 3;

    private Double maxAcCurrentA;
    private Double onboardChargerKw;

    // =========================================================================
    // DC Charging capabilities
    // =========================================================================

    private Double maxDcPowerKw;
    private Double maxDcCurrentA;

    // =========================================================================
    // Charging curves (native MongoDB maps)
    // =========================================================================

    private Map<Integer, Double> dcChargingCurve;
    private Map<Integer, Double> voltageCurve;

    // =========================================================================
    // Connectors
    // =========================================================================

    private List<String> connectorTypes;
    private List<String> dcConnectors;

    // =========================================================================
    // Efficiency
    // =========================================================================

    @Builder.Default
    private Double efficiencyAc = 0.90;

    @Builder.Default
    private Double efficiencyDc = 0.92;

    // =========================================================================
    // Defaults
    // =========================================================================

    @Builder.Default
    private Double defaultInitialSoc = 20.0;

    @Builder.Default
    private Double defaultTargetSoc = 80.0;

    @Builder.Default
    private Boolean preconditioning = false;

    // =========================================================================
    // Metadata
    // =========================================================================

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Indexed
    @Builder.Default
    private Boolean active = true;
}
