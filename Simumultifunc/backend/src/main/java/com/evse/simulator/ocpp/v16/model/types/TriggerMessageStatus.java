package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to TriggerMessage.req.
 */
public enum TriggerMessageStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    NOT_IMPLEMENTED("NotImplemented");

    private final String value;

    TriggerMessageStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TriggerMessageStatus fromValue(String value) {
        for (TriggerMessageStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TriggerMessageStatus: " + value);
    }
}
