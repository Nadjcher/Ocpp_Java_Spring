package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to SendLocalList.req.
 */
public enum UpdateStatus {
    ACCEPTED("Accepted"),
    FAILED("Failed"),
    NOT_SUPPORTED("NotSupported"),
    VERSION_MISMATCH("VersionMismatch");

    private final String value;

    UpdateStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UpdateStatus fromValue(String value) {
        for (UpdateStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown UpdateStatus: " + value);
    }
}
