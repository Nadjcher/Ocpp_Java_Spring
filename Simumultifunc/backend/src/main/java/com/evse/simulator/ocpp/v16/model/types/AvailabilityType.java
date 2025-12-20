package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Availability type for ChangeAvailability.req.
 */
public enum AvailabilityType {
    INOPERATIVE("Inoperative"),
    OPERATIVE("Operative");

    private final String value;

    AvailabilityType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AvailabilityType fromValue(String value) {
        for (AvailabilityType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AvailabilityType: " + value);
    }
}
