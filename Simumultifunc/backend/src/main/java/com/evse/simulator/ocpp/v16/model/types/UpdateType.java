package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Type of update for SendLocalList.req.
 */
public enum UpdateType {
    DIFFERENTIAL("Differential"),
    FULL("Full");

    private final String value;

    UpdateType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UpdateType fromValue(String value) {
        for (UpdateType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown UpdateType: " + value);
    }
}
