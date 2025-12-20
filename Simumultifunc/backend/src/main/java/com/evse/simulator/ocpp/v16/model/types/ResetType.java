package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Type of reset requested by Reset.req.
 */
public enum ResetType {
    HARD("Hard"),
    SOFT("Soft");

    private final String value;

    ResetType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ResetType fromValue(String value) {
        for (ResetType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ResetType: " + value);
    }
}
