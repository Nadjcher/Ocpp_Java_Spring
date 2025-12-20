package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to UnlockConnector.req.
 */
public enum UnlockStatus {
    UNLOCKED("Unlocked"),
    UNLOCK_FAILED("UnlockFailed"),
    NOT_SUPPORTED("NotSupported");

    private final String value;

    UnlockStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UnlockStatus fromValue(String value) {
        for (UnlockStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown UnlockStatus: " + value);
    }
}
