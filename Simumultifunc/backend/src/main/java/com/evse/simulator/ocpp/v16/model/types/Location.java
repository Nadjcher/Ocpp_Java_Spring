package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Localisation de la mesure OCPP 1.6.
 */
public enum Location {
    BODY("Body"),
    CABLE("Cable"),
    EV("EV"),
    INLET("Inlet"),
    OUTLET("Outlet");

    private final String value;

    Location(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static Location fromValue(String value) {
        for (Location location : values()) {
            if (location.value.equalsIgnoreCase(value)) {
                return location;
            }
        }
        return OUTLET;
    }
}
