package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Authorization status for Authorize and StartTransaction responses.
 */
public enum AuthorizationStatus {
    ACCEPTED("Accepted"),
    BLOCKED("Blocked"),
    EXPIRED("Expired"),
    INVALID("Invalid"),
    CONCURRENT_TX("ConcurrentTx");

    private final String value;

    AuthorizationStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AuthorizationStatus fromValue(String value) {
        for (AuthorizationStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown AuthorizationStatus: " + value);
    }
}
