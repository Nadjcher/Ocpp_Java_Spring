package com.evse.simulator.dto.response.vehicle;

/**
 * Point sur la courbe de charge d'un véhicule.
 */
public record ChargeCurvePoint(
        int soc,
        int powerKw
) {}
