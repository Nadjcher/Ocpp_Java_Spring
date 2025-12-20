package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to ClearCache.req.
 */
public enum ClearCacheStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected");

    private final String value;

    ClearCacheStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ClearCacheStatus fromValue(String value) {
        for (ClearCacheStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ClearCacheStatus: " + value);
    }
}
