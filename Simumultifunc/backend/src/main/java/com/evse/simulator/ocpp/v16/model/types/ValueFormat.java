package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Format of sampled value in MeterValues.
 */
public enum ValueFormat {
    RAW("Raw"),
    SIGNED_DATA("SignedData");

    private final String value;

    ValueFormat(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ValueFormat fromValue(String value) {
        for (ValueFormat format : values()) {
            if (format.value.equalsIgnoreCase(value)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown ValueFormat: " + value);
    }
}
