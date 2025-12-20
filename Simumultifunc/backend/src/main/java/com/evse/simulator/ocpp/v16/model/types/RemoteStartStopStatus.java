package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to RemoteStartTransaction.req and RemoteStopTransaction.req.
 */
public enum RemoteStartStopStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected");

    private final String value;

    RemoteStartStopStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RemoteStartStopStatus fromValue(String value) {
        for (RemoteStartStopStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown RemoteStartStopStatus: " + value);
    }
}
