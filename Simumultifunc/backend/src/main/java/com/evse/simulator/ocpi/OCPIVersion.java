package com.evse.simulator.ocpi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * OCPI protocol versions.
 */
public enum OCPIVersion {
    V2_1_1("2.1.1"),
    V2_2("2.2"),
    V2_2_1("2.2.1");

    private final String value;

    OCPIVersion(String value) {
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
    public static OCPIVersion fromValue(String value) {
        for (OCPIVersion version : values()) {
            if (version.value.equals(value)) {
                return version;
            }
        }
        throw new IllegalArgumentException("Unknown OCPI version: " + value);
    }
}
