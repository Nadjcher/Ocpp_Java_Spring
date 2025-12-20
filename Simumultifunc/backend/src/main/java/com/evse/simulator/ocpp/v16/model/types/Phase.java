package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Phase for sampled values.
 */
public enum Phase {
    L1("L1"),
    L2("L2"),
    L3("L3"),
    N("N"),
    L1_N("L1-N"),
    L2_N("L2-N"),
    L3_N("L3-N"),
    L1_L2("L1-L2"),
    L2_L3("L2-L3"),
    L3_L1("L3-L1");

    private final String value;

    Phase(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static Phase fromValue(String value) {
        for (Phase phase : values()) {
            if (phase.value.equalsIgnoreCase(value) || phase.name().equalsIgnoreCase(value)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("Unknown Phase: " + value);
    }
}
