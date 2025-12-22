package com.evse.simulator.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

/**
 * Profil d'un véhicule électrique avec ses caractéristiques de charge.
 * Contient les courbes de charge DC réalistes basées sur des données terrain.
 * <p>
 * Annotation @Document pour MongoDB (optionnel - fonctionne aussi sans MongoDB).
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "vehicle_profiles")
public class VehicleProfile {

    // =========================================================================
    // Identité
    // =========================================================================

    @Id
    @NotBlank(message = "L'identifiant est obligatoire")
    private String id;

    private String brand;

    private String model;

    private String variant;

    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    private String displayName;

    private String manufacturer;

    // =========================================================================
    // Batterie
    // =========================================================================

    /** Capacité utilisable en kWh */
    @Min(1)
    @Builder.Default
    private double batteryCapacityKwh = 50.0;

    /** Tension nominale en V */
    @Builder.Default
    private double batteryVoltageNominal = 350.0;

    /** Tension maximale en V */
    @Builder.Default
    private double batteryVoltageMax = 400.0;

    // =========================================================================
    // Charge AC
    // =========================================================================

    /** Puissance max AC en kW */
    @Min(1)
    @Builder.Default
    private double maxAcPowerKw = 11.0;

    /** Phases supportées (1 ou 3) */
    @Builder.Default
    private int maxAcPhases = 3;

    /** Courant max AC par phase en A */
    @Builder.Default
    private double maxAcCurrentA = 16.0;

    // Legacy fields for compatibility
    @Min(1)
    @Builder.Default
    private double maxAcChargingPowerKw = 11.0;

    @Min(1)
    @Builder.Default
    private double onboardChargerKw = 11.0;

    // =========================================================================
    // Charge DC
    // =========================================================================

    /** Puissance max DC en kW */
    @Min(1)
    @Builder.Default
    private double maxDcPowerKw = 100.0;

    /** Courant max DC en A */
    @Builder.Default
    private double maxDcCurrentA = 250.0;

    // Legacy field
    @Min(1)
    @Builder.Default
    private double maxDcChargingPowerKw = 100.0;

    /** Puissance de charge maximale globale en kW (legacy) */
    @Min(1)
    @Builder.Default
    private double maxChargingPowerKw = 100.0;

    // =========================================================================
    // Courbes de charge
    // =========================================================================

    /** Courbe de charge DC (SoC% -> Puissance kW) - Nouvelle structure */
    private NavigableMap<Integer, Double> dcChargingCurve;

    /** Courbe tension (SoC% -> Voltage V) */
    private NavigableMap<Integer, Double> voltageCurve;

    /** Courbe de charge legacy (liste de points) */
    private List<ChargingCurvePoint> chargingCurve;

    // =========================================================================
    // Connecteurs
    // =========================================================================

    /** Types de connecteurs supportés */
    @Builder.Default
    private List<String> connectorTypes = List.of("TYPE2", "CCS");

    /** Types de connecteurs DC supportés: "CCS" ou "CHAdeMO" */
    private List<String> dcConnectors;

    // =========================================================================
    // Efficacité
    // =========================================================================

    /** Efficacité charge AC (0.90 = 90%) */
    @Builder.Default
    private double efficiencyAc = 0.90;

    /** Efficacité charge DC (0.92 = 92%) */
    @Builder.Default
    private double efficiencyDc = 0.92;

    /** Efficacité générale (legacy) */
    @Builder.Default
    private double efficiency = 0.90;

    // =========================================================================
    // SoC par défaut
    // =========================================================================

    @Builder.Default
    private int defaultInitialSoc = 20;

    @Builder.Default
    private int defaultTargetSoc = 80;

    /** Support du préconditionnement batterie */
    @Builder.Default
    private boolean preconditioning = false;

    // =========================================================================
    // Méthodes de calcul - Nouvelle implémentation
    // =========================================================================

    /**
     * Puissance DC interpolée selon le SoC.
     * Utilise une interpolation linéaire entre les points de la courbe.
     *
     * @param soc State of Charge en %
     * @return Puissance en kW
     */
    public double getDcPowerAtSoc(int soc) {
        if (dcChargingCurve == null || dcChargingCurve.isEmpty()) {
            // Fallback vers l'ancienne méthode
            return getPowerAtSoc(soc, false);
        }

        Map.Entry<Integer, Double> floor = dcChargingCurve.floorEntry(soc);
        Map.Entry<Integer, Double> ceil = dcChargingCurve.ceilingEntry(soc);

        if (floor == null) return ceil != null ? ceil.getValue() : maxDcPowerKw;
        if (ceil == null) return floor.getValue();
        if (floor.getKey().equals(ceil.getKey())) return floor.getValue();

        // Interpolation linéaire
        double ratio = (double)(soc - floor.getKey()) / (ceil.getKey() - floor.getKey());
        return floor.getValue() + ratio * (ceil.getValue() - floor.getValue());
    }

    /**
     * Tension interpolée selon le SoC.
     *
     * @param soc State of Charge en %
     * @return Tension en V
     */
    public double getVoltageAtSoc(int soc) {
        if (voltageCurve == null || voltageCurve.isEmpty()) {
            return batteryVoltageNominal;
        }

        Map.Entry<Integer, Double> floor = voltageCurve.floorEntry(soc);
        Map.Entry<Integer, Double> ceil = voltageCurve.ceilingEntry(soc);

        if (floor == null) return ceil != null ? ceil.getValue() : batteryVoltageNominal;
        if (ceil == null) return floor.getValue();
        if (floor.getKey().equals(ceil.getKey())) return floor.getValue();

        double ratio = (double)(soc - floor.getKey()) / (ceil.getKey() - floor.getKey());
        return floor.getValue() + ratio * (ceil.getValue() - floor.getValue());
    }

    /**
     * Courant DC calculé selon le SoC (P = U * I).
     *
     * @param soc State of Charge en %
     * @return Courant en A
     */
    public double getDcCurrentAtSoc(int soc) {
        return (getDcPowerAtSoc(soc) * 1000) / getVoltageAtSoc(soc);
    }

    /**
     * Puissance AC effective selon les capacités de la borne.
     * Prend en compte les limites de phases et de courant.
     *
     * @param evsePhases Nombre de phases de la borne
     * @param evseCurrentA Courant max de la borne par phase
     * @param evseVoltageV Tension de la borne
     * @return Puissance effective en kW
     */
    public double getEffectiveAcPower(int evsePhases, double evseCurrentA, double evseVoltageV) {
        // Le véhicule est limité par le nombre de phases qu'il supporte
        int phases = Math.min(evsePhases, maxAcPhases);
        // Et par le courant max de son chargeur embarqué
        double current = Math.min(evseCurrentA, maxAcCurrentA);
        // Calcul de la puissance
        double power = (evseVoltageV * current * phases) / 1000.0;
        // Limité par la puissance max du chargeur embarqué
        return Math.min(power, maxAcPowerKw);
    }

    // =========================================================================
    // Méthodes legacy pour compatibilité
    // =========================================================================

    /**
     * Calcule la puissance de charge pour un SoC donné.
     *
     * @param soc State of Charge (0-100)
     * @param isAC true pour charge AC, false pour DC
     * @return puissance en kW
     */
    public double getPowerAtSoc(double soc, boolean isAC) {
        double maxPower = isAC ? maxAcPowerKw : maxDcPowerKw;

        // Si courbe DC disponible et mode DC
        if (!isAC && dcChargingCurve != null && !dcChargingCurve.isEmpty()) {
            return getDcPowerAtSoc((int) soc);
        }

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
        double eff = isAC ? efficiencyAc : efficiencyDc;
        double effectivePower = avgPower * eff;

        return (int) Math.ceil(energyNeeded / effectivePower * 60);
    }

    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

    /**
     * Vérifie si le véhicule supporte le connecteur CCS.
     */
    public boolean supportsCCS() {
        return (dcConnectors != null && dcConnectors.contains("CCS")) ||
               (connectorTypes != null && connectorTypes.contains("CCS"));
    }

    /**
     * Vérifie si le véhicule supporte le connecteur CHAdeMO.
     */
    public boolean supportsCHAdeMO() {
        return (dcConnectors != null && dcConnectors.contains("CHAdeMO")) ||
               (connectorTypes != null && connectorTypes.contains("CHAdeMO"));
    }

    /**
     * Vérifie si le véhicule est mono-phase.
     */
    public boolean isMonoPhase() {
        return maxAcPhases == 1;
    }

    /**
     * Vérifie si le véhicule a une architecture 800V.
     */
    public boolean is800V() {
        return batteryVoltageMax >= 700;
    }

    /**
     * Retourne le displayName ou construit un nom à partir des autres champs.
     */
    public String getDisplayName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        if (name != null && !name.isBlank()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        if (brand != null) sb.append(brand).append(" ");
        if (model != null) sb.append(model).append(" ");
        if (variant != null) sb.append(variant);
        return sb.toString().trim();
    }

    // =========================================================================
    // Classes internes
    // =========================================================================

    /**
     * Point sur la courbe de charge (legacy).
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
