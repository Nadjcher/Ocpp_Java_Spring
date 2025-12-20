package com.evse.simulator.mapper;

import com.evse.simulator.dto.response.vehicle.ChargeCurvePoint;
import com.evse.simulator.dto.response.vehicle.VehicleDetail;
import com.evse.simulator.dto.response.vehicle.VehicleSummary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Mapper pour convertir les profils vehicules en DTOs.
 */
@Component
public class VehicleMapper {

    /**
     * Convertit un profil vehicule en resume.
     */
    public VehicleSummary toSummary(Map<String, Object> v) {
        return new VehicleSummary(
                getString(v, "id"),
                getString(v, "displayName"),
                getString(v, "brand"),
                getInt(v, "batteryCapacityKwh"),
                getInt(v, "maxDcPowerKw"),
                getInt(v, "maxAcPowerKw")
        );
    }

    /**
     * Convertit un profil vehicule en details.
     */
    public VehicleDetail toDetail(Map<String, Object> v) {
        return new VehicleDetail(
                getString(v, "id"),
                getString(v, "displayName"),
                getString(v, "brand"),
                getInt(v, "batteryCapacityKwh"),
                getInt(v, "maxDcPowerKw"),
                getInt(v, "maxAcPowerKw"),
                getInt(v, "maxVoltage"),
                getInt(v, "maxCurrentA"),
                mapCurve(v.get("dcChargingCurve"))
        );
    }

    @SuppressWarnings("unchecked")
    private List<ChargeCurvePoint> mapCurve(Object curve) {
        if (curve == null) return List.of();

        if (curve instanceof Map) {
            Map<String, Object> curveMap = (Map<String, Object>) curve;
            return curveMap.entrySet().stream()
                    .map(e -> new ChargeCurvePoint(
                            Integer.parseInt(e.getKey()),
                            ((Number) e.getValue()).intValue()
                    ))
                    .sorted(Comparator.comparing(ChargeCurvePoint::soc))
                    .toList();
        }
        return List.of();
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private int getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
