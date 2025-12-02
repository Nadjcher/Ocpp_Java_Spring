package com.evse.simulator.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Profil d'un véhicule électrique.
 * <p>
 * Définit les caractéristiques de charge d'un véhicule EV,
 * incluant la courbe de charge et les limites.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleProfile {

    /**
     * Identifiant unique du profil.
     */
    @NotBlank(message = "L'identifiant est obligatoire")
    private String id;

    /**
     * Nom du véhicule.
     */
    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    /**
     * Fabricant du véhicule.
     */
    private String manufacturer;

    /**
     * Capacité de la batterie en kWh.
     */
    @Min(1)
    @Builder.Default
    private double batteryCapacityKwh = 50.0;

    /**
     * Puissance de charge maximale globale en kW.
     */
    @Min(1)
    @Builder.Default
    private double maxChargingPowerKw = 100.0;

    /**
     * Puissance de charge AC maximale en kW.
     */
    @Min(1)
    @Builder.Default
    private double maxAcChargingPowerKw = 11.0;

    /**
     * Puissance de charge DC maximale en kW.
     */
    @Min(1)
    @Builder.Default
    private double maxDcChargingPowerKw = 100.0;

    /**
     * Puissance du chargeur embarqué en kW.
     */
    @Min(1)
    @Builder.Default
    private double onboardChargerKw = 11.0;

    /**
     * Types de connecteurs supportés.
     */
    @Builder.Default
    private List<String> connectorTypes = List.of("TYPE2", "CCS");

    /**
     * Courbe de charge (puissance en fonction du SoC).
     */
    private List<ChargingCurvePoint> chargingCurve;

    /**
     * Efficacité de charge (0.0 - 1.0).
     */
    @Builder.Default
    private double efficiency = 0.90;

    /**
     * Support du préconditionnement batterie.
     */
    @Builder.Default
    private boolean preconditioning = false;

    /**
     * Calcule la puissance de charge pour un SoC donné.
     *
     * @param soc State of Charge (0-100)
     * @param isAC true pour charge AC, false pour DC
     * @return puissance en kW
     */
    public double getPowerAtSoc(double soc, boolean isAC) {
        double maxPower = isAC ? maxAcChargingPowerKw : maxDcChargingPowerKw;

        if (chargingCurve == null || chargingCurve.isEmpty()) {
            // Courbe de charge linéaire par défaut
            if (soc < 20) {
                return maxPower * 0.8;
            } else if (soc < 50) {
                return maxPower;
            } else if (soc < 80) {
                return maxPower * (1.0 - (soc - 50) / 60.0);
            } else {
                return maxPower * 0.3 * (1.0 - (soc - 80) / 40.0);
            }
        }

        // Interpolation sur la courbe de charge
        ChargingCurvePoint prevPoint = null;
        for (ChargingCurvePoint point : chargingCurve) {
            if (point.getSoc() >= soc) {
                if (prevPoint == null) {
                    return Math.min(point.getPowerKw(), maxPower);
                }
                // Interpolation linéaire
                double ratio = (soc - prevPoint.getSoc()) /
                        (point.getSoc() - prevPoint.getSoc());
                double power = prevPoint.getPowerKw() +
                        ratio * (point.getPowerKw() - prevPoint.getPowerKw());
                return Math.min(power, maxPower);
            }
            prevPoint = point;
        }

        // Au-delà de la courbe
        return chargingCurve.get(chargingCurve.size() - 1).getPowerKw();
    }

    /**
     * Calcule le temps de charge estimé.
     *
     * @param startSoc SoC de départ (0-100)
     * @param targetSoc SoC cible (0-100)
     * @param chargingPowerKw puissance disponible en kW
     * @param isAC true pour charge AC
     * @return temps estimé en minutes
     */
    public int estimateChargingTime(double startSoc, double targetSoc,
                                    double chargingPowerKw, boolean isAC) {
        if (startSoc >= targetSoc) {
            return 0;
        }

        double energyNeeded = batteryCapacityKwh * (targetSoc - startSoc) / 100.0;
        double avgPower = 0;
        int steps = (int) (targetSoc - startSoc);

        for (int i = 0; i < steps; i++) {
            double soc = startSoc + i;
            double vehiclePower = getPowerAtSoc(soc, isAC);
            avgPower += Math.min(vehiclePower, chargingPowerKw);
        }
        avgPower /= steps;

        // Ajout de l'efficacité
        double effectivePower = avgPower * efficiency;

        return (int) Math.ceil(energyNeeded / effectivePower * 60);
    }

    /**
     * Point sur la courbe de charge.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChargingCurvePoint {
        /**
         * State of Charge (0-100).
         */
        private double soc;

        /**
         * Puissance en kW à ce SoC.
         */
        private double powerKw;
    }
}