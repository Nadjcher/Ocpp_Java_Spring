package com.evse.simulator.ocpp;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * OCPP protocol versions.
 */
public enum OCPPVersion {
    OCPP_1_6("1.6"),
    OCPP_2_0("2.0"),
    OCPP_2_0_1("2.0.1");

    private final String value;

    OCPPVersion(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getVersion() {
        return value;
    }

    @JsonCreator
    public static OCPPVersion fromValue(String value) {
        for (OCPPVersion version : values()) {
            if (version.value.equals(value)) {
                return version;
            }
        }
        throw new IllegalArgumentException("Unknown OCPP version: " + value);
    }

    public static OCPPVersion fromString(String value) {
        return fromValue(value);
    }
}
