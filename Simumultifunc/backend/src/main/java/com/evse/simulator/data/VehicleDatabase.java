package com.evse.simulator.data;

import com.evse.simulator.model.VehicleProfile;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Base de données des profils véhicules.
 * Contient 6 véhicules représentatifs du marché avec courbes de charge réalistes.
 */
@Component
public class VehicleDatabase {

    private static final Map<String, VehicleProfile> VEHICLES = new LinkedHashMap<>();

    static {

        // ══════════════════════════════════════════════════════════════
        // 1. TESLA MODEL 3 LONG RANGE
        // Best-seller mondial, référence charge rapide 250kW
        // ══════════════════════════════════════════════════════════════

        VEHICLES.put("TESLA_MODEL_3_LR", VehicleProfile.builder()
            .id("TESLA_MODEL_3_LR")
            .brand("Tesla")
            .model("Model 3")
            .variant("Long Range")
            .name("Tesla Model 3 Long Range")
            .displayName("Tesla Model 3 Long Range")
            // Batterie 82 kWh brut, 78 kWh utilisable
            .batteryCapacityKwh(78.0)
            .batteryVoltageNominal(357)
            .batteryVoltageMax(410)
            // AC: 11kW triphasé, 16A/phase
            .maxAcPowerKw(11.0)
            .maxAcPhases(3)
            .maxAcCurrentA(16.0)
            .maxAcChargingPowerKw(11.0)
            .onboardChargerKw(11.0)
            // DC: 250kW max
            .maxDcPowerKw(250.0)
            .maxDcCurrentA(625.0)
            .maxDcChargingPowerKw(250.0)
            .maxChargingPowerKw(250.0)
            // Courbe DC réelle (Fastned/Ionity data)
            .dcChargingCurve(new TreeMap<>(Map.ofEntries(
                Map.entry(0, 70.0),     // Démarrage progressif
                Map.entry(5, 170.0),
                Map.entry(10, 240.0),
                Map.entry(15, 250.0),   // Peak
                Map.entry(20, 250.0),
                Map.entry(25, 245.0),
                Map.entry(30, 235.0),
                Map.entry(35, 220.0),
                Map.entry(40, 200.0),
                Map.entry(45, 175.0),
                Map.entry(50, 155.0),
                Map.entry(55, 135.0),
                Map.entry(60, 115.0),
                Map.entry(65, 100.0),
                Map.entry(70, 85.0),
                Map.entry(75, 72.0),
                Map.entry(80, 60.0),
                Map.entry(85, 45.0),
                Map.entry(90, 32.0),
                Map.entry(95, 18.0),
                Map.entry(100, 5.0)
            )))
            .voltageCurve(new TreeMap<>(Map.of(
                0, 320.0, 20, 350.0, 40, 370.0, 60, 385.0, 80, 400.0, 100, 410.0
            )))
            .dcConnectors(List.of("CCS"))
            .connectorTypes(List.of("TYPE2", "CCS"))
            .efficiencyAc(0.90)
            .efficiencyDc(0.93)
            .efficiency(0.92)
            .defaultInitialSoc(20)
            .defaultTargetSoc(80)
            .preconditioning(true)
            .build());

        // ══════════════════════════════════════════════════════════════
        // 2. RENAULT ZOÉ R135
        // Best-seller France, excellente en AC (22kW triphasé)
        // ══════════════════════════════════════════════════════════════

        VEHICLES.put("RENAULT_ZOE_R135", VehicleProfile.builder()
            .id("RENAULT_ZOE_R135")
            .brand("Renault")
            .model("Zoé")
            .variant("R135 ZE50")
            .name("Renault Zoé R135 52kWh")
            .displayName("Renault Zoé R135 52kWh")
            // Batterie 55 kWh brut, 52 kWh utilisable
            .batteryCapacityKwh(52.0)
            .batteryVoltageNominal(350)
            .batteryVoltageMax(400)
            // AC: 22kW triphasé ! (meilleure du marché)
            .maxAcPowerKw(22.0)
            .maxAcPhases(3)
            .maxAcCurrentA(32.0)
            .maxAcChargingPowerKw(22.0)
            .onboardChargerKw(22.0)
            // DC: 50kW seulement
            .maxDcPowerKw(50.0)
            .maxDcCurrentA(125.0)
            .maxDcChargingPowerKw(50.0)
            .maxChargingPowerKw(50.0)
            // Courbe DC (charge DC limitée)
            .dcChargingCurve(new TreeMap<>(Map.ofEntries(
                Map.entry(0, 25.0),
                Map.entry(5, 42.0),
                Map.entry(10, 48.0),
                Map.entry(15, 50.0),    // Peak
                Map.entry(20, 50.0),
                Map.entry(30, 49.0),
                Map.entry(40, 47.0),
                Map.entry(50, 44.0),
                Map.entry(60, 38.0),
                Map.entry(70, 32.0),
                Map.entry(80, 24.0),
                Map.entry(90, 15.0),
                Map.entry(100, 3.0)
            )))
            .voltageCurve(new TreeMap<>(Map.of(
                0, 290.0, 20, 330.0, 40, 355.0, 60, 375.0, 80, 390.0, 100, 400.0
            )))
            .dcConnectors(List.of("CCS"))
            .connectorTypes(List.of("TYPE2", "CCS"))
            .efficiencyAc(0.88)
            .efficiencyDc(0.90)
            .efficiency(0.89)
            .defaultInitialSoc(20)
            .defaultTargetSoc(80)
            .preconditioning(false)
            .build());

        // ══════════════════════════════════════════════════════════════
        // 3. PEUGEOT e-208
        // Stellantis e-CMP, très vendu en France
        // ══════════════════════════════════════════════════════════════

        VEHICLES.put("PEUGEOT_E208", VehicleProfile.builder()
            .id("PEUGEOT_E208")
            .brand("Peugeot")
            .model("e-208")
            .variant("156ch 51kWh")
            .name("Peugeot e-208 51kWh")
            .displayName("Peugeot e-208 51kWh")
            // Batterie 51 kWh brut, 48 kWh utilisable
            .batteryCapacityKwh(48.0)
            .batteryVoltageNominal(352)
            .batteryVoltageMax(395)
            // AC: 11kW triphasé
            .maxAcPowerKw(11.0)
            .maxAcPhases(3)
            .maxAcCurrentA(16.0)
            .maxAcChargingPowerKw(11.0)
            .onboardChargerKw(11.0)
            // DC: 100kW
            .maxDcPowerKw(100.0)
            .maxDcCurrentA(255.0)
            .maxDcChargingPowerKw(100.0)
            .maxChargingPowerKw(100.0)
            // Courbe DC réelle
            .dcChargingCurve(new TreeMap<>(Map.ofEntries(
                Map.entry(0, 35.0),
                Map.entry(5, 70.0),
                Map.entry(10, 92.0),
                Map.entry(15, 100.0),   // Peak
                Map.entry(20, 100.0),
                Map.entry(25, 98.0),
                Map.entry(30, 95.0),
                Map.entry(35, 88.0),
                Map.entry(40, 80.0),
                Map.entry(45, 72.0),
                Map.entry(50, 65.0),
                Map.entry(55, 58.0),
                Map.entry(60, 50.0),
                Map.entry(65, 42.0),
                Map.entry(70, 35.0),
                Map.entry(75, 28.0),
                Map.entry(80, 22.0),
                Map.entry(85, 16.0),
                Map.entry(90, 10.0),
                Map.entry(95, 5.0),
                Map.entry(100, 2.0)
            )))
            .voltageCurve(new TreeMap<>(Map.of(
                0, 300.0, 20, 340.0, 40, 360.0, 60, 378.0, 80, 390.0, 100, 395.0
            )))
            .dcConnectors(List.of("CCS"))
            .connectorTypes(List.of("TYPE2", "CCS"))
            .efficiencyAc(0.89)
            .efficiencyDc(0.91)
            .efficiency(0.90)
            .defaultInitialSoc(20)
            .defaultTargetSoc(80)
            .preconditioning(false)
            .build());

        // ══════════════════════════════════════════════════════════════
        // 4. HYUNDAI IONIQ 5 LONG RANGE
        // Architecture 800V, référence charge ultra-rapide
        // ══════════════════════════════════════════════════════════════

        VEHICLES.put("HYUNDAI_IONIQ_5_LR", VehicleProfile.builder()
            .id("HYUNDAI_IONIQ_5_LR")
            .brand("Hyundai")
            .model("Ioniq 5")
            .variant("Long Range 77kWh")
            .name("Hyundai Ioniq 5 Long Range")
            .displayName("Hyundai Ioniq 5 Long Range")
            // Batterie 77.4 kWh brut, 74 kWh utilisable
            .batteryCapacityKwh(74.0)
            // Architecture 800V !
            .batteryVoltageNominal(697)
            .batteryVoltageMax(800)
            // AC: 11kW triphasé (limité par chargeur embarqué)
            .maxAcPowerKw(11.0)
            .maxAcPhases(3)
            .maxAcCurrentA(16.0)
            .maxAcChargingPowerKw(11.0)
            .onboardChargerKw(11.0)
            // DC: 233kW - charge ultra-rapide grâce au 800V
            .maxDcPowerKw(233.0)
            .maxDcCurrentA(290.0)
            .maxDcChargingPowerKw(233.0)
            .maxChargingPowerKw(233.0)
            // Courbe DC 800V (excellente)
            .dcChargingCurve(new TreeMap<>(Map.ofEntries(
                Map.entry(0, 65.0),
                Map.entry(5, 160.0),
                Map.entry(10, 210.0),
                Map.entry(15, 230.0),
                Map.entry(20, 233.0),   // Peak
                Map.entry(25, 230.0),
                Map.entry(30, 225.0),
                Map.entry(35, 215.0),
                Map.entry(40, 200.0),
                Map.entry(45, 180.0),
                Map.entry(50, 160.0),
                Map.entry(55, 140.0),
                Map.entry(60, 120.0),
                Map.entry(65, 100.0),
                Map.entry(70, 82.0),
                Map.entry(75, 65.0),
                Map.entry(80, 50.0),
                Map.entry(85, 38.0),
                Map.entry(90, 25.0),
                Map.entry(95, 14.0),
                Map.entry(100, 4.0)
            )))
            .voltageCurve(new TreeMap<>(Map.of(
                0, 600.0, 20, 680.0, 40, 720.0, 60, 760.0, 80, 785.0, 100, 800.0
            )))
            .dcConnectors(List.of("CCS"))
            .connectorTypes(List.of("TYPE2", "CCS"))
            .efficiencyAc(0.90)
            .efficiencyDc(0.94)  // 800V très efficace
            .efficiency(0.92)
            .defaultInitialSoc(20)
            .defaultTargetSoc(80)
            .preconditioning(true)
            .build());

        // ══════════════════════════════════════════════════════════════
        // 5. NISSAN LEAF e+ 62kWh
        // Véhicule historique, MONO-PHASE + CHAdeMO (cas spécial)
        // ══════════════════════════════════════════════════════════════

        VEHICLES.put("NISSAN_LEAF_62", VehicleProfile.builder()
            .id("NISSAN_LEAF_62")
            .brand("Nissan")
            .model("Leaf")
            .variant("e+ 62kWh")
            .name("Nissan Leaf e+ 62kWh")
            .displayName("Nissan Leaf e+ 62kWh")
            // Batterie 62 kWh brut, 59 kWh utilisable
            .batteryCapacityKwh(59.0)
            .batteryVoltageNominal(350)
            .batteryVoltageMax(395)
            // AC: 6.6kW MONO-PHASE seulement !
            .maxAcPowerKw(6.6)
            .maxAcPhases(1)  // MONO-PHASE
            .maxAcCurrentA(32.0)
            .maxAcChargingPowerKw(6.6)
            .onboardChargerKw(6.6)
            // DC: 100kW via CHAdeMO
            .maxDcPowerKw(100.0)
            .maxDcCurrentA(255.0)
            .maxDcChargingPowerKw(100.0)
            .maxChargingPowerKw(100.0)
            // Courbe DC
            .dcChargingCurve(new TreeMap<>(Map.ofEntries(
                Map.entry(0, 40.0),
                Map.entry(5, 75.0),
                Map.entry(10, 92.0),
                Map.entry(15, 100.0),   // Peak
                Map.entry(20, 100.0),
                Map.entry(25, 95.0),
                Map.entry(30, 88.0),
                Map.entry(35, 78.0),
                Map.entry(40, 68.0),
                Map.entry(45, 58.0),
                Map.entry(50, 50.0),
                Map.entry(55, 42.0),
                Map.entry(60, 36.0),
                Map.entry(65, 30.0),
                Map.entry(70, 25.0),
                Map.entry(75, 20.0),
                Map.entry(80, 16.0),
                Map.entry(85, 12.0),
                Map.entry(90, 8.0),
                Map.entry(95, 4.0),
                Map.entry(100, 2.0)
            )))
            .voltageCurve(new TreeMap<>(Map.of(
                0, 300.0, 20, 335.0, 40, 355.0, 60, 375.0, 80, 388.0, 100, 395.0
            )))
            .dcConnectors(List.of("CHAdeMO"))  // Pas de CCS !
            .connectorTypes(List.of("TYPE2", "CHAdeMO"))
            .efficiencyAc(0.88)
            .efficiencyDc(0.89)
            .efficiency(0.88)
            .defaultInitialSoc(20)
            .defaultTargetSoc(80)
            .preconditioning(false)
            .build());

        // ══════════════════════════════════════════════════════════════
        // 6. VOLKSWAGEN ID.4 PRO
        // Groupe VW, plateforme MEB
        // ══════════════════════════════════════════════════════════════

        VEHICLES.put("VW_ID4_PRO", VehicleProfile.builder()
            .id("VW_ID4_PRO")
            .brand("Volkswagen")
            .model("ID.4")
            .variant("Pro 77kWh")
            .name("Volkswagen ID.4 Pro 77kWh")
            .displayName("Volkswagen ID.4 Pro 77kWh")
            // Batterie 82 kWh brut, 77 kWh utilisable
            .batteryCapacityKwh(77.0)
            .batteryVoltageNominal(352)
            .batteryVoltageMax(408)
            // AC: 11kW triphasé
            .maxAcPowerKw(11.0)
            .maxAcPhases(3)
            .maxAcCurrentA(16.0)
            .maxAcChargingPowerKw(11.0)
            .onboardChargerKw(11.0)
            // DC: 175kW
            .maxDcPowerKw(175.0)
            .maxDcCurrentA(430.0)
            .maxDcChargingPowerKw(175.0)
            .maxChargingPowerKw(175.0)
            // Courbe DC MEB
            .dcChargingCurve(new TreeMap<>(Map.ofEntries(
                Map.entry(0, 50.0),
                Map.entry(5, 115.0),
                Map.entry(10, 155.0),
                Map.entry(15, 172.0),
                Map.entry(20, 175.0),   // Peak
                Map.entry(25, 175.0),
                Map.entry(30, 170.0),
                Map.entry(35, 158.0),
                Map.entry(40, 142.0),
                Map.entry(45, 125.0),
                Map.entry(50, 108.0),
                Map.entry(55, 92.0),
                Map.entry(60, 78.0),
                Map.entry(65, 65.0),
                Map.entry(70, 54.0),
                Map.entry(75, 44.0),
                Map.entry(80, 35.0),
                Map.entry(85, 26.0),
                Map.entry(90, 18.0),
                Map.entry(95, 10.0),
                Map.entry(100, 4.0)
            )))
            .voltageCurve(new TreeMap<>(Map.of(
                0, 300.0, 20, 345.0, 40, 370.0, 60, 390.0, 80, 402.0, 100, 408.0
            )))
            .dcConnectors(List.of("CCS"))
            .connectorTypes(List.of("TYPE2", "CCS"))
            .efficiencyAc(0.89)
            .efficiencyDc(0.92)
            .efficiency(0.90)
            .defaultInitialSoc(20)
            .defaultTargetSoc(80)
            .preconditioning(false)
            .build());

        // ══════════════════════════════════════════════════════════════
        // GENERIC - Véhicule générique par défaut
        // ══════════════════════════════════════════════════════════════

        VEHICLES.put("GENERIC", VehicleProfile.builder()
            .id("GENERIC")
            .brand("Generic")
            .model("EV")
            .variant("Standard")
            .name("Generic Electric Vehicle")
            .displayName("Generic Electric Vehicle")
            .batteryCapacityKwh(60.0)
            .batteryVoltageNominal(350)
            .batteryVoltageMax(400)
            .maxAcPowerKw(11.0)
            .maxAcPhases(3)
            .maxAcCurrentA(16.0)
            .maxAcChargingPowerKw(11.0)
            .onboardChargerKw(11.0)
            .maxDcPowerKw(100.0)
            .maxDcCurrentA(250.0)
            .maxDcChargingPowerKw(100.0)
            .maxChargingPowerKw(100.0)
            .dcConnectors(List.of("CCS"))
            .connectorTypes(List.of("TYPE2", "CCS"))
            .efficiencyAc(0.90)
            .efficiencyDc(0.92)
            .efficiency(0.90)
            .defaultInitialSoc(20)
            .defaultTargetSoc(80)
            .preconditioning(false)
            .build());
    }

