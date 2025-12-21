package com.evse.simulator.gpm.model;

import com.evse.simulator.gpm.model.enums.GPMChargeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Configuration d'un type de véhicule électrique.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EVTypeConfig {

    private String id;
    private String name;
    private GPMChargeType chargeType;
    private int capacityWh;
    private int maxPowerW;

    // Courbe de charge: SOC% -> Puissance W
    @Builder.Default
    private NavigableMap<Integer, Integer> powerBySoc = new TreeMap<>();

    /**
     * Obtient la puissance de charge pour un SOC donné avec interpolation linéaire.
     */
    public double getPowerAtSoc(double soc) {
        if (powerBySoc.isEmpty()) {
            return maxPowerW;
        }

        int socInt = (int) Math.floor(soc);

        // Bornes
        Integer floorKey = powerBySoc.floorKey(socInt);
        Integer ceilingKey = powerBySoc.ceilingKey(socInt);

        if (floorKey == null) {
            return powerBySoc.firstEntry().getValue();
        }
        if (ceilingKey == null || floorKey.equals(ceilingKey)) {
            return powerBySoc.get(floorKey);
        }

        // Interpolation linéaire
        double floorPower = powerBySoc.get(floorKey);
        double ceilingPower = powerBySoc.get(ceilingKey);
        double ratio = (soc - floorKey) / (ceilingKey - floorKey);

        return floorPower + (ceilingPower - floorPower) * ratio;
    }

    /**
     * Constructeur avec liste de points (pour la désérialisation JSON).
     */
    public void setPowerBySocPoints(List<PowerBySocPoint> points) {
        this.powerBySoc = new TreeMap<>();
        if (points != null) {
            for (PowerBySocPoint point : points) {
                this.powerBySoc.put(point.getSoc(), point.getPowerW());
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PowerBySocPoint {
        private int soc;
        private int powerW;
    }
}
