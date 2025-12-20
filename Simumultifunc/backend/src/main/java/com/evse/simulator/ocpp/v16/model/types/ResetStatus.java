package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to Reset.req.
 */
public enum ResetStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected");

    private final String value;

    ResetStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ResetStatus fromValue(String value) {
        for (ResetStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ResetStatus: " + value);
    }
}