    // ══════════════════════════════════════════════════════════════
    // MÉTHODES D'ACCÈS
    // ══════════════════════════════════════════════════════════════

    /**
     * Récupère un véhicule par son ID.
     * Recherche flexible (insensible à la casse, par marque/modèle).
     *
     * @param id Identifiant du véhicule
     * @return Le profil véhicule ou GENERIC si non trouvé
     */
    public static VehicleProfile getById(String id) {
        if (id == null || id.isBlank()) {
            return VEHICLES.get("GENERIC");
        }

        // Recherche exacte
        VehicleProfile v = VEHICLES.get(id);
        if (v != null) return v;

        // Recherche insensible à la casse
        String upper = id.toUpperCase().replace(" ", "_").replace("-", "_");
        for (Map.Entry<String, VehicleProfile> e : VEHICLES.entrySet()) {
            if (e.getKey().equalsIgnoreCase(id) ||
                e.getKey().contains(upper) ||
                upper.contains(e.getKey())) {
                return e.getValue();
            }
        }

        // Recherche par marque/modèle
        for (VehicleProfile vp : VEHICLES.values()) {
            String search = (vp.getBrand() + "_" + vp.getModel())
                .toUpperCase().replace(" ", "_");
            if (search.contains(upper) || upper.contains(search)) {
                return vp;
            }
        }

        // Par défaut: véhicule générique
        return VEHICLES.get("GENERIC");
    }

