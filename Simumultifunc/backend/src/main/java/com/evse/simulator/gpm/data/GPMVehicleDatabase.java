package com.evse.simulator.gpm.data;

import com.evse.simulator.gpm.model.EVTypeConfig;
import com.evse.simulator.gpm.model.enums.GPMChargeType;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Base de données des véhicules électriques pour simulation GPM.
 * Véhicules organisés par type de charge (DC, TRI, MONO).
 */
@Component
public class GPMVehicleDatabase {

    private static final Map<String, EVTypeConfig> VEHICLES = new LinkedHashMap<>();
    private static final Map<GPMChargeType, List<EVTypeConfig>> BY_CHARGE_TYPE = new EnumMap<>(GPMChargeType.class);

    static {
        // ══════════════════════════════════════════════════════════════
        // VÉHICULES DC - Charge rapide
        // ══════════════════════════════════════════════════════════════

        addVehicle(EVTypeConfig.builder()
            .id("DC_TESLA_MODEL_3")
            .name("Tesla Model 3 LR (DC 250kW)")
            .chargeType(GPMChargeType.DC)
            .capacityWh(78000)
            .maxPowerW(250000)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 70000),
                Map.entry(5, 170000),
                Map.entry(10, 240000),
                Map.entry(15, 250000),
                Map.entry(20, 250000),
                Map.entry(25, 245000),
                Map.entry(30, 235000),
                Map.entry(35, 220000),
                Map.entry(40, 200000),
                Map.entry(45, 175000),
                Map.entry(50, 155000),
                Map.entry(55, 135000),
                Map.entry(60, 115000),
                Map.entry(65, 100000),
                Map.entry(70, 85000),
                Map.entry(75, 72000),
                Map.entry(80, 60000),
                Map.entry(85, 45000),
                Map.entry(90, 32000),
                Map.entry(95, 18000),
                Map.entry(100, 5000)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("DC_HYUNDAI_IONIQ_5")
            .name("Hyundai Ioniq 5 (DC 233kW)")
            .chargeType(GPMChargeType.DC)
            .capacityWh(74000)
            .maxPowerW(233000)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 65000),
                Map.entry(5, 160000),
                Map.entry(10, 210000),
                Map.entry(15, 230000),
                Map.entry(20, 233000),
                Map.entry(25, 230000),
                Map.entry(30, 225000),
                Map.entry(35, 215000),
                Map.entry(40, 200000),
                Map.entry(45, 180000),
                Map.entry(50, 160000),
                Map.entry(55, 140000),
                Map.entry(60, 120000),
                Map.entry(65, 100000),
                Map.entry(70, 82000),
                Map.entry(75, 65000),
                Map.entry(80, 50000),
                Map.entry(85, 38000),
                Map.entry(90, 25000),
                Map.entry(95, 14000),
                Map.entry(100, 4000)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("DC_VW_ID4")
            .name("VW ID.4 Pro (DC 175kW)")
            .chargeType(GPMChargeType.DC)
            .capacityWh(77000)
            .maxPowerW(175000)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 50000),
                Map.entry(5, 115000),
                Map.entry(10, 155000),
                Map.entry(15, 172000),
                Map.entry(20, 175000),
                Map.entry(25, 175000),
                Map.entry(30, 170000),
                Map.entry(35, 158000),
                Map.entry(40, 142000),
                Map.entry(45, 125000),
                Map.entry(50, 108000),
                Map.entry(55, 92000),
                Map.entry(60, 78000),
                Map.entry(65, 65000),
                Map.entry(70, 54000),
                Map.entry(75, 44000),
                Map.entry(80, 35000),
                Map.entry(85, 26000),
                Map.entry(90, 18000),
                Map.entry(95, 10000),
                Map.entry(100, 4000)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("DC_PEUGEOT_E208")
            .name("Peugeot e-208 (DC 100kW)")
            .chargeType(GPMChargeType.DC)
            .capacityWh(48000)
            .maxPowerW(100000)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 35000),
                Map.entry(5, 70000),
                Map.entry(10, 92000),
                Map.entry(15, 100000),
                Map.entry(20, 100000),
                Map.entry(25, 98000),
                Map.entry(30, 95000),
                Map.entry(35, 88000),
                Map.entry(40, 80000),
                Map.entry(45, 72000),
                Map.entry(50, 65000),
                Map.entry(55, 58000),
                Map.entry(60, 50000),
                Map.entry(65, 42000),
                Map.entry(70, 35000),
                Map.entry(75, 28000),
                Map.entry(80, 22000),
                Map.entry(85, 16000),
                Map.entry(90, 10000),
                Map.entry(95, 5000),
                Map.entry(100, 2000)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("DC_RENAULT_ZOE")
            .name("Renault Zoé (DC 50kW)")
            .chargeType(GPMChargeType.DC)
            .capacityWh(52000)
            .maxPowerW(50000)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 25000),
                Map.entry(5, 42000),
                Map.entry(10, 48000),
                Map.entry(15, 50000),
                Map.entry(20, 50000),
                Map.entry(30, 49000),
                Map.entry(40, 47000),
                Map.entry(50, 44000),
                Map.entry(60, 38000),
                Map.entry(70, 32000),
                Map.entry(80, 24000),
                Map.entry(90, 15000),
                Map.entry(100, 3000)
            )))
            .build());

        // ══════════════════════════════════════════════════════════════
        // VÉHICULES TRI (Triphasé AC) - Charge normale/accélérée 22kW max
        // ══════════════════════════════════════════════════════════════

        addVehicle(EVTypeConfig.builder()
            .id("TRI_RENAULT_ZOE_22")
            .name("Renault Zoé (TRI 22kW)")
            .chargeType(GPMChargeType.TRI)
            .capacityWh(52000)
            .maxPowerW(22000)
            // Courbe AC plate (puissance constante jusqu'à 80% puis dégression)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 22000),
                Map.entry(10, 22000),
                Map.entry(20, 22000),
                Map.entry(30, 22000),
                Map.entry(40, 22000),
                Map.entry(50, 22000),
                Map.entry(60, 22000),
                Map.entry(70, 22000),
                Map.entry(80, 20000),
                Map.entry(85, 16000),
                Map.entry(90, 12000),
                Map.entry(95, 8000),
                Map.entry(100, 2000)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("TRI_TESLA_MODEL_3_11")
            .name("Tesla Model 3 (TRI 11kW)")
            .chargeType(GPMChargeType.TRI)
            .capacityWh(78000)
            .maxPowerW(11000)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 11000),
                Map.entry(10, 11000),
                Map.entry(20, 11000),
                Map.entry(30, 11000),
                Map.entry(40, 11000),
                Map.entry(50, 11000),
                Map.entry(60, 11000),
                Map.entry(70, 11000),
                Map.entry(80, 10000),
                Map.entry(85, 8000),
                Map.entry(90, 6000),
                Map.entry(95, 4000),
                Map.entry(100, 1000)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("TRI_PEUGEOT_E208_11")
            .name("Peugeot e-208 (TRI 11kW)")
            .chargeType(GPMChargeType.TRI)
            .capacityWh(48000)
            .maxPowerW(11000)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 11000),
                Map.entry(10, 11000),
                Map.entry(20, 11000),
                Map.entry(30, 11000),
                Map.entry(40, 11000),
                Map.entry(50, 11000),
                Map.entry(60, 11000),
                Map.entry(70, 11000),
                Map.entry(80, 10000),
                Map.entry(85, 8000),
                Map.entry(90, 6000),
                Map.entry(95, 4000),
                Map.entry(100, 1000)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("TRI_VW_ID4_11")
            .name("VW ID.4 (TRI 11kW)")
            .chargeType(GPMChargeType.TRI)
            .capacityWh(77000)
            .maxPowerW(11000)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 11000),
                Map.entry(10, 11000),
                Map.entry(20, 11000),
                Map.entry(30, 11000),
                Map.entry(40, 11000),
                Map.entry(50, 11000),
                Map.entry(60, 11000),
                Map.entry(70, 11000),
                Map.entry(80, 10000),
                Map.entry(85, 8000),
                Map.entry(90, 6000),
                Map.entry(95, 4000),
                Map.entry(100, 1000)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("TRI_GENERIC_7")
            .name("Véhicule Triphasé 7kW")
            .chargeType(GPMChargeType.TRI)
            .capacityWh(50000)
            .maxPowerW(7000)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 7000),
                Map.entry(10, 7000),
                Map.entry(20, 7000),
                Map.entry(30, 7000),
                Map.entry(40, 7000),
                Map.entry(50, 7000),
                Map.entry(60, 7000),
                Map.entry(70, 7000),
                Map.entry(80, 6500),
                Map.entry(85, 5500),
                Map.entry(90, 4500),
                Map.entry(95, 3000),
                Map.entry(100, 700)
            )))
            .build());

        // ══════════════════════════════════════════════════════════════
        // VÉHICULES MONO (Monophasé AC) - Charge lente 7.36kW max
        // ══════════════════════════════════════════════════════════════

        addVehicle(EVTypeConfig.builder()
            .id("MONO_NISSAN_LEAF")
            .name("Nissan Leaf (MONO 6.6kW)")
            .chargeType(GPMChargeType.MONO)
            .capacityWh(59000)
            .maxPowerW(6600)
            // Courbe plate typique chargeur monophasé
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 6600),
                Map.entry(10, 6600),
                Map.entry(20, 6600),
                Map.entry(30, 6600),
                Map.entry(40, 6600),
                Map.entry(50, 6600),
                Map.entry(60, 6600),
                Map.entry(70, 6600),
                Map.entry(80, 6000),
                Map.entry(85, 5000),
                Map.entry(90, 4000),
                Map.entry(95, 2500),
                Map.entry(100, 500)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("MONO_RENAULT_TWINGO")
            .name("Renault Twingo (MONO 7.4kW)")
            .chargeType(GPMChargeType.MONO)
            .capacityWh(22000)
            .maxPowerW(7400)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 7400),
                Map.entry(10, 7400),
                Map.entry(20, 7400),
                Map.entry(30, 7400),
                Map.entry(40, 7400),
                Map.entry(50, 7400),
                Map.entry(60, 7400),
                Map.entry(70, 7400),
                Map.entry(80, 6800),
                Map.entry(85, 5500),
                Map.entry(90, 4200),
                Map.entry(95, 2800),
                Map.entry(100, 600)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("MONO_SMART_EQ")
            .name("Smart EQ (MONO 4.6kW)")
            .chargeType(GPMChargeType.MONO)
            .capacityWh(17600)
            .maxPowerW(4600)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 4600),
                Map.entry(10, 4600),
                Map.entry(20, 4600),
                Map.entry(30, 4600),
                Map.entry(40, 4600),
                Map.entry(50, 4600),
                Map.entry(60, 4600),
                Map.entry(70, 4600),
                Map.entry(80, 4200),
                Map.entry(85, 3500),
                Map.entry(90, 2800),
                Map.entry(95, 1800),
                Map.entry(100, 400)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("MONO_GENERIC_3")
            .name("Véhicule Monophasé 3.7kW")
            .chargeType(GPMChargeType.MONO)
            .capacityWh(40000)
            .maxPowerW(3700)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 3700),
                Map.entry(10, 3700),
                Map.entry(20, 3700),
                Map.entry(30, 3700),
                Map.entry(40, 3700),
                Map.entry(50, 3700),
                Map.entry(60, 3700),
                Map.entry(70, 3700),
                Map.entry(80, 3400),
                Map.entry(85, 2800),
                Map.entry(90, 2200),
                Map.entry(95, 1400),
                Map.entry(100, 300)
            )))
            .build());

        addVehicle(EVTypeConfig.builder()
            .id("MONO_GENERIC_7")
            .name("Véhicule Monophasé 7.4kW")
            .chargeType(GPMChargeType.MONO)
            .capacityWh(60000)
            .maxPowerW(7400)
            .powerBySoc(createCurve(Map.ofEntries(
                Map.entry(0, 7400),
                Map.entry(10, 7400),
                Map.entry(20, 7400),
                Map.entry(30, 7400),
                Map.entry(40, 7400),
                Map.entry(50, 7400),
                Map.entry(60, 7400),
                Map.entry(70, 7400),
                Map.entry(80, 6800),
                Map.entry(85, 5500),
                Map.entry(90, 4200),
                Map.entry(95, 2800),
                Map.entry(100, 600)
            )))
            .build());

        // Initialiser les maps par type de charge
        for (GPMChargeType type : GPMChargeType.values()) {
            BY_CHARGE_TYPE.put(type, new ArrayList<>());
        }
        for (EVTypeConfig vehicle : VEHICLES.values()) {
            BY_CHARGE_TYPE.get(vehicle.getChargeType()).add(vehicle);
        }
    }

    private static void addVehicle(EVTypeConfig vehicle) {
        VEHICLES.put(vehicle.getId(), vehicle);
    }

    private static NavigableMap<Integer, Integer> createCurve(Map<Integer, Integer> points) {
        return new TreeMap<>(points);
    }

    // ══════════════════════════════════════════════════════════════
    // MÉTHODES D'ACCÈS
    // ══════════════════════════════════════════════════════════════

    /**
     * Récupère un véhicule par son ID.
     */
    public EVTypeConfig getById(String id) {
        return VEHICLES.get(id);
    }

    /**
     * Récupère un véhicule par son ID, avec fallback générique.
     */
    public EVTypeConfig getByIdOrDefault(String id, GPMChargeType chargeType) {
        EVTypeConfig vehicle = VEHICLES.get(id);
        if (vehicle != null) {
            return vehicle;
        }
        // Fallback vers le premier véhicule du type demandé
        List<EVTypeConfig> byType = BY_CHARGE_TYPE.get(chargeType);
        return byType.isEmpty() ? VEHICLES.values().iterator().next() : byType.get(0);
    }

    /**
     * Retourne tous les véhicules.
     */
    public List<EVTypeConfig> getAll() {
        return new ArrayList<>(VEHICLES.values());
    }

    /**
     * Retourne les véhicules par type de charge.
     */
    public List<EVTypeConfig> getByChargeType(GPMChargeType chargeType) {
        return new ArrayList<>(BY_CHARGE_TYPE.get(chargeType));
    }

    /**
     * Retourne les véhicules DC.
     */
    public List<EVTypeConfig> getDCVehicles() {
        return getByChargeType(GPMChargeType.DC);
    }

    /**
     * Retourne les véhicules triphasés.
     */
    public List<EVTypeConfig> getTriVehicles() {
        return getByChargeType(GPMChargeType.TRI);
    }

    /**
     * Retourne les véhicules monophasés.
     */
    public List<EVTypeConfig> getMonoVehicles() {
        return getByChargeType(GPMChargeType.MONO);
    }

    /**
     * Retourne les IDs par type de charge.
     */
    public Map<GPMChargeType, List<String>> getIdsByChargeType() {
        Map<GPMChargeType, List<String>> result = new EnumMap<>(GPMChargeType.class);
        BY_CHARGE_TYPE.forEach((type, vehicles) ->
            result.put(type, vehicles.stream().map(EVTypeConfig::getId).toList())
        );
        return result;
    }

    /**
     * Calcule la puissance pour un véhicule donné à un SOC donné.
     * Tient compte de la limite de setpoint si fournie.
     */
    public double getPowerForVehicle(String vehicleId, double soc, Double setpointLimitW) {
        EVTypeConfig vehicle = VEHICLES.get(vehicleId);
        if (vehicle == null) {
            return 0;
        }

        double power = vehicle.getPowerAtSoc(soc);

        // Appliquer la limite de setpoint si définie
        if (setpointLimitW != null && setpointLimitW > 0) {
            power = Math.min(power, setpointLimitW);
        }

        return power;
    }

    /**
     * Calcule le temps de charge estimé (en minutes) de initialSoc à targetSoc.
     */
    public double estimateChargeTime(String vehicleId, double initialSoc, double targetSoc) {
        EVTypeConfig vehicle = VEHICLES.get(vehicleId);
        if (vehicle == null || initialSoc >= targetSoc) {
            return 0;
        }

        double capacityWh = vehicle.getCapacityWh();
        double energyNeededWh = capacityWh * (targetSoc - initialSoc) / 100.0;

        // Calcul simplifié avec puissance moyenne
        double avgPower = 0;
        int steps = 0;
        for (int soc = (int) initialSoc; soc < targetSoc; soc += 5) {
            avgPower += vehicle.getPowerAtSoc(soc);
            steps++;
        }
        avgPower = steps > 0 ? avgPower / steps : vehicle.getMaxPowerW();

        // Temps en heures puis en minutes
        double timeHours = energyNeededWh / avgPower;
        return timeHours * 60;
    }
}
