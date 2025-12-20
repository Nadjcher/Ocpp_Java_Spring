package com.evse.simulator.ocpp.v16.model.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status in response to ChangeConfiguration.req.
 */
public enum ConfigurationStatus {
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    REBOOT_REQUIRED("RebootRequired"),
    NOT_SUPPORTED("NotSupported");

    private final String value;

    ConfigurationStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ConfigurationStatus fromValue(String value) {
        for (ConfigurationStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ConfigurationStatus: " + value);
    }
}
