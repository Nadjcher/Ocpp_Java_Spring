package com.evse.simulator.ocpi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * OCPI roles.
 */
public enum OCPIRole {
    CPO("CPO"),
    EMSP("EMSP"),
    HUB("HUB"),
    NAP("NAP"),
    NSP("NSP"),
    OTHER("OTHER"),
    SCSP("SCSP");

    private final String value;

    OCPIRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OCPIRole fromValue(String value) {
        for (OCPIRole role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown OCPI role: " + value);
    }
}