    /**
     * Retourne tous les véhicules.
     */
    public static List<VehicleProfile> getAll() {
        return new ArrayList<>(VEHICLES.values());
    }

    /**
     * Retourne tous les IDs de véhicules.
     */
    public static List<String> getAllIds() {
        return new ArrayList<>(VEHICLES.keySet());
    }

    /**
     * Retourne une map ID -> displayName.
     */
    public static Map<String, String> getDisplayNames() {
        Map<String, String> names = new LinkedHashMap<>();
        VEHICLES.forEach((id, v) -> names.put(id, v.getDisplayName()));
        return names;
    }

    /**
     * Retourne les véhicules supportant le CCS.
     */
    public static List<VehicleProfile> getCCSVehicles() {
        return VEHICLES.values().stream()
            .filter(VehicleProfile::supportsCCS)
            .toList();
    }

    /**
     * Retourne les véhicules supportant le CHAdeMO.
     */
    public static List<VehicleProfile> getCHAdeMOVehicles() {
        return VEHICLES.values().stream()
            .filter(VehicleProfile::supportsCHAdeMO)
            .toList();
    }

    /**
     * Retourne les véhicules mono-phase.
     */
    public static List<VehicleProfile> getMonoPhaseVehicles() {
        return VEHICLES.values().stream()
            .filter(VehicleProfile::isMonoPhase)
            .toList();
    }

    /**
     * Retourne les véhicules 800V.
     */
    public static List<VehicleProfile> get800VVehicles() {
        return VEHICLES.values().stream()
            .filter(VehicleProfile::is800V)
            .toList();
    }
}
